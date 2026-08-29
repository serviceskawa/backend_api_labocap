package com.labo.anapath.appel;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La signalisation des appels audio.
 *
 * <h2>Ce que le serveur fait, et ce qu'il ne fait pas</h2>
 *
 * <p>Il met en relation et il vérifie les droits. Il ne voit jamais la voix :
 * elle passe d'un téléphone à l'autre, ou par le relais TURN, et jamais par
 * ici. Un serveur qui porterait la voix devrait la déchiffrer pour la
 * retransmettre — ce qui ferait de lui l'endroit où toutes les conversations
 * médicales du laboratoire deviennent lisibles d'un coup.</p>
 *
 * <h2>Le contrôle qui compte</h2>
 *
 * <p>Chaque message est vérifié contre les participants du fil. Sans cela,
 * n'importe quelle session ouverte pourrait injecter une offre dans l'appel de
 * deux autres personnes, ou se faire sonner par un dossier qui ne la regarde
 * pas. La liaison prouve qui vous êtes ; elle ne dit rien de ce à quoi vous avez
 * droit.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GestionnaireAppels extends TextWebSocketHandler {

    private final RegistreDesAppels registre;
    private final ServiceAppels service;
    private final ObjectMapper json;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID qui = utilisateurDe(session);
        if (qui == null) return;
        registre.ouvrir(qui, session);
        log.debug("Liaison d'appel ouverte : {}", qui);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        UUID qui = utilisateurDe(session);
        UUID branche = brancheDe(session);
        if (qui == null) return;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> recu = json.readValue(message.getPayload(), Map.class);
            String type = String.valueOf(recu.get("type"));

            switch (type) {
                case "appeler" -> service.appeler(qui, branche,
                        uuid(recu.get("dossier")), identifiants(recu.get("cibles")));
                case "accepter" -> service.accepter(qui, uuid(recu.get("appel")));
                case "refuser" -> service.refuser(qui, uuid(recu.get("appel")));
                case "raccrocher" -> service.raccrocher(qui, uuid(recu.get("appel")));
                case "signal" -> service.relayer(qui, uuid(recu.get("appel")),
                        uuid(recu.get("vers")), recu.get("charge"));
                default -> log.debug("Message d'appel inconnu : {}", type);
            }
        } catch (Exception e) {
            // Un message mal formé ne doit pas couper la liaison : l'appareil
            // se retrouverait muet sans savoir pourquoi, en plein appel.
            log.debug("Message d'appel illisible : {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus statut) {
        UUID qui = utilisateurDe(session);
        if (qui == null) return;
        registre.fermer(qui, session);
        // Un téléphone qui perd le réseau doit sortir des appels où il était,
        // sinon les autres restent devant une vignette qui ne parle plus.
        if (!registre.estEnLigne(qui)) service.quitterTout(qui);
        log.debug("Liaison d'appel fermée : {}", qui);
    }

    private static UUID utilisateurDe(WebSocketSession session) {
        Object v = session.getAttributes().get(PoigneeDeMain.UTILISATEUR);
        return v instanceof UUID id ? id : null;
    }

    private static UUID brancheDe(WebSocketSession session) {
        Object v = session.getAttributes().get(PoigneeDeMain.BRANCHE);
        return v instanceof UUID id ? id : null;
    }

    private static UUID uuid(Object v) {
        try {
            return v == null ? null : UUID.fromString(String.valueOf(v));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<UUID> identifiants(Object v) {
        List<UUID> liste = new ArrayList<>();
        if (v instanceof List<?> brut) {
            for (Object o : brut) {
                UUID id = uuid(o);
                if (id != null) liste.add(id);
            }
        }
        return liste;
    }

    /** Raccourci de lecture pour les messages sortants. */
    static Map<String, Object> message(String type) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        return m;
    }
}
