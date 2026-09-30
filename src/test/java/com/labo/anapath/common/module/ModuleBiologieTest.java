package com.labo.anapath.common.module;

import com.labo.anapath.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un laboratoire sans biologie ne voit pas ses routes ; les autres routes ne
 * sont jamais concernées par l'interrupteur.
 */
class ModuleBiologieTest {

    @RestController
    static class RoutesDEssai {
        @GetMapping("/api/v1/biology-results/worklist")
        String resultats() { return "ok"; }

        @GetMapping("/api/v1/antibiotics")
        String antibiotiques() { return "ok"; }

        @GetMapping("/api/v1/culture-options/{id}")
        String option() { return "ok"; }

        @GetMapping("/api/v1/test-orders")
        String bons() { return "ok"; }
    }

    private static MockMvc api(boolean biologie) {
        ModulesProperties modules = new ModulesProperties();
        modules.setBiology(biologie);
        return MockMvcBuilders.standaloneSetup(new RoutesDEssai())
                .addMappedInterceptors(ModulesWebConfig.BIOLOGY_PATHS, new BiologyModuleInterceptor(modules))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("module désactivé : les routes de biologie répondent 404")
    void desactive() throws Exception {
        MockMvc mvc = api(false);
        mvc.perform(get("/api/v1/biology-results/worklist")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/antibiotics")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/culture-options/42")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("module activé : les routes de biologie répondent")
    void active() throws Exception {
        MockMvc mvc = api(true);
        mvc.perform(get("/api/v1/biology-results/worklist")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/antibiotics")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/culture-options/42")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("les routes partagées avec l'anapath ne dépendent pas du module")
    void routesPartagees() throws Exception {
        api(false).perform(get("/api/v1/test-orders")).andExpect(status().isOk());
    }
}
