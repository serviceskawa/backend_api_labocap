package com.labo.anapath.report;

import com.labo.anapath.common.exception.AccesRefuseExplique;
import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.test.TypeOrder;
import com.labo.anapath.test.TypeOrderRepository;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Décide si un compte peut valider un compte-rendu donné, et administre les
 * périmètres.
 *
 * <p>Le contrôle vient <em>après</em> {@code @PreAuthorize("validate-reports")} :
 * la permission dit qu'on peut valider, ce service dit quoi.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServicePerimetreDeValidation {

    /**
     * Les métiers dont la validation n'est pas bornée.
     *
     * <p>Mêmes slugs que partout ailleurs dans le serveur. Un pathologiste n'a
     * pas à se faire accorder le droit de valider une biopsie : borner son
     * périmètre serait lui retirer l'exercice de son métier par inadvertance,
     * le jour où quelqu'un lui accorderait un type « pour essayer ».</p>
     */
    private static final List<String> METIERS_SANS_BORNE = List.of("docteur", "super-admin");

    private final PerimetreDeValidationRepository perimetres;
    private final UserRepository utilisateurs;
    private final TypeOrderRepository typesDExamen;
    private final JournalDuPerimetreRepository journal;

    /**
     * Ce qu'un compte pouvait valider au moment où il a validé.
     *
     * <p>Rendu au journal du compte-rendu, pour qu'on y lise sous quelle règle
     * l'acte a été posé et non seulement par qui. Sans cela, la validation d'un
     * secrétaire et celle d'un pathologiste laissent la même ligne, et
     * reconstituer la seconde suppose de deviner l'état d'une table qui a
     * changé depuis.</p>
     */
    @Transactional(readOnly = true)
    public String sousQuelleRegle(UUID auteurId) {
        User auteur = utilisateurs.findById(auteurId).orElse(null);
        if (auteur == null) return null;
        String metier = premierMetier(auteur);
        if (exerceUnMetierSansBorne(auteur)) {
            return metier == null ? "périmètre complet" : metier + ", périmètre complet";
        }
        List<String> confies = perimetres.libellesCouverts(auteurId);
        String perimetre = confies.isEmpty() ? "aucun type confié" : String.join(", ", confies);
        return metier == null ? perimetre : metier + ", périmètre : " + perimetre;
    }

    /**
     * Refuse la validation si le type d'examen sort du périmètre du compte.
     *
     * <p>Le message nomme le type refusé et les types couverts : un « accès
     * refusé » sec enverrait l'agent au support alors qu'il lui suffit de savoir
     * que cette demande-là n'est pas pour lui.</p>
     */
    @Transactional(readOnly = true)
    public void exigerLePerimetre(Report compteRendu, UUID auteurId) {
        User auteur = utilisateurs.findById(auteurId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", auteurId));

        if (exerceUnMetierSansBorne(auteur)) {
            return;
        }

        String titre = titreDuType(compteRendu);
        if (titre == null) {
            // Sans type identifiable, aucun périmètre ne peut être vérifié. On
            // refuse plutôt que de laisser passer : c'est la seule décision qui
            // ne crée pas de validation dont personne n'a pesé la portée.
            throw new AccesRefuseExplique(
                    "Le type d'examen de cette demande est inconnu : la validation par périmètre "
                            + "ne peut pas s'appliquer. Un médecin doit valider ce compte-rendu.");
        }

        if (!perimetres.couvreLeType(auteurId, titre)) {
            List<String> couverts = perimetres.libellesCouverts(auteurId);
            throw new AccesRefuseExplique(couverts.isEmpty()
                    ? "Aucun type d'examen ne vous est confié pour la validation. "
                            + "Un administrateur doit vous en accorder."
                    : "Vous ne validez pas les comptes-rendus de type « " + titre + " ». "
                            + "Types qui vous sont confiés : " + String.join(", ", couverts) + ".");
        }
    }

    /** Le périmètre d'un compte, pour l'afficher. */
    @Transactional(readOnly = true)
    public List<String> typesConfies(UUID userId) {
        return perimetres.libellesCouverts(userId);
    }

    /**
     * Remplace le périmètre d'un compte par les types dont les libellés sont
     * donnés.
     *
     * <p>Remplacement et non ajout : l'écran d'administration montre une liste
     * de cases, et son enregistrement doit valoir pour l'état entier — sans
     * quoi décocher un type ne le retirerait jamais.</p>
     *
     * <p>Chaque libellé est accordé sur <strong>toutes</strong> ses lignes
     * {@code type_orders}. La base migrée en porte deux par libellé, et n'en
     * enregistrer qu'une ferait échouer la moitié des demandes du même type
     * sans que rien ne l'explique.</p>
     */
    @Transactional
    public List<String> definirLePerimetre(UUID userId, UUID branchId, List<String> libelles, UUID auteurId) {
        User beneficiaire = utilisateurs.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));

        // Relevé AVANT toute écriture : au-delà, l'état d'origine est perdu et
        // le journal ne pourrait plus dire ce qui a été retiré.
        List<String> avant = perimetres.libellesCouverts(userId);

        perimetres.deleteByUserId(userId);
        // Vidé en base AVANT de réécrire.
        //
        // Hibernate ordonne ses écritures par type — insertions d'abord,
        // suppressions ensuite — et non dans l'ordre où on les demande. Sans ce
        // flush, réenregistrer un périmètre qui conserve un type déjà accordé
        // tentait de l'insérer avant d'avoir retiré l'ancienne ligne, et se
        // heurtait à `uq_perimetre_validation_user_type`. Le défaut ne se voyait
        // pas sur une table vide : il fallait un compte ayant déjà un périmètre,
        // c'est-à-dire exactement le cas courant.
        perimetres.flush();

        if (libelles != null) {
            for (String libelle : libelles) {
                if (libelle == null || libelle.isBlank()) continue;
                List<TypeOrder> lignes = typesDExamen.findAll().stream()
                        .filter(t -> t.getTitle() != null
                                && t.getTitle().trim().equalsIgnoreCase(libelle.trim()))
                        .toList();
                if (lignes.isEmpty()) {
                    // La transaction est annulée : ni le retrait ni la ligne de
                    // journal ne subsistent. Un journal qui enregistrerait une
                    // décision non appliquée serait pire que pas de journal.
                    throw new InvalidOperationException(
                            "Type d'examen inconnu : « " + libelle + " ».");
                }
                for (TypeOrder ligne : lignes) {
                    perimetres.save(new PerimetreDeValidation(branchId, beneficiaire, ligne, auteurId));
                }
            }
        }

        List<String> apres = perimetres.libellesCouverts(userId);

        // Une ligne par décision, même quand elle ne change rien : « on a
        // réexaminé le périmètre de cette personne et on l'a laissé tel quel »
        // est un fait d'audit, et son absence se lirait comme un oubli.
        journal.save(new JournalDuPerimetre(branchId, userId, auteurId, avant, apres));

        log.info("Périmètre de validation de userId={} : [{}] → [{}] par userId={}",
                userId, String.join(", ", avant), String.join(", ", apres), auteurId);
        return apres;
    }

    /** L'historique des décisions sur un compte, du plus récent au plus ancien. */
    @Transactional(readOnly = true)
    public List<JournalDuPerimetre> historique(UUID userId) {
        return journal.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private String premierMetier(User utilisateur) {
        if (utilisateur.getRoles() == null) return null;
        return utilisateur.getRoles().stream()
                .map(r -> r.getName() != null ? r.getName() : r.getSlug())
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private boolean exerceUnMetierSansBorne(User utilisateur) {
        return utilisateur.getRoles() != null && utilisateur.getRoles().stream()
                .map(r -> r.getSlug() == null ? "" : r.getSlug().toLowerCase())
                .anyMatch(METIERS_SANS_BORNE::contains);
    }

    /**
     * Le libellé du type d'examen, ou {@code null} s'il est hors d'atteinte.
     *
     * <p>Le bon d'examen est chargé paresseusement, et sept comptes-rendus de la
     * base pointent vers un bon supprimé logiquement — le proxy ne se résout
     * alors pas et lève {@link jakarta.persistence.EntityNotFoundException}.
     * Cette garde est le premier code à déréférencer le bon pendant une
     * validation : sans ce filet, elle changerait une donnée ancienne en
     * erreur 500 sur un dossier qui se validait la veille.</p>
     *
     * <p>Rendre {@code null} conduit au refus explicite « type inconnu », qui
     * renvoie vers un médecin — lequel n'est pas borné et passe avant même
     * d'arriver ici. Le dossier reste donc traitable.</p>
     */
    private String titreDuType(Report compteRendu) {
        try {
            if (compteRendu.getTestOrder() == null) return null;
            TypeOrder type = compteRendu.getTestOrder().getTypeOrder();
            if (type == null || type.getTitle() == null || type.getTitle().isBlank()) return null;
            return type.getTitle();
        } catch (jakarta.persistence.EntityNotFoundException introuvable) {
            log.warn("Compte-rendu {} : bon d'examen introuvable (supprimé ?), "
                    + "périmètre invérifiable", compteRendu.getId());
            return null;
        }
    }
}
