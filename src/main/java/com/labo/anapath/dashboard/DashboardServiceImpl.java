package com.labo.anapath.dashboard;

import com.labo.anapath.appointment.AppointmentRepository;
import com.labo.anapath.client.ClientRepository;
import com.labo.anapath.contract.ContratRepository;
import com.labo.anapath.finance.InvoiceRepository;
import com.labo.anapath.patient.PatientRepository;
import com.labo.anapath.report.ReportRepository;
import com.labo.anapath.report.ReportStatus;
import com.labo.anapath.test.LabTestRepository;
import com.labo.anapath.testorder.TestOrderRepository;
import com.labo.anapath.testorder.TestOrderStatus;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {

    private final PatientRepository patientRepository;
    private final ClientRepository clientRepository;
    private final TestOrderRepository testOrderRepository;
    private final InvoiceRepository invoiceRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ContratRepository contratRepository;
    private final LabTestRepository labTestRepository;
    private final AppointmentRepository appointmentRepository;

    // -----------------------------------------------------------------------
    // Admin KPIs
    // -----------------------------------------------------------------------

    @Override
    public DashboardDto.AdminStats getAdminStats(UUID branchId) {
        LocalDate now = LocalDate.now();
        LocalDate startCurrentMonth = now.withDayOfMonth(1);
        LocalDate startPrevMonth = startCurrentMonth.minusMonths(1);
        LocalDate endPrevMonth = startCurrentMonth.minusDays(1);

        // Patients
        long valPatient = patientRepository.countByBranchId(branchId);
        long currPatient = patientRepository.countByBranchIdAndCreatedAtBetween(branchId,
                startCurrentMonth.atStartOfDay(), now.atTime(LocalTime.MAX));
        long prevPatient = patientRepository.countByBranchIdAndCreatedAtBetween(branchId,
                startPrevMonth.atStartOfDay(), endPrevMonth.atTime(LocalTime.MAX));
        double crPatient = calcGrowth(currPatient, prevPatient);

        // Clients
        long valClient = clientRepository.countByBranchId(branchId);
        long currClient = clientRepository.countByBranchIdAndCreatedAtBetween(branchId,
                startCurrentMonth.atStartOfDay(), now.atTime(LocalTime.MAX));
        long prevClient = clientRepository.countByBranchIdAndCreatedAtBetween(branchId,
                startPrevMonth.atStartOfDay(), endPrevMonth.atTime(LocalTime.MAX));
        double crClient = calcGrowth(currClient, prevClient);

        // TestOrders
        long valTO = testOrderRepository.countByBranchId(branchId);
        long currTO = testOrderRepository.countByBranchIdAndCreatedAtBetween(branchId,
                startCurrentMonth.atStartOfDay(), now.atTime(LocalTime.MAX));
        long prevTO = testOrderRepository.countByBranchIdAndCreatedAtBetween(branchId,
                startPrevMonth.atStartOfDay(), endPrevMonth.atTime(LocalTime.MAX));
        double crTO = calcGrowth(currTO, prevTO);

        // Invoices
        BigDecimal valInvoice = invoiceRepository.sumTotalByBranchId(branchId);
        if (valInvoice == null) valInvoice = BigDecimal.ZERO;
        BigDecimal currInv = invoiceRepository.sumTotalByBranchIdAndDateRange(branchId,
                startCurrentMonth, now);
        if (currInv == null) currInv = BigDecimal.ZERO;
        BigDecimal prevInv = invoiceRepository.sumTotalByBranchIdAndDateRange(branchId,
                startPrevMonth, endPrevMonth);
        if (prevInv == null) prevInv = BigDecimal.ZERO;
        double crInvoice = prevInv.compareTo(BigDecimal.ZERO) != 0
                ? currInv.subtract(prevInv).multiply(BigDecimal.valueOf(100))
                        .divide(prevInv, 2, RoundingMode.HALF_UP).doubleValue()
                : 0.0;

        // Donut « STATUT D'EXAMENS » — équivalent du $totalByStatus de Laravel :
        // terminé = compte rendu validé ou remis, en attente = brouillon ou en relecture.
        long finishTest = reportRepository.countByBranchIdAndStatusIn(branchId,
                List.of(ReportStatus.VALIDATED, ReportStatus.DELIVERED));
        long noFinishTest = reportRepository.countByBranchIdAndStatusIn(branchId,
                List.of(ReportStatus.DRAFT, ReportStatus.PENDING_REVIEW));

        return new DashboardDto.AdminStats(valPatient, crPatient, valClient, crClient,
                valTO, crTO, valInvoice, crInvoice, finishTest, noFinishTest);
    }

    // -----------------------------------------------------------------------
    // Secrétariat stats
    // -----------------------------------------------------------------------

    @Override
    public DashboardDto.SecretariatStats getSecretariatStats(UUID branchId) {
        long patients = patientRepository.countByBranchId(branchId);
        long contrats = contratRepository.countByBranchId(branchId);
        long tests = labTestRepository.countByBranchId(branchId);
        long toCount = testOrderRepository.countByBranchId(branchId);
        // finishTest = reports livrés, noFinishTest = reports non livrés
        long finishTest = reportRepository.countByBranchIdAndIsDelivered(branchId, true);
        long noFinishTest = reportRepository.countByBranchIdAndIsDelivered(branchId, false);
        // noSaveTest = test_orders sans report
        long noSaveTest = testOrderRepository.countByBranchIdAndReportIsNull(branchId);
        // noFinishWeek = testorders pending depuis > 3 semaines
        LocalDateTime threeWeeksAgo = LocalDateTime.now().minusWeeks(3);
        long noFinishWeek = testOrderRepository.countByBranchIdAndStatusPendingAndCreatedAtBefore(branchId, threeWeeksAgo);

        return new DashboardDto.SecretariatStats(patients, contrats, tests, toCount,
                finishTest, noFinishTest, noSaveTest, noFinishWeek);
    }

    // -----------------------------------------------------------------------
    // Reports today
    // -----------------------------------------------------------------------

    @Override
    public List<DashboardDto.ReportToday> getReportsToday(UUID branchId) {
        LocalDate today = LocalDate.now();
        return reportRepository.findReportsTodayByBranchId(branchId, today).stream()
                .map(p -> new DashboardDto.ReportToday(
                        p.getId(), p.getTestOrderId(), p.getCode(),
                        p.getPatientLastname(), p.getPatientFirstname(),
                        p.getCreatedAt(), p.getStatus() != null ? p.getStatus() : 0,
                        Boolean.TRUE.equals(p.getIsDeliver()), p.getInvoiceId()))
                .toList();
    }

    // -----------------------------------------------------------------------
    // Doctor stats
    // -----------------------------------------------------------------------

    @Override
    public List<DashboardDto.DoctorStat> getDoctorStats(UUID branchId) {
        return testOrderRepository.getDoctorStatsByBranchId(branchId).stream()
                .map(p -> new DashboardDto.DoctorStat(p.getId(), p.getDoctor(),
                        p.getAssigne() != null ? p.getAssigne() : 0L,
                        p.getTraite() != null ? p.getTraite() : 0L))
                .toList();
    }

    // -----------------------------------------------------------------------
    // Top examens
    // -----------------------------------------------------------------------

    @Override
    public List<DashboardDto.TopExamen> getTopExamens(UUID branchId) {
        return testOrderRepository.getTopExamensByBranchId(branchId, 7).stream()
                .map(p -> new DashboardDto.TopExamen(p.getTestName(),
                        p.getTotalDemandes() != null ? p.getTotalDemandes() : 0L))
                .toList();
    }

    // -----------------------------------------------------------------------
    // Monthly stats
    // -----------------------------------------------------------------------

    @Override
    public DashboardDto.MonthlyStats getMonthlyStats(UUID branchId) {
        // Mois seul, sans l'année : voir le commentaire de parité Laravel dans
        // TestOrderRepository (section « Dashboard — stats mensuelles »).
        int month = LocalDate.now().getMonthValue();

        long nombreTests = testOrderRepository.countByBranchIdAndMonth(branchId, month);
        BigDecimal caTests = testOrderRepository.sumPriceByBranchIdAndMonth(branchId, month);
        if (caTests == null) caTests = BigDecimal.ZERO;
        long totalPatientTest = testOrderRepository.countPatientsByBranchIdAndMonth(branchId, month);

        List<DashboardDto.ByItem> byHopital = testOrderRepository.countByHospitalAndMonth(branchId, month)
                .stream().map(p -> new DashboardDto.ByItem(p.getNom(), p.getTotalPatients() != null ? p.getTotalPatients() : 0L)).toList();
        List<DashboardDto.ByItem> byMedecin = testOrderRepository.countByDoctorAndMonth(branchId, month)
                .stream().map(p -> new DashboardDto.ByItem(p.getNom(), p.getTotalPatients() != null ? p.getTotalPatients() : 0L)).toList();
        List<DashboardDto.ByItem> byType = testOrderRepository.countByTypeOrderAndMonth(branchId, month)
                .stream().map(p -> new DashboardDto.ByItem(p.getNom(), p.getTotalPatients() != null ? p.getTotalPatients() : 0L)).toList();

        return new DashboardDto.MonthlyStats(nombreTests, caTests, totalPatientTest,
                byHopital, byMedecin, byType);
    }

    // -----------------------------------------------------------------------
    // Connected users
    // -----------------------------------------------------------------------

    @Override
    public List<DashboardDto.ConnectedUser> getConnectedUsers(UUID branchId) {
        return userRepository.findConnectedUsersByBranchId(branchId, LocalDate.now()).stream()
                .map(p -> new DashboardDto.ConnectedUser(p.getId(), p.getLastname(),
                        p.getFirstname(), p.getEmail()))
                .toList();
    }

    // -----------------------------------------------------------------------
    // Revenue data
    // -----------------------------------------------------------------------

    @Override
    public DashboardDto.RevenueData getRevenueData(UUID branchId) {
        LocalDate today = LocalDate.now();
        LocalDate startCurrentWeek = today.with(DayOfWeek.MONDAY);
        LocalDate endCurrentWeek = today.with(DayOfWeek.SUNDAY);
        LocalDate startLastWeek = startCurrentWeek.minusWeeks(1);
        LocalDate endLastWeek = endCurrentWeek.minusWeeks(1);

        BigDecimal totalCurrentWeek = invoiceRepository.sumPaidSalesByBranchIdAndDateRange(branchId, startCurrentWeek, endCurrentWeek);
        if (totalCurrentWeek == null) totalCurrentWeek = BigDecimal.ZERO;
        BigDecimal totalLastWeek = invoiceRepository.sumPaidSalesByBranchIdAndDateRange(branchId, startLastWeek, endLastWeek);
        if (totalLastWeek == null) totalLastWeek = BigDecimal.ZERO;
        BigDecimal totalToday = invoiceRepository.sumPaidByBranchIdAndDate(branchId, today);
        if (totalToday == null) totalToday = BigDecimal.ZERO;

        // La requête ne renvoie que les jours ayant au moins une facture encaissée :
        // on complète la semaine pour toujours livrer 7 points, du lundi au dimanche.
        // Sans cela, le graphe du chiffre d'affaires n'a aucun point à tracer les
        // semaines creuses, et les deux séries ne s'alignent pas jour par jour.
        List<DashboardDto.DayRevenue> currentWeekByDay =
                fillWeek(startCurrentWeek, invoiceRepository.sumPaidByDayInRange(branchId, startCurrentWeek, endCurrentWeek));
        List<DashboardDto.DayRevenue> lastWeekByDay =
                fillWeek(startLastWeek, invoiceRepository.sumPaidByDayInRange(branchId, startLastWeek, endLastWeek));

        return new DashboardDto.RevenueData(totalCurrentWeek, totalLastWeek, totalToday,
                currentWeekByDay, lastWeekByDay);
    }

    /**
     * Complète une série journalière sur les 7 jours de la semaine commençant à
     * {@code weekStart} : chaque jour sans facture encaissée reçoit un total de zéro.
     *
     * @param weekStart lundi de la semaine concernée
     * @param rows      lignes renvoyées par la base (uniquement les jours non vides)
     * @return 7 points ordonnés du lundi au dimanche
     */
    private List<DashboardDto.DayRevenue> fillWeek(
            LocalDate weekStart, List<DashboardProjection.DayRevenue> rows) {
        java.util.Map<String, BigDecimal> byDate = new java.util.HashMap<>();
        for (DashboardProjection.DayRevenue row : rows) {
            byDate.put(row.getDate(), nullSafe(row.getTotal()));
        }
        List<DashboardDto.DayRevenue> week = new java.util.ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            String date = weekStart.plusDays(i).toString();
            week.add(new DashboardDto.DayRevenue(date, byDate.getOrDefault(date, BigDecimal.ZERO)));
        }
        return week;
    }

    // -----------------------------------------------------------------------
    // Invoice status (4 catégories)
    // -----------------------------------------------------------------------

    @Override
    public DashboardDto.InvoiceStatus getInvoiceStatus(UUID branchId) {
        long invoicePaid = invoiceRepository.countByBranchIdAndStatusInvoiceAndPaid(branchId, 0, true);
        BigDecimal invoiceTotalPaid = invoiceRepository.sumByBranchIdAndStatusInvoiceAndPaid(branchId, 0, true);
        long invoiceNoPaid = invoiceRepository.countByBranchIdAndStatusInvoiceAndPaid(branchId, 0, false);
        BigDecimal invoiceTotalNoPaid = invoiceRepository.sumByBranchIdAndStatusInvoiceAndPaid(branchId, 0, false);
        long refundPaid = invoiceRepository.countByBranchIdAndStatusInvoiceAndPaid(branchId, 1, true);
        BigDecimal refundTotalPaid = invoiceRepository.sumByBranchIdAndStatusInvoiceAndPaid(branchId, 1, true);
        long refundNoPaid = invoiceRepository.countByBranchIdAndStatusInvoiceAndPaid(branchId, 1, false);
        BigDecimal refundTotalNoPaid = invoiceRepository.sumByBranchIdAndStatusInvoiceAndPaid(branchId, 1, false);

        return new DashboardDto.InvoiceStatus(
                invoicePaid, invoiceNoPaid, refundPaid, refundNoPaid,
                nullSafe(invoiceTotalPaid), nullSafe(invoiceTotalNoPaid),
                nullSafe(refundTotalPaid), nullSafe(refundTotalNoPaid));
    }

    // -----------------------------------------------------------------------
    // Doctor-specific
    // -----------------------------------------------------------------------

    @Override
    public DashboardDto.ExamStatusChart getExamStatusForDoctor(UUID userId, UUID branchId) {
        long termine = testOrderRepository.countByAssignedToUserIdAndBranchIdAndStatus(
                userId, branchId, TestOrderStatus.DELIVERED);
        long enAttente = testOrderRepository.countByAssignedToUserIdAndBranchIdAndStatus(
                userId, branchId, TestOrderStatus.PENDING)
                + testOrderRepository.countByAssignedToUserIdAndBranchIdAndStatus(
                userId, branchId, TestOrderStatus.VALIDATED);
        return new DashboardDto.ExamStatusChart(termine, enAttente);
    }

    @Override
    public List<DashboardDto.AppointmentDto> getAppointmentsForDoctor(UUID userId, UUID branchId) {
        return appointmentRepository.findPendingByDoctorInterne(userId).stream()
                .map(p -> new DashboardDto.AppointmentDto(p.getId(), p.getPatientName(),
                        p.getDate(), p.getPriority(), p.getStatus(), p.getMessage()))
                .toList();
    }

    // -----------------------------------------------------------------------
    // Doctor orders (affectés via attribuate_doctor_id)
    // -----------------------------------------------------------------------

    @Override
    public List<DashboardDto.DoctorOrder> getDoctorOrders(UUID userId, UUID branchId) {
        return testOrderRepository.findAllByAttribuateDoctorId(userId, branchId).stream()
                .map(p -> new DashboardDto.DoctorOrder(p.getId(), p.getCode(), p.getCreatedAt(),
                        p.getPatientFirstname(), p.getPatientLastname(),
                        p.getReportStatus() != null ? p.getReportStatus() : 0))
                .toList();
    }

    @Override
    public List<DashboardDto.DoctorOrder> getDoctorOrdersToday(UUID userId, UUID branchId) {
        return testOrderRepository.findTodayByAttribuateDoctorId(userId, branchId, LocalDate.now()).stream()
                .map(p -> new DashboardDto.DoctorOrder(p.getId(), p.getCode(), p.getCreatedAt(),
                        p.getPatientFirstname(), p.getPatientLastname(),
                        p.getReportStatus() != null ? p.getReportStatus() : 0))
                .toList();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private double calcGrowth(long curr, long prev) {
        if (prev == 0) return 0.0;
        return Math.round(((double) (curr - prev) / prev) * 10000.0) / 100.0;
    }

    private BigDecimal nullSafe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
