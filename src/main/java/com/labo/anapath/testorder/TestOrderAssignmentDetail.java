package com.labo.anapath.testorder;

import com.labo.anapath.common.audit.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "test_order_assignment_details")
@Getter
@Setter
@NoArgsConstructor
public class TestOrderAssignmentDetail extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_order_assignment_id", nullable = false)
    private TestOrderAssignment testOrderAssignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_order_id")
    private TestOrder testOrder;

    @Column(name = "test_order_code", length = 50)
    private String testOrderCode;

    /**
     * Étiquettes physiques des prélèvements affectés — « L1 », « L2 »…
     *
     * <p>Une demande regroupe parfois plusieurs prélèvements, et ils ne partent
     * pas toujours tous ensemble. Sans elles, l'affectation dit « la demande
     * 26-0188 » là où la paillasse manipule « L1 et L2 de 26-0188 » ; c'est la
     * seconde formulation qui permet de retrouver un tube.</p>
     *
     * <p>Stockées en JSON plutôt qu'en table : elles ne sont jamais lues seules,
     * et une jointure de plus à chaque lecture ne servirait personne.</p>
     */
    @Column(name = "labels", columnDefinition = "TEXT")
    private String labels;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /**
     * Où en est le médecin sur cette demande.
     *
     * <p>Stocké en clair et non en ordinal : un ordinal se décale dès qu'on
     * insère une valeur au milieu de l'énumération, et rien ne le signale — les
     * dossiers changent alors d'état tous à la fois. Voir {@link DocteurStatus}
     * pour ce que ces trois valeurs disent, et pourquoi elles ne se confondent
     * pas avec le statut du compte rendu.</p>
     */
    @Column(name = "docteur_status", nullable = false, length = 20)
    private String docteurStatus = DocteurStatus.A_TRAITER.valeur();

    /** Le statut, relu. Une valeur inconnue rend « à traiter ». */
    public DocteurStatus statutDuMedecin() {
        return DocteurStatus.depuis(docteurStatus);
    }
}
