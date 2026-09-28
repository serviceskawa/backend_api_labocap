package com.labo.anapath.report;

import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Consigne sur un compte-rendu une tentative qui a été refusée.
 *
 * <p>Dans une transaction <strong>à part</strong>, et c'est toute sa raison
 * d'être : le refus remonte en exception, la transaction de validation est donc
 * annulée, et une écriture faite dedans disparaîtrait avec elle. C'est aussi
 * pourquoi ce composant est un bean distinct plutôt qu'une méthode privée —
 * Spring n'applique pas {@code @Transactional} à un appel qu'un objet se fait à
 * lui-même, et la nouvelle transaction n'aurait jamais commencé.</p>
 *
 * <p>Sans cette trace, seules les validations réussies seraient auditables. Qui
 * a tenté de valider un dossier qui n'était pas le sien ne se lirait que dans
 * les journaux applicatifs, qui tournent et finissent par se perdre ; le
 * journal du compte-rendu, lui, vit aussi longtemps que le dossier.</p>
 */
@Component
@RequiredArgsConstructor
public class JournalDesRefus {

    private final ReportRepository comptesRendus;
    private final LogReportRepository journal;
    private final UserRepository utilisateurs;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void refusDeValidation(UUID reportId, UUID auteurId, String motif) {
        comptesRendus.findById(reportId).ifPresent(compteRendu -> {
            LogReport ligne = new LogReport();
            ligne.setBranchId(compteRendu.getBranchId());
            ligne.setReport(compteRendu);
            ligne.setAction("Validation refusée");
            ligne.setDescription(motif == null ? "Validation refusée" : motif);
            utilisateurs.findById(auteurId).ifPresent(ligne::setUser);
            journal.save(ligne);
        });
    }
}
