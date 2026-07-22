package com.labo.anapath.hr;

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
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entité représentant un contrat de travail d'un employé du laboratoire.
 * <p>
 * Un employé peut avoir plusieurs contrats successifs (CDD, CDI, stage…).
 * Cette entité n'hérite pas de {@code AuditableEntity} et gère directement
 * ses propres champs d'audit via {@link AuditingEntityListener}.
 * </p>
 */
@Entity
@Table(name = "employee_contrats")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class EmployeeContrat {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "type", length = 50)
    private String type;

    @Column(name = "salary", nullable = false, precision = 10, scale = 2)
    private BigDecimal salary = BigDecimal.ZERO;

    // ── Onglet « Contrat » (calque du wizard contrat Laravel) ───────────────

    @Column(name = "probation_end_date")
    private LocalDate probationEndDate;

    @Column(name = "weekly_work_hours")
    private Integer weeklyWorkHours;

    @Column(name = "working_days_per_week")
    private Integer workingDaysPerWeek;

    @Column(name = "termination_reason", columnDefinition = "text")
    private String terminationReason;

    // ── Onglet « Paie » ─────────────────────────────────────────────────────

    @Column(name = "hourly_gross_rate", precision = 10, scale = 2)
    private BigDecimal hourlyGrossRate;

    @Column(name = "transport_allowance", precision = 10, scale = 2)
    private BigDecimal transportAllowance;

    @Column(name = "iban", length = 50)
    private String iban;

    @Column(name = "bic", length = 20)
    private String bic;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
