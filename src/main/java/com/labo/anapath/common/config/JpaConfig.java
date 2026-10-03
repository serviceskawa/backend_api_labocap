package com.labo.anapath.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration JPA et planification des tâches.
 * <p>
 * Active les repositories JPA sur l'ensemble du package {@code com.labo.anapath}
 * et active la planification Spring ({@code @Scheduled}) pour les tâches périodiques
 * telles que le nettoyage de la blacklist de tokens.
 * </p>
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.labo.anapath")
@EnableScheduling
public class JpaConfig {

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return new RestTemplate(factory);
    }
}
