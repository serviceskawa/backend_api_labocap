package com.labo.anapath.report;

import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.setting.SettingReportTemplateRepository;
import com.labo.anapath.testorder.TestOrder;
import com.labo.anapath.testorder.TestOrderRepository;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock private ReportRepository reportRepository;
    @Mock private LogReportRepository logReportRepository;
    @Mock private TagRepository tagRepository;
    @Mock private TitleReportRepository titleReportRepository;
    @Mock private TestOrderRepository testOrderRepository;
    @Mock private UserRepository userRepository;
    @Mock private SettingReportTemplateRepository templateRepository;
    @Mock private ReportMapper reportMapper;

    @InjectMocks
    private ReportServiceImpl service;

    private final UUID BRANCH_ID = UUID.randomUUID();
    private final UUID ORDER_ID = UUID.randomUUID();
    private final UUID REPORT_ID = UUID.randomUUID();
    private final UUID TAG_1 = UUID.randomUUID();
    private final UUID TAG_2 = UUID.randomUUID();
    private final UUID USER_ID = UUID.randomUUID();

    private TestOrder buildOrder() {
        TestOrder o = new TestOrder();
        o.setCode("EX26-0001");
        return o;
    }

    private Report buildDraftReport() {
        Report r = new Report();
        r.setBranchId(BRANCH_ID);
        r.setStatus(ReportStatus.DRAFT);
        r.setTags(new ArrayList<>());
        return r;
    }

    private Tag buildTag(String name) {
        Tag t = new Tag();
        t.setName(name);
        return t;
    }

    @Test
    @DisplayName("createOrUpdate - création avec tagIds → sync tags delete+reinsert")
    void createReport_withTagIds_syncsTagsDeleteAndReinsert() {
        ReportRequestDto dto = new ReportRequestDto();
        dto.setTestOrderId(ORDER_ID);
        dto.setTagIds(List.of(TAG_1, TAG_2));

        Tag tag1 = buildTag("Histologie");
        Tag tag2 = buildTag("Cytologie");

        when(testOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(buildOrder()));
        when(tagRepository.findAllById(List.of(TAG_1, TAG_2))).thenReturn(List.of(tag1, tag2));
        when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reportMapper.toResponseDto(any())).thenReturn(null);

        service.createOrUpdate(dto, BRANCH_ID);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getTags()).containsExactlyInAnyOrder(tag1, tag2);
    }

    @Test
    @DisplayName("createOrUpdate - status VALIDATED → signatureDate et deliveryDate posées")
    void updateReport_withStatus_VALIDATED_setsSignatureDate() {
        Report existing = buildDraftReport();
        existing.setTestOrder(buildOrder());

        ReportRequestDto dto = new ReportRequestDto();
        dto.setReportId(REPORT_ID);
        dto.setStatus("VALIDATED");

        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(existing));
        when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reportMapper.toResponseDto(any())).thenReturn(null);

        service.createOrUpdate(dto, BRANCH_ID);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReportStatus.VALIDATED);
        assertThat(captor.getValue().getSignatureDate()).isNotNull();
        assertThat(captor.getValue().getDeliveryDate()).isNotNull();
    }

    @Test
    @DisplayName("createOrUpdate - signatory1Id → met à jour testOrder.assignedToUserId")
    void updateReport_withSignatory1_updatesTestOrderAssignedTo() {
        TestOrder order = buildOrder();
        Report existing = buildDraftReport();
        existing.setTestOrder(order);

        ReportRequestDto dto = new ReportRequestDto();
        dto.setReportId(REPORT_ID);
        dto.setStatus("VALIDATED");
        dto.setSignatory1Id(USER_ID);

        User doctor = new User();
        doctor.setFirstname("Dr");
        doctor.setLastname("Test");

        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(existing));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(doctor));
        when(testOrderRepository.save(any())).thenReturn(order);
        when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reportMapper.toResponseDto(any())).thenReturn(null);

        service.createOrUpdate(dto, BRANCH_ID);

        assertThat(order.getAssignedToUserId()).isEqualTo(USER_ID);
        verify(testOrderRepository).save(order);
    }

    @Test
    @DisplayName("findAll filtré - délègue au repository avec month/year/doctorId")
    void findAll_withMonthYearFilter_returnsFilteredReports() {
        Integer month = 5;
        Integer year = 2026;
        UUID doctorId = UUID.randomUUID();

        when(reportRepository.findFiltered(eq(BRANCH_ID), eq(month), eq(year), eq(doctorId), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.findAll(0, 20, BRANCH_ID, month, year, doctorId);

        verify(reportRepository).findFiltered(eq(BRANCH_ID), eq(month), eq(year), eq(doctorId), any(Pageable.class));
    }

    /**
     * Remplace un test qui consacrait le verrou inverse.
     *
     * <p>Un complément arrive après la remise du résultat : refuser la
     * modification d'un compte-rendu livré rendait inatteignable la fonction
     * même qui sert à ce moment-là. Laravel ne posait aucun verrou — la
     * livraison y était un drapeau {@code is_delivered} distinct du statut.</p>
     */
    @Test
    @DisplayName("createOrUpdate - un rapport livré reste modifiable (complément)")
    void createOrUpdate_deliveredReport_estModifiable() {
        Report delivered = buildDraftReport();
        delivered.setStatus(ReportStatus.DELIVERED);
        delivered.setTestOrder(buildOrder());

        ReportRequestDto dto = new ReportRequestDto();
        dto.setReportId(REPORT_ID);
        dto.setDescriptionSupplementaire("Complément après remise du résultat.");

        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(delivered));
        when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reportMapper.toResponseDto(any())).thenReturn(null);

        service.createOrUpdate(dto, BRANCH_ID);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getDescriptionSupplementaire())
                .isEqualTo("Complément après remise du résultat.");
    }

    // ===== Tests story 3-7 — Validation, signature, livraison =====

    @Test
    @DisplayName("validate - DRAFT → VALIDATED + signatureDate posée")
    void validate_setsStatusValidatedAndSignatureDate() {
        Report report = buildDraftReport();
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));
        when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reportMapper.toResponseDto(any())).thenReturn(null);

        service.validate(REPORT_ID, USER_ID);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(ReportStatus.VALIDATED);
        assertThat(captor.getValue().getSignatureDate()).isNotNull();
    }

    @Test
    @DisplayName("validate - déjà VALIDATED → InvalidOperationException")
    void validate_alreadyValidated_throws() {
        Report report = buildDraftReport();
        report.setStatus(ReportStatus.VALIDATED);
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.validate(REPORT_ID, USER_ID))
                .isInstanceOf(InvalidOperationException.class);
        verify(reportRepository, never()).save(any());
    }

    @Test
    @DisplayName("markDelivered - isDelivered=true + deliveryDate posée")
    void markDelivered_setsIsDeliveredTrueAndLogsAction() {
        Report report = buildDraftReport();
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));
        when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reportMapper.toResponseDto(any())).thenReturn(null);

        service.markDelivered(REPORT_ID, USER_ID);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().isDelivered()).isTrue();
        assertThat(captor.getValue().getDeliveryDate()).isNotNull();
    }

    @Test
    @DisplayName("markInformed - isCalled=true + callDate posée")
    void markInformed_setsIsCalledTrueAndLogsAction() {
        Report report = buildDraftReport();
        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));
        when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reportMapper.toResponseDto(any())).thenReturn(null);

        service.markInformed(REPORT_ID, USER_ID);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        assertThat(captor.getValue().isCalled()).isTrue();
        assertThat(captor.getValue().getCallDate()).isNotNull();
    }

    @Test
    @DisplayName("storeSignature - RÈGLE R5 : isDelivered ET isCalled positionnés simultanément")
    void storeSignature_setsIsDeliveredAndIsCalledSimultaneously() {
        Report report = buildDraftReport();
        StoreSignatureRequestDto dto = new StoreSignatureRequestDto();
        dto.setSignatorName("Jean Dupont");
        dto.setSignature("data:image/png;base64,abc123");

        when(reportRepository.findById(REPORT_ID)).thenReturn(Optional.of(report));
        when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reportMapper.toResponseDto(any())).thenReturn(null);

        service.storeSignature(REPORT_ID, dto, USER_ID);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(captor.capture());
        Report saved = captor.getValue();
        assertThat(saved.isDelivered()).isTrue();
        assertThat(saved.isCalled()).isTrue();
        assertThat(saved.getRetrieverName()).isEqualTo("Jean Dupont");
        assertThat(saved.getRetrieverSignature()).isEqualTo("data:image/png;base64,abc123");
        assertThat(saved.getDeliveryDate()).isNotNull();
        assertThat(saved.getCallDate()).isNotNull();
    }
}
