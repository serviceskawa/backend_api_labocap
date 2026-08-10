package com.labo.anapath.report;

import com.labo.anapath.common.dto.PageResponse;

import java.util.List;
import java.util.UUID;

public interface ReportService {

    PageResponse<ReportResponseDto> findAll(int page, int size, UUID branchId);

    PageResponse<ReportResponseDto> findAll(int page, int size, UUID branchId, Integer month, Integer year, UUID doctorId);

    PageResponse<ReportResponseDto> findAll(int page, int size, UUID branchId, Integer month, Integer year, UUID doctorId, String status, String search);

    ReportResponseDto findById(UUID id);

    ReportDetailDto findDetailById(UUID id, UUID branchId);

    ReportResponseDto create(ReportRequestDto dto, UUID branchId);

    ReportResponseDto createOrUpdate(ReportRequestDto dto, UUID branchId);

    ReportResponseDto update(UUID id, ReportRequestDto dto, UUID userId, UUID branchId);

    void delete(UUID id);

    ReportResponseDto validate(UUID id, UUID userId);

    ReportResponseDto deliver(UUID id, String receiverName, UUID userId);

    ReportResponseDto markDelivered(UUID id, UUID userId);

    ReportResponseDto markInformed(UUID id, UUID userId);

    ReportResponseDto storeSignature(UUID id, StoreSignatureRequestDto dto, UUID userId);

    ReportSuiviDto getSuivi(UUID branchId, Integer month, Integer year);

    PageResponse<ReportSuiviRowDto> getSuiviList(
            UUID branchId, int page, int size,
            String search, String typeOrderId,
            String dateBegin, String dateEnd,
            Boolean isUrgent, Integer statusFilter, Boolean isLate);

    PageResponse<LogReportResponseDto> getReportLogs(UUID branchId, int page, int size);

    PageResponse<ReportGlobalSearchRowDto> globalSearch(
            UUID branchId, int page, int size,
            List<String> typeOrderIds,
            List<String> contratIds,
            List<String> patientIds,
            List<String> doctorIds,
            List<String> hospitalIds,
            String referenceHospital,
            String dateBegin, String dateEnd,
            String content, Boolean isUrgent);

    com.labo.anapath.setting.SettingReportTemplate getTemplate(UUID reportId);

    ReportResponseDto setTemplate(UUID reportId, UUID templateId);

    void logAction(UUID reportId, String action, UUID userId);

    PageResponse<ReportListDto> getList(
            UUID branchId, int page, int size,
            String search, String statusFilter, String dateBegin, String dateEnd);

    ReportPerformanceDto getPerformanceStats(
            UUID branchId, String doctorId, Integer month, Integer year);

    /**
     * Modifications apportées au compte-rendu après sa signature.
     *
     * @param reportId identifiant UUID du compte-rendu
     * @return liste chronologique, vide si le compte-rendu n'a pas bougé depuis
     *         sa signature — le cas de l'immense majorité des dossiers
     */
    List<ModificationApresSignatureDto> getModificationsApresSignature(UUID reportId);
}
