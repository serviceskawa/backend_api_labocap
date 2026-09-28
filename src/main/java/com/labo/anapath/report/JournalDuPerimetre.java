package com.labo.anapath.report;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Une décision d'accorder ou de retirer le droit de valider certains types.
 *
 * <p>La table {@code perimetre_de_validation} ne porte que l'état courant, et
 * l'enregistrement d'un périmètre efface puis réécrit ses lignes. Sans ce
 * journal, retirer un type effacerait toute trace qu'il ait jamais été
 * accordé — et un compte-rendu validé six mois plus tôt se retrouverait sans
 * règle visible pour l'expliquer.</p>
 *
 * <p>Les types sont conservés en clair et non par identifiant : ce journal se
 * lit des années après, quand la ligne {@code type_orders} visée peut avoir été
 * renommée ou supprimée, et un UUID orphelin ne dirait plus rien.</p>
 */
@Entity
@Table(name = "journal_du_perimetre_de_validation")
@Getter
@Setter
@NoArgsConstructor
public class JournalDuPerimetre {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    /** Le compte dont le périmètre a changé. */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Qui a décidé. */
    @Column(name = "accorde_par")
    private UUID accordePar;

    @Column(name = "types_avant", nullable = false, columnDefinition = "TEXT")
    private String typesAvant = "";

    @Column(name = "types_apres", nullable = false, columnDefinition = "TEXT")
    private String typesApres = "";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public JournalDuPerimetre(UUID branchId, UUID userId, UUID accordePar,
                              List<String> avant, List<String> apres) {
        this.branchId = branchId;
        this.userId = userId;
        this.accordePar = accordePar;
        this.typesAvant = joindre(avant);
        this.typesApres = joindre(apres);
    }

    /**
     * Ordonné avant d'être joint.
     *
     * <p>Sans cela, deux enregistrements du même périmètre dans un ordre
     * différent produiraient deux lignes qui semblent décrire un changement.
     * Un journal qui signale des mouvements qui n'ont pas eu lieu finit par ne
     * plus être lu.</p>
     */
    private static String joindre(List<String> types) {
        if (types == null || types.isEmpty()) return "";
        return types.stream().sorted(String.CASE_INSENSITIVE_ORDER)
                .reduce((a, b) -> a + ", " + b).orElse("");
    }
}
