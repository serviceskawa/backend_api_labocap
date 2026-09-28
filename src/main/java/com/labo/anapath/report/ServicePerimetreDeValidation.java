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

        perimetres.deleteByUserId(userId);
        if (libelles == null || libelles.isEmpty()) {
            log.info("Périmètre de validation vidé pour userId={} par userId={}", userId, auteurId);
            return List.of();
        }

        for (String libelle : libelles) {
            if (libelle == null || libelle.isBlank()) continue;
            List<TypeOrder> lignes = typesDExamen.findAll().stream()
                    .filter(t -> t.getTitle() != null
                            && t.getTitle().trim().equalsIgnoreCase(libelle.trim()))
                    .toList();
            if (lignes.isEmpty()) {
                throw new InvalidOperationException(
                        "Type d'examen inconnu : « " + libelle + " ».");
            }
            for (TypeOrder ligne : lignes) {
                perimetres.save(new PerimetreDeValidation(branchId, beneficiaire, ligne, auteurId));
            }
        }

        log.info("Périmètre de validation de userId={} fixé à {} par userId={}",
                userId, libelles, auteurId);
        return perimetres.libellesCouverts(userId);
    }

    private boolean exerceUnMetierSansBorne(User utilisateur) {
        return utilisateur.getRoles() != null && utilisateur.getRoles().stream()
                .map(r -> r.getSlug() == null ? "" : r.getSlug().toLowerCase())
                .anyMatch(METIERS_SANS_BORNE::contains);
    }

    private String titreDuType(Report compteRendu) {
        if (compteRendu.getTestOrder() == null) return null;
        TypeOrder type = compteRendu.getTestOrder().getTypeOrder();
        if (type == null || type.getTitle() == null || type.getTitle().isBlank()) return null;
        return type.getTitle();
    }
}
