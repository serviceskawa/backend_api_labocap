package com.labo.anapath.testorder;

import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.common.security.UserPrincipal;
import com.labo.anapath.report.Report;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Ce qu'un médecin a le droit de voir : ses dossiers, pas ceux des confrères.
 *
 * <p>Un compte au rôle {@code docteur} ne voit que les demandes qui lui sont
 * confiées (affectation directe ou par lot) et les comptes rendus qu'il a
 * relus, validés ou signés — ou dont la demande lui est confiée. Les listes
 * et les fiches passent par ici ; un dossier hors périmètre est « introuvable »,
 * comme un dossier d'une autre agence, pour ne pas révéler qu'il existe.</p>
 *
 * <p>La règle se lit dans le contexte de sécurité plutôt que d'être passée en
 * paramètre : les services de liste reçoivent déjà l'agence, et leur faire
 * porter aussi la personne aurait touché chaque contrôleur et chaque test
 * pour une information que le fil de la requête détient déjà.</p>
 *
 * <p>Un super-admin ou un admin qui serait aussi médecin garde la vue
 * complète : la borne est celle du métier, pas de la double casquette.</p>
 */
@Component
@RequiredArgsConstructor
public class PerimetreDuMedecin {

    private final UserRepository utilisateurs;
    private final TestOrderAssignmentDetailRepository affectations;

    /** L'identifiant du médecin connecté si sa vue doit se borner à ses dossiers ; nul sinon. */
    public UUID medecinBorne() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal personne)) {
            return null;
        }
        return utilisateurs.estMedecinBorne(personne.getId()) ? personne.getId() : null;
    }

    /** Lève « introuvable » si la demande est hors du périmètre du médecin connecté. */
    public void exiger(TestOrder demande) {
        UUID medecin = medecinBorne();
        if (medecin != null && !estConfieeA(demande, medecin)) {
            throw new ResourceNotFoundException("Bon d'examen", demande.getId());
        }
    }

    /** Lève « introuvable » si le compte rendu est hors du périmètre du médecin connecté. */
    public void exiger(Report compteRendu) {
        UUID medecin = medecinBorne();
        if (medecin == null) return;
        boolean signataire = Stream.of(compteRendu.getReviewedBy(), compteRendu.getValidatedBy(),
                        compteRendu.getSignatory1(), compteRendu.getSignatory2(), compteRendu.getSignatory3())
                .filter(Objects::nonNull).map(User::getId).anyMatch(medecin::equals);
        if (!signataire
                && (compteRendu.getTestOrder() == null || !estConfieeA(compteRendu.getTestOrder(), medecin))) {
            throw new ResourceNotFoundException("Compte-rendu", compteRendu.getId());
        }
    }

    private boolean estConfieeA(TestOrder demande, UUID medecin) {
        if (medecin.equals(demande.getAttribuateDoctorId()) || medecin.equals(demande.getAssignedToUserId())) {
            return true;
        }
        // Les lots : l'affectation courante seulement, une demande reprise par
        // un confrère sort de la file de celui qui l'avait.
        return affectations.affectationsCourantes(demande.getId()).stream()
                .anyMatch(d -> d.getTestOrderAssignment() != null
                        && d.getTestOrderAssignment().getUser() != null
                        && medecin.equals(d.getTestOrderAssignment().getUser().getId()));
    }
}
