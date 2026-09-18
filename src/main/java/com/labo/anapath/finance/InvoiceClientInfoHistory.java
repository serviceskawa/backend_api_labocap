package com.labo.anapath.finance;

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

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Trace une actualisation manuelle du nom/adresse client d'une facture depuis
 * les informations courantes du patient rattaché : qui l'a déclenchée, quand,
 * et les valeurs avant/après. La facture elle-même reste figée tant que
 * personne ne déclenche cette action explicite.
 */
@Entity
@Table(name = "invoice_client_info_history")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class InvoiceClientInfoHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "old_client_name")
    private String oldClientName;

    @Column(name = "new_client_name")
    private String newClientName;

    @Column(name = "old_client_address")
    private String oldClientAddress;

    @Column(name = "new_client_address")
    private String newClientAddress;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
