package com.labo.anapath.finance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /** Dans l'agence donnée seulement : un identifiant d'ailleurs est introuvable. */
    Optional<Payment> findByIdAndBranchId(UUID id, UUID branchId);

    Page<Payment> findByBranchId(UUID branchId, Pageable pageable);

    Optional<Payment> findByInvoiceId(UUID invoiceId);

    Optional<Payment> findByInvoiceIdAndPaymentStatus(UUID invoiceId, String paymentStatus);
}
