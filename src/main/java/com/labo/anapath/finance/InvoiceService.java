package com.labo.anapath.finance;

import com.labo.anapath.common.dto.PageResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface InvoiceService {

    PageResponse<InvoiceResponseDto> findAll(int page, int size, UUID branchId);
    PageResponse<InvoiceResponseDto> findAll(int page, int size, UUID branchId, Boolean paid, LocalDate startDate, LocalDate endDate, String search);
    PageResponse<InvoiceResponseDto> findAll(int page, int size, UUID branchId, Boolean paid, Integer statusInvoice, LocalDate startDate, LocalDate endDate, String search);

    InvoiceResponseDto findById(UUID id, UUID branchId);

    InvoiceResponseDto create(InvoiceRequestDto dto, UUID branchId);

    /**
     * Crée la facture rattachée à un bon d'examen (réplique Laravel {@code storeFromOrder}).
     * Si une facture existe déjà pour ce bon, elle est renvoyée telle quelle (idempotent).
     *
     * @param orderId  identifiant du bon d'examen à facturer
     * @param branchId identifiant de la branche
     * @return la facture créée ou existante
     */
    InvoiceResponseDto createFromOrder(UUID orderId, UUID branchId);

    InvoiceResponseDto update(UUID id, InvoiceRequestDto dto, UUID branchId);

    void delete(UUID id, UUID branchId);

    InvoiceResponseDto markAsPaid(UUID invoiceId, InvoiceStatusUpdateDto dto, UUID branchId);

    BusinessDashboardDto getBusinessDashboard(UUID branchId);

    InvoiceSearchResultDto searchByPeriod(LocalDate startDate, LocalDate endDate, UUID branchId);

    boolean checkCode(String code, UUID branchId);

    /**
     * Total des factures de vente encaissées aujourd'hui sur la branche.
     *
     * @param branchId identifiant de la branche
     * @return montant total encaissé sur la journée en cours (jamais {@code null})
     */
    BigDecimal getTotalEncashedToday(UUID branchId);

    /**
     * Calcule le rapport mensuel d'activité.
     * <p>
     * Si {@code year} ou {@code month} sont {@code null}, la date du jour est utilisée.
     * </p>
     *
     * @param branchId identifiant de la branche
     * @param year     année (optionnelle, par défaut année courante)
     * @param month    mois (optionnel, par défaut mois courant)
     * @return rapport agrégé pour la période demandée
     */
    InvoiceReportDto getReports(UUID branchId, Integer year, Integer month);

    /**
     * Calcule le rapport sur une période libre, ventilé par mois.
     * <p>
     * Une ligne par mois civil couvert, y compris les mois sans aucune facture :
     * un trou dans la suite des mois se lirait comme une donnée manquante, pas
     * comme un mois sans activité. Les totaux de tête récapitulent la période.
     * </p>
     *
     * @param branchId identifiant de la branche
     * @param debut    premier jour, inclus
     * @param fin      dernier jour, <b>inclus</b> — la conversion en borne haute
     *                 exclusive revient à l'implémentation, les colonnes
     *                 comparées étant des timestamps et non des dates
     * @return rapport agrégé et ventilé
     */
    InvoiceReportDto getReportsForPeriod(UUID branchId, LocalDate debut, LocalDate fin);

    /**
     * Statistiques mensuelles (Facturés / Avoirs / CA / Encaissements) pour
     * l'année demandée. Si {@code year} est {@code null}, l'année courante est
     * utilisée. Retourne une ligne par mois jusqu'au mois courant pour l'année
     * en cours, ou les 12 mois pour les années passées.
     *
     * @param branchId identifiant de la branche
     * @param year     année (optionnelle, par défaut année courante)
     * @return liste ordonnée des statistiques mensuelles
     */
    List<InvoiceMonthlyStatsDto> getMonthlyStats(UUID branchId, Integer year);

    /**
     * Crée la facture d'avoir d'une facture de vente.
     *
     * <p>L'avoir est l'opération inverse de la vente. Il s'enregistre seul :
     * la facture d'origine reste intacte, ni annulée ni modifiée — c'est
     * l'existence de l'avoir qui porte l'annulation comptable.</p>
     *
     * <p>Second chemin de création, à côté de celui du circuit de remboursement
     * ({@code RefundServiceImpl.updateStatus}), qui produit le même avoir quand
     * une demande passe à « Aprouvé ». Les deux aboutissent à une facture de
     * {@code statusInvoice = 1} référençant la vente.</p>
     *
     * @param invoiceId facture de vente à contrepasser
     * @param branchId  branche de l'utilisateur
     * @return l'avoir créé
     */
    InvoiceResponseDto createCreditNote(UUID invoiceId, UUID branchId);
}
