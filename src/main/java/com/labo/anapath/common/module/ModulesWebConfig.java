package com.labo.anapath.common.module;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Rattache chaque module optionnel à ses routes.
 *
 * <p>Les routes partagées avec l'anapath ({@code /test-orders}, {@code /reports},
 * {@code /lab-tests}…) ne figurent pas ici : c'est le service qui refuse une donnée
 * {@code BIOLOGY} quand le module est désactivé.
 */
@Configuration
@RequiredArgsConstructor
public class ModulesWebConfig implements WebMvcConfigurer {

    static final String[] BIOLOGY_PATHS = {
            "/api/v1/biology-*",
            "/api/v1/biology-*/**",
            "/api/v1/antibiotics",
            "/api/v1/antibiotics/**",
            "/api/v1/culture-options",
            "/api/v1/culture-options/**",
    };

    private final BiologyModuleInterceptor biologyModuleInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(biologyModuleInterceptor).addPathPatterns(BIOLOGY_PATHS);
    }
}
