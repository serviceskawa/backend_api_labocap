package com.labo.anapath.finance;

import com.labo.anapath.common.dto.PageResponse;

import java.util.UUID;

public interface CashboxDailyService {

    CashboxDailyResponseDto openOrUpdate(CashboxDailyOpenDto dto, UUID branchId, UUID userId);

    PageResponse<CashboxDailyResponseDto> findAll(int page, int size, UUID branchId);

    CashboxDailyResponseDto findById(UUID id, UUID branchId);

    CashboxDailyResponseDto closeCashbox(UUID id, CashboxDailyCloseDto dto, UUID userId);

    /**
     * @param sessionId session concernée par la fermeture ; {@code null} retombe
     *                  sur la dernière session ouverte de la branche
     */
    CashboxDailySummaryDto getDailySummary(UUID branchId, UUID sessionId);

    void delete(UUID id);
}
