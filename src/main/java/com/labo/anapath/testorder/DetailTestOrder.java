package com.labo.anapath.testorder;

import com.labo.anapath.test.LabTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité représentant une ligne d'analyse dans un bon d'examen ({@link TestOrder}).
 *
 * <p>Chaque détail correspond à une analyse (LabTest) commandée, avec son prix unitaire,
 * sa remise et le total calculé. Le champ {@code status} sert de marqueur de facturation :
 * {@code null} ou {@code true} = non encore facturé, {@code false} = facturé.
 */
@Entity
@Table(name = "detail_test_orders")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class DetailTestOrder {

    /** Identifiant unique de la ligne de détail. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Bon d'examen auquel appartient ce détail. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_order_id", nullable = false)
    private TestOrder testOrder;

    /**
     * Analyse de laboratoire commandée.
     *
     * <p>{@link NotFound} : {@link LabTest} est en soft delete. Sans {@code IGNORE},
     * la suppression d'une analyse encore référencée par un détail ferait échouer
     * le mapping du bon (voir la note sur les associations de {@link TestOrder}).
     * Le libellé reste disponible via le snapshot {@code testName}.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "lab_test_id", nullable = false)
    private LabTest labTest;

    /** Nom de l'analyse au moment de la commande (snapshot pour traçabilité). */
    @Column(name = "test_name", length = 300, nullable = false)
    private String testName;

    /** Prix unitaire de l'analyse au moment de la commande. */
    @Column(name = "price", nullable = false)
    private Double price = 0.0;

    /** Remise en pourcentage appliquée sur le prix unitaire. */
    @Column(name = "discount")
    private Double discount = 0.0;

    /** Total calculé : {@code prix - (prix × remise / 100)}. */
    @Column(name = "total", nullable = false)
    private Double total = 0.0;

    /**
     * Marqueur de facturation :
     * {@code null} ou {@code true} = non encore facturé, {@code false} = déjà inclus dans une facture.
     */
    @Column(name = "status")
    private Boolean status;

    /** Date de création de la ligne (gérée automatiquement par JPA Auditing). */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Date de dernière modification (gérée automatiquement par JPA Auditing). */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
