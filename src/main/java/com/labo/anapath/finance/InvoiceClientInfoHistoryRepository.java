package com.labo.anapath.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceClientInfoHistoryRepository extends JpaRepository<InvoiceClientInfoHistory, UUID> {

    List<InvoiceClientInfoHistory> findByInvoiceIdOrderByCreatedAtDesc(UUID invoiceId);
}
