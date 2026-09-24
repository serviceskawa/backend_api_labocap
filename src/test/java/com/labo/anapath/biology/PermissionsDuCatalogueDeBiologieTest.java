package com.labo.anapath.biology;

import com.labo.anapath.common.exception.GlobalExceptionHandler;
import com.labo.anapath.common.security.UserPrincipal;
import com.labo.anapath.test.CategoryTestController;
import com.labo.anapath.test.CategoryTestService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Qui peut lire et écrire le catalogue de biologie.
 *
 * <p>Deux niveaux : la table des gardes déclarées (toute route ajoutée sans garde,
 * ou avec la mauvaise, fait échouer le test), puis leur application réelle — les
 * contrôleurs sont enveloppés dans l'intercepteur {@code @PreAuthorize} de Spring
 * Security, comme dans l'application.</p>
 */
class PermissionsDuCatalogueDeBiologieTest {

    private static final UUID BRANCH = UUID.randomUUID();

    private static Map<String, String> gardes(Class<?> controleur) {
        return Arrays.stream(controleur.getDeclaredMethods())
                .filter(m -> Arrays.stream(m.getAnnotations())
                        .anyMatch(a -> a.annotationType().isAnnotationPresent(RequestMapping.class)))
                .collect(Collectors.toMap(Method::getName,
                        m -> m.isAnnotationPresent(PreAuthorize.class)
                                ? m.getAnnotation(PreAuthorize.class).value() : "(aucune)",
                        (a, b) -> a, TreeMap::new));
    }

    private static String a(String slug) {
        return "hasAuthority('" + slug + "')";
    }

    @Test
    @DisplayName("gardes déclarées : lecture view-tests, écriture par permission dédiée")
    void gardesDeclarees() {
        assertThat(gardes(BiologyParameterController.class)).isEqualTo(Map.of(
                "getSheet", a("view-tests"),
                "saveSheet", a("manage-biology-parameters")));
        assertThat(gardes(AntibioticController.class)).isEqualTo(Map.of(
                "findAll", a("view-tests"),
                "create", a("manage-antibiotics"),
                "update", a("manage-antibiotics"),
                "delete", a("manage-antibiotics")));
        assertThat(gardes(CultureOptionController.class)).isEqualTo(Map.of(
                "findAll", a("view-tests"),
                "findByLabTest", a("view-tests"),
                "create", a("manage-culture-options"),
                "update", a("manage-culture-options"),
                "delete", a("manage-culture-options"),
                "setForLabTest", a("manage-culture-options")));
        // Les catégories par défaut suivent la création de catégorie.
        assertThat(gardes(CategoryTestController.class))
                .containsEntry("createBiologyDefaults", a("edit-tests"))
                .containsEntry("create", a("edit-tests"));
    }

    // -------------------------------------------------------------------------
    // Application réelle des gardes.
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static <T> T securise(T controleur) {
        ProxyFactory usine = new ProxyFactory(controleur);
        usine.setProxyTargetClass(true);
        usine.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        return (T) usine.getProxy();
    }

    private static MockMvc api() {
        return MockMvcBuilders.standaloneSetup(
                        securise(new BiologyParameterController(mock(BiologySheetService.class))),
                        securise(new AntibioticController(mock(AntibioticService.class))),
                        securise(new CultureOptionController(mock(CultureOptionService.class))),
                        securise(new CategoryTestController(mock(CategoryTestService.class))))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static void connecter(String... permissions) {
        List<GrantedAuthority> droits = Arrays.stream(permissions)
                .map(SimpleGrantedAuthority::new).map(GrantedAuthority.class::cast).toList();
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "agent@labo.bj", "x", BRANCH, true, droits);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, droits));
    }

    @AfterEach
    void deconnecter() {
        SecurityContextHolder.clearContext();
    }

    private static final String ANTIBIOTIQUE = "{\"name\":\"Amoxicilline\"}";
    private static final String OPTION = "{\"name\":\"Aspect\",\"choices\":[\"Clair\"]}";
    private static final String FICHE = "{\"sections\":[],\"parameters\":[]}";
    private static final String OPTIONS_DE_L_ANALYSE = "{\"cultureOptionIds\":[]}";

    @Test
    @DisplayName("view-tests seul : lit tout, n'écrit rien")
    void lectureSeule() throws Exception {
        connecter("view-tests");
        MockMvc mvc = api();
        UUID id = UUID.randomUUID();

        mvc.perform(get("/api/v1/biology-parameters/sheet/" + id)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/antibiotics")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/culture-options")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/culture-options/by-lab-test/" + id)).andExpect(status().isOk());

        mvc.perform(put("/api/v1/biology-parameters/sheet/" + id)
                .contentType(MediaType.APPLICATION_JSON).content(FICHE)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/antibiotics")
                .contentType(MediaType.APPLICATION_JSON).content(ANTIBIOTIQUE)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/antibiotics/" + id)
                .contentType(MediaType.APPLICATION_JSON).content(ANTIBIOTIQUE)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/antibiotics/" + id)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/culture-options")
                .contentType(MediaType.APPLICATION_JSON).content(OPTION)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/culture-options/" + id)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/culture-options/by-lab-test/" + id)
                .contentType(MediaType.APPLICATION_JSON).content(OPTIONS_DE_L_ANALYSE))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/category-tests/biology-defaults")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sans view-tests : même la lecture est refusée")
    void sansDroit() throws Exception {
        connecter("view-reports");
        MockMvc mvc = api();

        mvc.perform(get("/api/v1/antibiotics")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/biology-parameters/sheet/" + UUID.randomUUID())).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("chaque permission d'écriture n'ouvre que son référentiel")
    void cloisonnement() throws Exception {
        UUID id = UUID.randomUUID();

        connecter("manage-antibiotics");
        MockMvc mvc = api();
        mvc.perform(post("/api/v1/antibiotics")
                .contentType(MediaType.APPLICATION_JSON).content(ANTIBIOTIQUE)).andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/antibiotics/" + id)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/culture-options")
                .contentType(MediaType.APPLICATION_JSON).content(OPTION)).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/biology-parameters/sheet/" + id)
                .contentType(MediaType.APPLICATION_JSON).content(FICHE)).andExpect(status().isForbidden());

        connecter("manage-culture-options");
        mvc.perform(post("/api/v1/culture-options")
                .contentType(MediaType.APPLICATION_JSON).content(OPTION)).andExpect(status().isCreated());
        mvc.perform(put("/api/v1/culture-options/by-lab-test/" + id)
                .contentType(MediaType.APPLICATION_JSON).content(OPTIONS_DE_L_ANALYSE)).andExpect(status().isOk());
        mvc.perform(post("/api/v1/antibiotics")
                .contentType(MediaType.APPLICATION_JSON).content(ANTIBIOTIQUE)).andExpect(status().isForbidden());

        connecter("manage-biology-parameters");
        mvc.perform(put("/api/v1/biology-parameters/sheet/" + id)
                .contentType(MediaType.APPLICATION_JSON).content(FICHE)).andExpect(status().isOk());
        mvc.perform(put("/api/v1/culture-options/" + id)
                .contentType(MediaType.APPLICATION_JSON).content(OPTION)).andExpect(status().isForbidden());

        connecter("edit-tests");
        mvc.perform(post("/api/v1/category-tests/biology-defaults")).andExpect(status().isOk());
    }
}
