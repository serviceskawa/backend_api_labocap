package com.labo.anapath.mobile;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Les notifications hors-app.
 *
 * <h2>Ce qu'elles portent, et ce qu'elles taisent</h2>
 *
 * <p>Une notification s'affiche sur un écran verrouillé, parfois sous les yeux
 * d'un tiers — dans un couloir, sur une table. Elle nomme donc l'auteur et le
 * dossier, jamais le contenu du message. Qui veut lire déverrouille : c'est un
 * geste de plus, et c'est le prix de ne pas exposer une conversation médicale
 * à qui passe.</p>
 *
 * <h2>Sans clé</h2>
 *
 * <p>Le service reste présent mais muet. Une installation qui n'a pas de projet
 * Firebase — ou qui n'en veut pas — continue de fonctionner : les badges dans
 * l'application suffisent, seule l'alerte hors-app manque. Refuser de démarrer
 * pour cela serait disproportionné.</p>
 *
 * <h2>Ce qu'il ne fait jamais</h2>
 *
 * <p>Échouer bruyamment. Un jeton périmé, un réseau coupé, un quota atteint :
 * rien de tout cela ne doit empêcher un message d'être posté. La conversation
 * est la donnée ; la notification n'en est que l'écho.</p>
 */
@Slf4j
@Component
public class NotificationsPush {

    private final FirebaseMessaging messagerie;

    /**
     * @param json    le contenu du compte de service, pour les déploiements qui
     *                passent les secrets par l'environnement
     * @param chemin  ou son chemin sur le disque, pour ceux qui montent un
     *                fichier
     */
    public NotificationsPush(
            @Value("${app.push.credentials:}") String json,
            @Value("${app.push.credentials-file:}") String chemin) {
        FirebaseMessaging m = null;
        try {
            byte[] octets = null;
            if (json != null && !json.isBlank()) {
                octets = json.getBytes(StandardCharsets.UTF_8);
            } else if (chemin != null && !chemin.isBlank()) {
                Path p = Path.of(chemin.trim());
                if (Files.isReadable(p)) octets = Files.readAllBytes(p);
                else log.warn("Notifications : « {} » est illisible.", chemin);
            }

            if (octets != null) {
                // Une application nommée : Firebase refuse d'en initialiser
                // deux sous le même nom, et le rechargement à chaud d'un
                // contexte de test en créerait une seconde.
                FirebaseApp app = FirebaseApp.getApps().stream()
                        .filter(a -> "labo-anapath".equals(a.getName()))
                        .findFirst()
                        .orElse(null);
                if (app == null) {
                    app = FirebaseApp.initializeApp(
                            FirebaseOptions.builder()
                                    .setCredentials(GoogleCredentials.fromStream(
                                            new ByteArrayInputStream(octets)))
                                    .build(),
                            "labo-anapath");
                }
                m = FirebaseMessaging.getInstance(app);
                log.info("Notifications hors-app actives.");
            }
        } catch (Exception e) {
            // Une clé mal formée n'empêche pas de servir : elle éteint les
            // notifications, et le dit une fois au démarrage.
            log.warn("Notifications hors-app inactives : {}", e.getMessage());
        }
        this.messagerie = m;
        if (m == null) {
            log.info("Notifications hors-app inactives : aucune clé configurée.");
        }
    }

    public boolean estActif() {
        return messagerie != null;
    }

    /**
     * Prévient les appareils donnés.
     *
     * <p>Un envoi par appareil plutôt qu'un envoi groupé : c'est ce qui permet
     * de savoir lequel a échoué, et l'échec d'un jeton périmé ne doit pas
     * emporter les autres.</p>
     *
     * @param jetons  les appareils à joindre
     * @param titre   ce qui s'affiche en gras — « Anapath — Dossier 26-0155 »
     * @param corps   une ligne, sans le contenu du message
     * @param donnees ce que l'application relit au tap : le dossier à ouvrir
     */
    public void prevenir(List<String> jetons, String titre, String corps,
                         Map<String, String> donnees) {
        if (messagerie == null || jetons.isEmpty()) return;

        for (String jeton : jetons) {
            try {
                messagerie.send(Message.builder()
                        .setToken(jeton)
                        .setNotification(Notification.builder()
                                .setTitle(titre)
                                .setBody(corps)
                                .build())
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .setNotification(AndroidNotification.builder()
                                        // Un fil par dossier : les messages d'un
                                        // même cas se remplacent au lieu
                                        // d'empiler dix lignes identiques.
                                        .setTag(donnees.getOrDefault("testOrderId", "anapath"))
                                        .build())
                                .build())
                        .putAllData(donnees)
                        .build());
            } catch (FirebaseMessagingException e) {
                // Le jeton peut être périmé — application désinstallée, données
                // effacées. On le note sans le supprimer : c'est l'appareil qui
                // renverra le sien à la prochaine connexion, et effacer ici
                // ferait perdre la trace d'un appareil encore enrôlé.
                log.debug("Notification non délivrée : {}", e.getMessage());
            } catch (Exception e) {
                log.warn("Notification impossible", e);
            }
        }
    }
}
