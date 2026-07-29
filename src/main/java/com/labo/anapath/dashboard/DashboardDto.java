package com.labo.anapath.dashboard;

import java.math.BigDecimal;
import java.util.List;

public class DashboardDto {

    // KPIs admin
    /**
     * Cartes et donut du tableau de bord admin.
     *
     * <p>{@code finishTest} / {@code noFinishTest} alimentent le graphique
     * « STATUT D'EXAMENS » et reprennent le {@code $totalByStatus} de Laravel
     * (bons d'examen joints à leur compte rendu, regroupés par statut).</p>
     */
    public record AdminStats(
        long valeurPatient, double crPatient,
        long valeurClient, double crClient,
        long valeurTestOrder, double crTestOrder,
        BigDecimal valeurInvoice, double crInvoice,
        long finishTest, long noFinishTest
    ) {}

    // Stats secrétariat
    public record SecretariatStats(
        long patients, long contrats, long tests,
        long testOrdersCount, long finishTest, long noFinishTest,
        long noSaveTest, long noFinishWeek
    ) {}

    // Rapport disponible aujourd'hui
    public record ReportToday(
        String id, String testOrderId, String code,
        String patientLastname, String patientFirstname,
        String createdAt, int status, boolean isDeliver,
        String invoiceId
    ) {}

    // Stat par docteur
    public record DoctorStat(String id, String doctor, long assigne, long traite) {}

    // Top examen
    public record TopExamen(String testName, long totalDemandes) {}

    // Stats mensuelles
    public record MonthlyStats(
        long nombreTests, BigDecimal caTests, long totalPatientTest,
        List<ByItem> byHopital,
        List<ByItem> byMedecin,
        List<ByItem> byType
    ) {}

    public record ByItem(String nom, long totalPatients) {}

    // Utilisateurs connectés
    public record ConnectedUser(String id, String lastname, String firstname, String email) {}

    // Revenus finance
    public record RevenueData(
        BigDecimal totalCurrentWeek,
        BigDecimal totalLastWeek,
        BigDecimal totalToday,
        List<DayRevenue> currentWeekByDay,
        List<DayRevenue> lastWeekByDay
    ) {}

    public record DayRevenue(String date, BigDecimal total) {}

    // Statut factures (4 catégories)
    public record InvoiceStatus(
        long invoicePaid, long invoiceNoPaid,
        long refundPaid, long refundNoPaid,
        BigDecimal invoiceTotalPaid, BigDecimal invoiceTotalNoPaid,
        BigDecimal refundTotalPaid, BigDecimal refundTotalNoPaid
    ) {}

    // Statut examens (pour docteur)
    public record ExamStatusChart(long termine, long enAttente) {}

    // Rendez-vous pour docteur
    public record AppointmentDto(
        String id, String patientName, String date,
        String priority, String status, String message
    ) {}

    // Bons affectés au pathologiste
    public record DoctorOrder(
        String id,
        String code,
        String createdAt,
        String patientFirstname,
        String patientLastname,
        int reportStatus
    ) {}
}
