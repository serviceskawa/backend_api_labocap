package com.labo.anapath.contract;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ContratResponseDto(
        UUID id,
        String name,
        String type,
        String description,
        UUID hospitalId,
        String hospitalName,
        UUID clientId,
        String clientName,
        int nbrTests,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        Boolean invoiceUnique,
        Boolean isClose,
        List<DetailsContratDto> details,
        UUID branchId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        // Enrichissements de la vue détail (null sur le endpoint liste)
        Integer usedTestsCount,
        ContratInvoiceDto invoice
) {}
