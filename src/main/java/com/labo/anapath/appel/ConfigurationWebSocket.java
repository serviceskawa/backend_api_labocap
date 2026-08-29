package com.labo.anapath.appel;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Ouvre la liaison de signalisation.
 *
 * <h2>WebSocket brute plutôt que STOMP</h2>
 *
 * <p>STOMP apporte des files, des abonnements et des accusés — rien dont un
 * échange d'offres et de candidats ait besoin. Il ajouterait une couche à
 * déboguer des deux côtés pour un protocole qui tient en huit messages.</p>
 *
 * <h2>Les origines</h2>
 *
 * <p>L'application mobile n'envoie pas d'origine : c'est un client natif, pas un
 * navigateur. Le contrôle d'origine ne protégerait donc rien ici — c'est le
 * jeton de la poignée de main qui fait le travail.</p>
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ConfigurationWebSocket implements WebSocketConfigurer {

    private final GestionnaireAppels gestionnaire;
    private final PoigneeDeMain poignee;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registre) {
        registre.addHandler(gestionnaire, "/ws/appels")
                .addInterceptors(poignee)
                .setAllowedOriginPatterns("*");
    }
}
