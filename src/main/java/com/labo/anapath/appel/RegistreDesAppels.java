package com.labo.anapath.appel;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Qui est connecté, et quels appels sont en cours.
 *
 * <h2>En mémoire d'un seul serveur</h2>
 *
 * <p>L'installation tourne sur un conteneur unique : une carte suffit. Le jour
 * où deux instances serviraient en parallèle, deux participants pourraient
 * atterrir sur des serveurs différents et ne jamais se voir — il faudrait alors
 * un relais partagé (Redis). C'est écrit ici pour que la limite se découvre en
 * lisant, et non le jour du second conteneur.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RegistreDesAppels {

    private final ObjectMapper json;

    /** Les liaisons ouvertes, par personne : un même agent peut avoir deux appareils. */
    private final Map<UUID, Set<WebSocketSession>> liaisons = new ConcurrentHashMap<>();

    /** Les appels en cours, par identifiant. */
    private final Map<UUID, Appel> appels = new ConcurrentHashMap<>();

    public void ouvrir(UUID utilisateur, WebSocketSession session) {
        liaisons.computeIfAbsent(utilisateur, k -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void fermer(UUID utilisateur, WebSocketSession session) {
        Set<WebSocketSession> siennes = liaisons.get(utilisateur);
        if (siennes == null) return;
        siennes.remove(session);
        if (siennes.isEmpty()) liaisons.remove(utilisateur);
    }

    public boolean estEnLigne(UUID utilisateur) {
        return liaisons.containsKey(utilisateur);
    }

    public void deposer(Appel appel) {
        appels.put(appel.getId(), appel);
    }

    public Optional<Appel> appel(UUID id) {
        return Optional.ofNullable(appels.get(id));
    }

    public void retirer(UUID id) {
        appels.remove(id);
    }

    public Collection<Appel> tous() {
        return appels.values();
    }

    /**
     * Envoie un message à toutes les liaisons d'une personne.
     *
     * <p>À toutes et non à une : l'agent peut avoir laissé une session ouverte
     * sur un second appareil, et rien ne dit lequel il tient en main.</p>
     *
     * <p>N'échoue jamais. Une liaison morte se découvre à l'écriture — la
     * détecter autrement demanderait un battement de cœur pour un gain nul,
     * puisque la seule réaction utile est de cesser de lui écrire.</p>
     */
    public void envoyer(UUID destinataire, Map<String, Object> message) {
        Set<WebSocketSession> siennes = liaisons.get(destinataire);
        if (siennes == null) return;
        String charge;
        try {
            charge = json.writeValueAsString(message);
        } catch (Exception e) {
            log.warn("Message d'appel insérialisable", e);
            return;
        }
        for (WebSocketSession session : siennes) {
            try {
                // `sendMessage` n'est pas sûr à plusieurs fils : deux envois
                // simultanés entrelaceraient leurs trames et casseraient la
                // liaison. Le verrou porte sur la session, pas sur le registre.
                synchronized (session) {
                    if (session.isOpen()) session.sendMessage(new TextMessage(charge));
                }
            } catch (IOException e) {
                log.debug("Liaison perdue en écrivant : {}", e.getMessage());
            }
        }
    }
}
