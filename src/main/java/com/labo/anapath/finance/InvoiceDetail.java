package com.labo.anapath.finance;

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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Ligne de détail d'une facture, correspondant à une analyse (LabTest) facturée.
 *
 * <p>Le total de la ligne est calculé selon la formule :
 * {@code total = unitPrice × quantity} (la remise éventuelle est déduite en amont
 * lors de la valorisation du prix unitaire).</p>
 */
@Entity
@Table(name = "invoice_details")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class InvoiceDetail {

    /** Identifiant unique de la ligne de détail. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Facture parente à laquelle cette ligne appartient. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    /** Analyse de laboratoire facturée sur cette ligne. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lab_test_id", nullable = false)
    private LabTest labTest;

    /** Libellé de l'analyse tel qu'imprimé sur la facture (dénormalisé pour l'historique). */
    @Column(name = "test_name", length = 100)
    private String testName;

    /** Prix catalogue de l'analyse au moment de la facturation. */
    /**
     * Un libellé choisi à la main, qui remplace {@link #testName} s'il est posé.
     *
     * <p>Le nom du catalogue ne convient pas toujours à ce qu'on facture : un
     * acte regroupé, une formulation attendue par un assureur, une précision
     * demandée par le patient. Le corriger dans le catalogue changerait toutes
     * les factures à venir — et celle-ci seule est en cause.</p>
     *
     * <p>{@link #testName} n'est pas écrasé pour autant : il reste la trace de
     * l'analyse réellement rendue, et c'est sur lui que se font les
     * rapprochements avec le catalogue.</p>
     */
    @Column(name = "custom_test_name", length = 100)
    private String customTestName;

    @Column(name = "price")
    private Double price;

    /** Remise appliquée sur cette ligne (en valeur absolue). */
    @Column(name = "discount")
    private Double discount;

    /** Nombre d'unités facturées (1 par défaut pour une analyse standard). */
    @Column(name = "quantity", nullable = false)
    private int quantity = 1;

    /** Prix unitaire après application de la remise et du contrat éventuel. */
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice = BigDecimal.ZERO;

    /** Montant total de la ligne : {@code unitPrice × quantity}. */
    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    /** Date et heure de création de la ligne. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Le libellé à faire paraître : le nom personnalisé s'il y en a un.
     *
     * <p>Un seul endroit décide. Le document imprimé et la requête de
     * normalisation doivent porter le même mot : deux lectures séparées de
     * cette règle finiraient par diverger, et la facture papier ne
     * correspondrait plus à celle déclarée à la DGI.</p>
     *
     * <p>Un nom fait d'espaces vaut un nom absent — un champ effleuré au
     * clavier ne doit pas remplacer le libellé du catalogue par du vide.</p>
     */
    public String nomAFacturer() {
        return customTestName != null && !customTestName.isBlank()
                ? customTestName.trim()
                : testName;
    }
}
