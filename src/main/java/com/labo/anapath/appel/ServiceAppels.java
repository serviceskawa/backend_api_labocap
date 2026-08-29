package com.labo.anapath.appel;

import com.labo.anapath.common.NomComplet;
import com.labo.anapath.discussion.Discussion;
import com.labo.anapath.discussion.DiscussionParticipant;
import com.labo.anapath.discussion.DiscussionRepository;
import com.labo.anapath.mobile.MobileDeviceRepository;
import com.labo.anapath.mobile.NotificationsPush;
import com.labo.anapath.testorder.TestOrder;
import com.labo.anapath.testorder.TestOrderRepository;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.labo.anapath.appel.GestionnaireAppels.message;

/**
 * Les règles de l'appel : qui peut sonner qui, et qui peut entrer.
 *
 * <h2>Le fil décide, pas l'appelant</h2>
 *
 * <p>On ne peut appeler que des participants du fil — les mêmes qui reçoivent
 * les messages et comptent les non-lus. Un annuaire d'appel séparé finirait par
 * en diverger, et permettrait de sonner quelqu'un à propos d'un dossier qu'il
 * n'a pas le droit de voir.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceAppels {

    private final RegistreDesAppels registre;
    private final DiscussionRepository discussions;
    private final TestOrderRepository demandes;
    private final UserRepository utilisateurs;
    private final JournalAppelRepository journal;
    private final NotificationsPush notifications;
    private final MobileDeviceRepository appareils;

    /**
     * Sonne les personnes visées, ou tout le fil si aucune n'est nommée.
     *
     * <p>Un appel n'existe qu'une fois par dossier : si quelqu'un appelle alors
     * qu'un appel est déjà en cours sur ce fil, il le rejoint au lieu d'en
     * ouvrir un second. Deux appels parallèles sur le même dossier
     * partageraient la salle en deux moitiés qui ne s'entendent pas, sans que
     * personne ne comprenne pourquoi.</p>
     */
    @Transactional
    public void appeler(UUID qui, UUID branche, UUID dossier, List<UUID> cibles) {
        if (dossier == null) return;

        Discussion fil = discussions.findByTestOrderId(dossier).orElse(null);
        if (fil == null || !estParticipant(fil, qui)) {
            log.debug("Appel refusé : {} n'est pas du fil {}", qui, dossier);
            return;
        }

        // Un appel déjà ouvert sur ce dossier ? On le rejoint.
        Optional<Appel> encours = registre.tous().stream()
                .filter(a -> a.getDossier().equals(dossier))
                .findFirst();
        if (encours.isPresent()) {
            accepter(qui, encours.get().getId());
            return;
        }

        Appel appel = new Appel(dossier, branche, qui);
        List<UUID> destinataires = cibles.isEmpty()
                ? participants(fil, qui)
                : cibles.stream().filter(c -> !c.equals(qui) && estParticipant(fil, c)).toList();
        if (destinataires.isEmpty()) {
            log.debug("Appel sans destinataire sur le fil {}", dossier);
            return;
        }

        appel.getConviés().addAll(destinataires);
        appel.getPrésents().put(qui, LocalDateTime.now());
        registre.deposer(appel);

        TestOrder demande = demandes.findById(dossier).orElse(null);
        String code = demande == null || demande.getCode() == null ? "" : demande.getCode();
        String nom = nomDe(qui);

        Map<String, Object> sonnerie = message("sonnerie");
        sonnerie.put("appel", appel.getId().toString());
        sonnerie.put("dossier", dossier.toString());
        sonnerie.put("codeDemande", code);
        sonnerie.put("de", qui.toString());
        sonnerie.put("nomAppelant", nom);
        sonnerie.put("groupe", destinataires.size() > 1);

        for (UUID cible : destinataires) {
            registre.envoyer(cible, sonnerie);
            // Hors ligne : le téléphone est en poche, application fermée. La
            // notification est le seul moyen de le faire sonner — sans elle,
            // l'appel n'atteint que ceux qui regardaient déjà leur écran.
            if (!registre.estEnLigne(cible)) sonnerHorsApp(cible, appel, code, nom);
        }

        Map<String, Object> ouvert = message("ouvert");
        ouvert.put("appel", appel.getId().toString());
        ouvert.put("dossier", dossier.toString());
        registre.envoyer(qui, ouvert);

        log.info("Appel ouvert : dossier={} par={} conviés={}", dossier, qui, destinataires.size());
    }

    /** Entre dans l'appel, et se présente à ceux qui y sont déjà. */
    public void accepter(UUID qui, UUID appelId) {
        Appel appel = registre.appel(appelId).orElse(null);
        if (appel == null || !appel.peutEntrer(qui) || appel.estPresent(qui)) return;

        List<UUID> deja = new ArrayList<>(appel.getPrésents().keySet());
        appel.getPrésents().put(qui, LocalDateTime.now());
        appel.getRefus().remove(qui);

        // Chacun apprend l'arrivée du nouveau, et le nouveau apprend qui est là.
        // C'est ce qui déclenche les liaisons directes : le serveur ne dit que
        // « untel est là », les téléphones s'arrangent entre eux.
        Map<String, Object> entre = message("entre");
        entre.put("appel", appelId.toString());
        entre.put("qui", qui.toString());
        entre.put("nom", nomDe(qui));
        for (UUID present : deja) registre.envoyer(present, entre);

        Map<String, Object> salle = message("salle");
        salle.put("appel", appelId.toString());
        salle.put("dossier", appel.getDossier().toString());
        salle.put("membres", deja.stream()
                .map(id -> Map.of("id", id.toString(), "nom", nomDe(id)))
                .toList());
        registre.envoyer(qui, salle);
    }

    /** Refuse : les autres l'apprennent, et on ne le resonne pas. */
    public void refuser(UUID qui, UUID appelId) {
        Appel appel = registre.appel(appelId).orElse(null);
        if (appel == null || !appel.getConviés().contains(qui)) return;
        appel.getRefus().add(qui);
        appel.getConviés().remove(qui);

        Map<String, Object> refus = message("refus");
        refus.put("appel", appelId.toString());
        refus.put("qui", qui.toString());
        appel.getPrésents().keySet().forEach(p -> registre.envoyer(p, refus));

        // Plus personne à attendre et personne d'autre en ligne : l'appel meurt
        // au lieu de laisser l'appelant seul devant une sonnerie sans fin.
        if (appel.getConviés().isEmpty() && appel.estTermine()) clore(appel, "refusé");
    }

    /** Raccroche. Le dernier à partir éteint l'appel. */
    public void raccrocher(UUID qui, UUID appelId) {
        Appel appel = registre.appel(appelId).orElse(null);
        if (appel == null) return;
        sortir(appel, qui);
    }

    /** Sort la personne de tous les appels — liaison perdue, application tuée. */
    public void quitterTout(UUID qui) {
        for (Appel appel : new ArrayList<>(registre.tous())) {
            if (appel.estPresent(qui)) sortir(appel, qui);
            else if (appel.getConviés().remove(qui) && appel.getConviés().isEmpty()
                    && appel.estTermine()) {
                clore(appel, "sans réponse");
            }
        }
    }

    /**
     * Relaie une offre, une réponse ou un candidat, sans le lire.
     *
     * <p>La charge n'est pas interprétée : c'est du SDP et des candidats ICE,
     * dont le format évolue avec les navigateurs et les systèmes. La comprendre
     * ici obligerait à suivre ces évolutions pour rien — le serveur n'a besoin
     * de savoir que de qui à qui.</p>
     */
    public void relayer(UUID qui, UUID appelId, UUID vers, Object charge) {
        Appel appel = registre.appel(appelId).orElse(null);
        if (appel == null || vers == null || charge == null) return;
        // Les deux bouts doivent être dans l'appel : sans ce contrôle, une
        // session ouverte pourrait glisser une offre dans la conversation de
        // deux autres personnes.
        if (!appel.estPresent(qui) || !appel.estPresent(vers)) return;

        Map<String, Object> signal = message("signal");
        signal.put("appel", appelId.toString());
        signal.put("de", qui.toString());
        signal.put("charge", charge);
        registre.envoyer(vers, signal);
    }

    // ── Interne ─────────────────────────────────────────────────────────

    private void sortir(Appel appel, UUID qui) {
        if (appel.getPrésents().remove(qui) == null) return;

        Map<String, Object> sort = message("sort");
        sort.put("appel", appel.getId().toString());
        sort.put("qui", qui.toString());
        appel.getPrésents().keySet().forEach(p -> registre.envoyer(p, sort));

        if (appel.estTermine()) clore(appel, "terminé");
    }

    private void clore(Appel appel, String raison) {
        Map<String, Object> fin = message("fin");
        fin.put("appel", appel.getId().toString());
        fin.put("raison", raison);
        appel.getPrésents().keySet().forEach(p -> registre.envoyer(p, fin));
        appel.getConviés().forEach(c -> registre.envoyer(c, fin));

        registre.retirer(appel.getId());
        ecrireAuJournal(appel, raison);
    }

    /**
     * Consigne l'appel : qui, quand, combien de temps — jamais ce qui s'est dit.
     *
     * <p>Un appel engage un avis médical au même titre qu'un message. Ce qui est
     * gardé est ce qui permet de retrouver qu'il a eu lieu ; l'enregistrer
     * demanderait le consentement de tous et créerait un fonds d'archives
     * autrement plus sensible que ce que ce logiciel sait protéger.</p>
     */
    private void ecrireAuJournal(Appel appel, String raison) {
        try {
            JournalAppel trace = new JournalAppel();
            trace.setTestOrderId(appel.getDossier());
            trace.setBranchId(appel.getBranche());
            trace.setInitiateurId(appel.getInitiateur());
            trace.setDebut(appel.getDebut());
            trace.setFin(LocalDateTime.now());
            trace.setSecondes(Duration.between(appel.getDebut(), LocalDateTime.now()).toSeconds());
            trace.setIssue(raison);
            trace.setParticipants(String.join(",", appel.getConviés().stream()
                    .map(UUID::toString).toList()));
            journal.save(trace);
        } catch (Exception e) {
            // Perdre la trace d'un appel est regrettable ; faire échouer sa
            // clôture le laisserait ouvert pour tout le monde.
            log.warn("Journal d'appel impossible", e);
        }
    }

    private void sonnerHorsApp(UUID cible, Appel appel, String code, String nom) {
        try {
            List<String> jetons = appareils.jetonsDe(List.of(cible));
            if (jetons.isEmpty()) return;
            notifications.prevenir(jetons,
                    "Appel — dossier " + code,
                    nom + " vous appelle",
                    Map.of("testOrderId", appel.getDossier().toString(),
                           "codeDemande", code,
                           "appel", appel.getId().toString(),
                           "genre", "appel"));
        } catch (Exception e) {
            log.debug("Sonnerie hors-app impossible : {}", e.getMessage());
        }
    }

    private boolean estParticipant(Discussion fil, UUID qui) {
        return fil.getParticipants().stream()
                .anyMatch(p -> p.getUserId().equals(qui));
    }

    private List<UUID> participants(Discussion fil, UUID sauf) {
        return fil.getParticipants().stream()
                .map(DiscussionParticipant::getUserId)
                .filter(id -> !id.equals(sauf))
                .distinct()
                .limit(Appel.MAXIMUM - 1L)
                .toList();
    }

    private String nomDe(UUID id) {
        return utilisateurs.findById(id)
                .map(u -> NomComplet.de(u.getLastname(), u.getFirstname()))
                .filter(n -> n != null && !n.isBlank())
                .orElse("Quelqu'un");
    }
}
