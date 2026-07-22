package com.labo.anapath.finance;

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
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "cashbox_dailies")
@SQLDelete(sql = "UPDATE cashbox_dailies SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class CashboxDaily extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cashbox_id", nullable = false)
    private Cashbox cashbox;

    @Column(name = "opening_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "closing_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal closingBalance = BigDecimal.ZERO;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    // 1 = caisse ouverte, 0 = caisse fermée
    @Column(name = "status")
    private Integer status = 0;

    @Column(name = "code", length = 50)
    private String code;

    // Décompte espèces
    @Column(name = "cash_calculated", precision = 12, scale = 2)
    private BigDecimal cashCalculated;

    @Column(name = "cash_confirmation", precision = 12, scale = 2)
    private BigDecimal cashConfirmation;

    @Column(name = "cash_ecart", precision = 12, scale = 2)
    private BigDecimal cashEcart;

    // Décompte mobile money
    @Column(name = "mobile_money_calculated", precision = 12, scale = 2)
    private BigDecimal mobileMoneyCalculated;

    @Column(name = "mobile_money_confirmation", precision = 12, scale = 2)
    private BigDecimal moneyMoneyConfirmation;

    @Column(name = "mobile_money_ecart", precision = 12, scale = 2)
    private BigDecimal mobileMoneyEcart;

    // Décompte chèques
    @Column(name = "cheque_calculated", precision = 12, scale = 2)
    private BigDecimal chequeCalculated;

    @Column(name = "cheque_confirmation", precision = 12, scale = 2)
    private BigDecimal chequeConfirmation;

    @Column(name = "cheque_ecart", precision = 12, scale = 2)
    private BigDecimal chequeEcart;

    // Décompte virements
    @Column(name = "virement_calculated", precision = 12, scale = 2)
    private BigDecimal virementCalculated;

    @Column(name = "virement_confirmation", precision = 12, scale = 2)
    private BigDecimal virementConfirmation;

    @Column(name = "virement_ecart", precision = 12, scale = 2)
    private BigDecimal virementEcart;

    // Totaux
    @Column(name = "total_calculated", precision = 12, scale = 2)
    private BigDecimal totalCalculated;

    @Column(name = "total_confirmation", precision = 12, scale = 2)
    private BigDecimal totalConfirmation;

    @Column(name = "total_ecart", precision = 12, scale = 2)
    private BigDecimal totalEcart;

    // Commentaire de clôture (calque Laravel « Commentaire » du récapitulatif).
    @Column(name = "description", columnDefinition = "text")
    private String description;
}
