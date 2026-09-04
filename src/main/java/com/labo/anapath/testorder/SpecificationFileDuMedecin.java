package com.labo.anapath.testorder;

import com.labo.anapath.report.Report;
import com.labo.anapath.report.ReportStatus;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * La file de travail d'un médecin, telle que le serveur sait la découper.
 *
 * <h2>Pourquoi une spécification et non une requête à paramètres facultatifs</h2>
 *
 * <p>La forme {@code (:borne IS NULL OR colonne >= :borne)} paraît la façon
 * naturelle de rendre un critère optionnel en JPQL. Elle ne fonctionne pas :
 * PostgreSQL ne sait pas typer un paramètre qu'il ne rencontre que dans un test
 * de nullité, et rejette la requête entière — l'échec n'apparaît qu'à
 * l'exécution, contre une vraie base. Une spécification n'ajoute que les
 * prédicats demandés : ce qui n'est pas filtré n'existe pas dans le SQL.</p>
 */
final class SpecificationFileDuMedecin {

    private SpecificationFileDuMedecin() {}

    /**
     * @param docteurId        le médecin dont c'est la file
     * @param aujourdhui       la date du jour, pour la fenêtre des dossiers clos
     * @param joursAvantAlerte le délai au-delà duquel un dossier est en retard
     */
    static Specification<TestOrderAssignmentDetail> filtrer(
            UUID docteurId, LocalDate aujourdhui, int joursAvantAlerte,
            FiltreFileDuMedecin filtre) {
        return (racine, requete, cb) -> {
            var lot = racine.join("testOrderAssignment");
            var demande = racine.join("testOrder", JoinType.LEFT);

            List<Predicate> predicats = new ArrayList<>();
            predicats.add(cb.equal(lot.get("user").get("id"), docteurId));
            predicats.add(cb.isNull(racine.get("deletedAt")));
            // Une ligne remplacée appartient au médecin précédent.
            predicats.add(cb.isNull(racine.get("remplaceeLe")));
            // Les dossiers terminés restent visibles le jour même : les retirer
            // à l'instant où on les ferme ferait douter d'avoir enregistré.
            predicats.add(cb.or(
                    cb.notEqual(racine.get("docteurStatus"), DocteurStatus.TERMINE.valeur()),
                    cb.greaterThanOrEqualTo(lot.get("date"), aujourdhui)));

            if (filtre.annee() != null) {
                var debut = LocalDate.of(filtre.annee(), 1, 1).atStartOfDay();
                predicats.add(cb.greaterThanOrEqualTo(demande.get("createdAt"), debut));
                predicats.add(cb.lessThan(demande.get("createdAt"), debut.plusYears(1)));
            }
            if (renseigne(filtre.lot())) {
                predicats.add(cb.equal(lot.get("code"), filtre.lot()));
            }
            if (renseigne(filtre.docteurStatus())) {
                predicats.add(cb.equal(racine.get("docteurStatus"), filtre.docteurStatus()));
            }
            if (Boolean.TRUE.equals(filtre.exclureTermines())) {
                predicats.add(cb.notEqual(racine.get("docteurStatus"),
                        DocteurStatus.TERMINE.valeur()));
            }
            if (renseigne(filtre.statutDemande())) {
                predicats.add(cb.equal(demande.get("status"),
                        TestOrderStatus.valueOf(filtre.statutDemande())));
            }
            if (Boolean.TRUE.equals(filtre.urgents())) {
                // L'urgence se décide à l'accueil et cesse de valoir à la
                // remise : le délai qu'elle visait est alors tenu.
                predicats.add(cb.isTrue(demande.get("isUrgent")));
                predicats.add(demande.get("status").in(
                        TestOrderStatus.DELIVERED, TestOrderStatus.CANCELLED).not());
            }
            if (Boolean.TRUE.equals(filtre.enRetard())) {
                predicats.add(cb.lessThan(demande.get("createdAt"),
                        aujourdhui.minusDays(joursAvantAlerte).atStartOfDay()));
                // Un compte rendu validé ou remis solde le retard, quel que
                // soit l'âge du dossier.
                var sousRequete = requete.subquery(Long.class);
                var compteRendu = sousRequete.from(Report.class);
                sousRequete.select(cb.literal(1L)).where(
                        cb.equal(compteRendu.get("testOrder").get("id"), demande.get("id")),
                        cb.isNull(compteRendu.get("deletedAt")),
                        compteRendu.get("status").in(
                                ReportStatus.VALIDATED, ReportStatus.DELIVERED));
                predicats.add(cb.not(cb.exists(sousRequete)));
            }
            if (filtre.demandes() != null) {
                // Liste vide : aucune demande ne convient. « in() » sans valeur
                // est refusé par certains pilotes, d'où la fausseté explicite.
                predicats.add(filtre.demandes().isEmpty()
                        ? cb.disjunction()
                        : demande.get("id").in(filtre.demandes()));
            }
            return cb.and(predicats.toArray(new Predicate[0]));
        };
    }

    private static boolean renseigne(String valeur) {
        return valeur != null && !valeur.isBlank();
    }
}
