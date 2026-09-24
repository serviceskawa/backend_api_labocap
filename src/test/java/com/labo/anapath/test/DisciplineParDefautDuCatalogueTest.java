package com.labo.anapath.test;

import com.labo.anapath.common.Discipline;
import com.labo.anapath.common.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les listes du catalogue ({@code /lab-tests}, {@code /category-tests}) répondent
 * comme avant quand on ne leur précise pas de discipline : l'écran « Examens » et le
 * formulaire de demande d'anatomie pathologique ne voient jamais la biologie.
 */
class DisciplineParDefautDuCatalogueTest {

    private static final UUID BRANCH_ID = UUID.randomUUID();

    private static List<String> listesAvecDiscipline(Class<?> controleur) {
        return Arrays.stream(controleur.getDeclaredMethods())
                .filter(m -> Arrays.stream(m.getParameters()).anyMatch(p -> p.getType() == Discipline.class))
                .peek(m -> {
                    for (Parameter p : m.getParameters()) {
                        if (p.getType() != Discipline.class) continue;
                        RequestParam rp = p.getAnnotation(RequestParam.class);
                        assertThat(rp).as("%s : discipline doit être un @RequestParam", m.getName()).isNotNull();
                        assertThat(rp.defaultValue()).as("%s : défaut", m.getName()).isEqualTo("PATHOLOGY");
                    }
                })
                .map(Method::getName)
                .sorted()
                .toList();
    }

    @Test
    @DisplayName("toutes les listes du catalogue ont PATHOLOGY pour défaut")
    void defautsDeclares() {
        assertThat(listesAvecDiscipline(LabTestController.class)).containsExactly("findAll", "findAll", "search");
        assertThat(listesAvecDiscipline(CategoryTestController.class)).containsExactly("findAll");
    }

    private final LabTestService labTestService = mock(LabTestService.class);
    private final CategoryTestService categoryTestService = mock(CategoryTestService.class);
    private MockMvc mvc;

    @BeforeEach
    void monter() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new LabTestController(labTestService), new CategoryTestController(categoryTestService))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "agent@labo.bj", "x", BRANCH_ID, true, List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void demonter() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("GET /lab-tests, /all, /search sans discipline → PATHOLOGY ; avec → la valeur")
    void analyses() throws Exception {
        mvc.perform(get("/api/v1/lab-tests")).andExpect(status().isOk());
        verify(labTestService).findAll(anyInt(), anyInt(), any(), any(), eq(Discipline.PATHOLOGY), eq(BRANCH_ID));
        mvc.perform(get("/api/v1/lab-tests").param("discipline", "BIOLOGY")).andExpect(status().isOk());
        verify(labTestService).findAll(anyInt(), anyInt(), any(), any(), eq(Discipline.BIOLOGY), eq(BRANCH_ID));

        mvc.perform(get("/api/v1/lab-tests/all")).andExpect(status().isOk());
        verify(labTestService).findAll(BRANCH_ID, Discipline.PATHOLOGY);

        mvc.perform(get("/api/v1/lab-tests/search").param("q", "nfs")).andExpect(status().isOk());
        verify(labTestService).search("nfs", BRANCH_ID, Discipline.PATHOLOGY);
    }

    @Test
    @DisplayName("GET /category-tests sans discipline → PATHOLOGY ; avec → la valeur")
    void categories() throws Exception {
        mvc.perform(get("/api/v1/category-tests")).andExpect(status().isOk());
        verify(categoryTestService).findAll(anyInt(), anyInt(), eq(Discipline.PATHOLOGY), eq(BRANCH_ID));
        mvc.perform(get("/api/v1/category-tests").param("discipline", "BIOLOGY")).andExpect(status().isOk());
        verify(categoryTestService).findAll(anyInt(), anyInt(), eq(Discipline.BIOLOGY), eq(BRANCH_ID));
    }
}
