package com.labo.anapath.finance;

import com.labo.anapath.common.dto.PageResponse;

import java.util.UUID;

public interface CashboxVoucherService {

    PageResponse<CashboxVoucherResponseDto> findAll(int page, int size, UUID branchId);

    CashboxVoucherResponseDto findById(UUID id, UUID branchId);

    /**
     * Compte les bons de caisse en attente de traitement (badge « Caisses » du menu,
     * équivalent du helper Laravel {@code getnbrBonCaissePending()}).
     *
     * @param branchId identifiant de la branche active
     * @return nombre de bons au statut « en attente »
     */
    long countPending(UUID branchId);

    CashboxVoucherResponseDto create(CashboxVoucherRequestDto dto, UUID branchId);

    CashboxVoucherResponseDto update(UUID id, CashboxVoucherRequestDto dto);

    void delete(UUID id);

    CashboxVoucherResponseDto addDetail(UUID voucherId, CashboxVoucherDetailRequestDto dto, UUID branchId);

    void removeDetail(UUID voucherId, UUID detailId);

    CashboxVoucherResponseDto updateStatus(UUID voucherId, CashboxVoucherStatusDto dto, UUID branchId, UUID userId);
}
