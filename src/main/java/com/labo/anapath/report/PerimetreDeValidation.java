package com.labo.anapath.report;

import com.labo.anapath.test.TypeOrder;
import com.labo.anapath.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Un type d'examen qu'un compte donné a le droit de valider.
 *
 * <p>La permission {@code validate-reports} dit <em>qu'on peut valider</em> ;
 * ces lignes disent <em>quoi</em>. Le périmètre est attaché au compte et non au
 * rôle : deux secrétaires n'ont pas la même expérience, et le rôle les
 * confondrait.</p>
 *
 * <h2>L'absence de ligne ne veut pas dire la même chose pour tout le monde</h2>
 *
 * <p>Pour un médecin, aucune ligne signifie <em>aucune restriction</em> — c'est
 * le comportement d'avant, et un pathologiste n'a pas à se faire accorder le
 * droit de valider une biopsie. Pour tout autre métier, aucune ligne signifie
 * <em>aucune autorisation</em>. L'asymétrie est délibérée : la faute qu'on veut
 * rendre impossible est d'accorder {@code validate-reports} à un secrétaire en
 * oubliant de borner son périmètre, ce qui lui ouvrirait tout le laboratoire.
 * Voir {@code ServicePerimetreDeValidation}.</p>
 */
@Entity
@Table(name = "perimetre_de_validation")
@Getter
@Setter
@NoArgsConstructor
public class PerimetreDeValidation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_order_id", nullable = false)
    private TypeOrder typeOrder;

    /** Qui a accordé ce périmètre : l'attribution est elle-même un acte. */
    @Column(name = "accorde_par")
    private UUID accordePar;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public PerimetreDeValidation(UUID branchId, User user, TypeOrder typeOrder, UUID accordePar) {
        this.branchId = branchId;
        this.user = user;
        this.typeOrder = typeOrder;
        this.accordePar = accordePar;
    }
}
