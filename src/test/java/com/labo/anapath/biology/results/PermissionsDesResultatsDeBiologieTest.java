package com.labo.anapath.biology.results;

import com.labo.anapath.common.exception.GlobalExceptionHandler;
import com.labo.anapath.common.security.UserPrincipal;
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
import org.springframework.test.web.servlet.RequestBuilder;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Qui peut lire, saisir et valider techniquement les résultats de biologie.
 *
 * <p>Comme pour le catalogue : la table des gardes déclarées, puis leur application
 * réelle par l'intercepteur {@code @PreAuthorize} de Spring Security.</p>
 */
class PermissionsDesResultatsDeBiologieTest {

    private static final UUID BRANCH = UUID.randomUUID();
    private static final UUID USER = UUID.randomUUID();
    private static final String BON = "/api/v1/biology-results/orders/" + UUID.randomUUID();
    private static final String ANALYSE = BON + "/analyses/" + UUID.randomUUID();

    private final BiologyResultService service = mock(BiologyResultService.class);

    private static String a(String slug) {
        return "hasAuthority('" + slug + "')";
    }

    @Test
    @DisplayName("gardes déclarées : lecture, saisie, validation technique")
    void gardesDeclarees() {
        Map<String, String> gardes = Arrays.stream(BiologyResultController.class.getDeclaredMethods())
                .filter(m -> Arrays.stream(m.getAnnotations())
                        .anyMatch(x -> x.annotationType().isAnnotationPresent(RequestMapping.class)))
                .collect(Collectors.toMap(Method::getName,
                        m -> m.isAnnotationPresent(PreAuthorize.class)
                                ? m.getAnnotation(PreAuthorize.class).value() : "(aucune)",
                        (x, y) -> x, TreeMap::new));
        assertThat(gardes).isEqualTo(Map.of(
                "worklist", a("view-biology-results"),
                "worksheet", a("view-biology-results"),
                "savePanel", a("edit-biology-results"),
                "saveCulture", a("edit-biology-results"),
                "validateTechnically", a("validate-biology-results"),
                "cancelTechnicalValidation", a("validate-biology-results")));
    }

    private MockMvc api() {
        ProxyFactory usine = new ProxyFactory(new BiologyResultController(service));
        usine.setProxyTargetClass(true);
        usine.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        return MockMvcBuilders.standaloneSetup(usine.getProxy())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private static void connecter(String... permissions) {
        List<GrantedAuthority> droits = Arrays.stream(permissions)
                .map(SimpleGrantedAuthority::new).map(GrantedAuthority.class::cast).toList();
        UserPrincipal principal = new UserPrincipal(USER, "agent@labo.bj", "x", BRANCH, true, droits);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, droits));
    }

    @AfterEach
    void deconnecter() {
        SecurityContextHolder.clearContext();
    }

    private static final String FICHE = "{\"values\":[],\"comment\":null}";
    private static final String CULTURE = "{\"options\":[],\"isolates\":[]}";

    private static RequestBuilder lireListe() { return get("/api/v1/biology-results/worklist"); }
    private static RequestBuilder lireFeuille() { return get(BON); }
    private static RequestBuilder saisirFiche() {
        return put(ANALYSE).contentType(MediaType.APPLICATION_JSON).content(FICHE);
    }
    private static RequestBuilder saisirCulture() {
        return put(ANALYSE + "/culture").contentType(MediaType.APPLICATION_JSON).content(CULTURE);
    }
    private static RequestBuilder valider() { return post(ANALYSE + "/technical-validation"); }
    private static RequestBuilder devalider() { return delete(ANALYSE + "/technical-validation"); }

    @Test
    @DisplayName("view-biology-results seul : lit, ne saisit ni ne valide")
    void lecture() throws Exception {
        connecter("view-biology-results");
        MockMvc mvc = api();
        mvc.perform(lireListe()).andExpect(status().isOk());
        mvc.perform(lireFeuille()).andExpect(status().isOk());
        mvc.perform(saisirFiche()).andExpect(status().isForbidden());
        mvc.perform(saisirCulture()).andExpect(status().isForbidden());
        mvc.perform(valider()).andExpect(status().isForbidden());
        mvc.perform(devalider()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("edit-biology-results seul : saisit (auteur transmis), ne valide pas, ne lit pas la liste")
    void saisie() throws Exception {
        connecter("edit-biology-results");
        MockMvc mvc = api();
        mvc.perform(saisirFiche()).andExpect(status().isOk());
        mvc.perform(saisirCulture()).andExpect(status().isOk());
        verify(service).savePanel(any(), any(), any(), eq(USER), eq(BRANCH));
        mvc.perform(valider()).andExpect(status().isForbidden());
        mvc.perform(lireListe()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("validate-biology-results seul : valide et dévalide, ne saisit pas")
    void validation() throws Exception {
        connecter("validate-biology-results");
        MockMvc mvc = api();
        mvc.perform(valider()).andExpect(status().isOk());
        mvc.perform(devalider()).andExpect(status().isOk());
        mvc.perform(saisirFiche()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("droits d'anatomie pathologique ou validation biologique seule : rien")
    void autresDroits() throws Exception {
        connecter("view-reports", "edit-reports", "validate-reports", "validate-biology-reports");
        MockMvc mvc = api();
        mvc.perform(lireListe()).andExpect(status().isForbidden());
        mvc.perform(saisirFiche()).andExpect(status().isForbidden());
        mvc.perform(valider()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("interprétation d'antibiogramme manquante : 400 avant le service")
    void validationDeLaRequete() throws Exception {
        connecter("edit-biology-results");
        api().perform(put(ANALYSE + "/culture").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isolates\":[{\"organism\":\"E. coli\",\"antibiogram\":[{\"antibioticId\":\""
                                + UUID.randomUUID() + "\"}]}]}"))
                .andExpect(status().isBadRequest());
    }
}
