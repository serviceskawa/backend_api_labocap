package com.labo.anapath.finance;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.contract.ContratRepository;
import com.labo.anapath.patient.PatientRepository;
import com.labo.anapath.setting.SettingInvoiceRepository;
import com.labo.anapath.test.LabTestRepository;
import com.labo.anapath.testorder.DetailTestOrder;
import com.labo.anapath.testorder.TestOrder;
import com.labo.anapath.testorder.TestOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceDetailRepository invoiceDetailRepository;
    private final TestOrderRepository testOrderRepository;
    private final PatientRepository patientRepository;
    private final LabTestRepository labTestRepository;
    private final CashboxRepository cashboxRepository;
    private final ContratRepository contratRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final RefundReasonRepository refundReasonRepository;
    private final SettingInvoiceRepository settingInvoiceRepository;
    private final FinanceMapper financeMapper;
    private final com.labo.anapath.report.QrCodeService qrCodeService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponseDto> findAll(int page, int size, UUID branchId) {
        return PageResponse.of(invoiceRepository.findByBranchId(branchId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(financeMapper::toInvoiceResponseDto));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponseDto> findAll(int page, int size, UUID branchId,
            Boolean paid, LocalDate startDate, LocalDate endDate, String search) {
        return findAll(page, size, branchId, paid, null, startDate, endDate, search);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InvoiceResponseDto> findAll(int page, int size, UUID branchId,
            Boolean paid, Integer statusInvoice, LocalDate startDate, LocalDate endDate, String search) {
        if (paid == null && statusInvoice == null && startDate == null && endDate == null
                && (search == null || search.isBlank())) {
            return findAll(page, size, branchId);
        }
        java.time.LocalDateTime startDt = startDate != null ? startDate.atStartOfDay() : null;
        java.time.LocalDateTime endDt = endDate != null ? endDate.atTime(23, 59, 59) : null;
        return PageResponse.of(invoiceRepository.findFiltered(
                branchId, paid, statusInvoice, startDt, endDt,
                (search != null && !search.isBlank()) ? search : null,
                PageRequest.of(page, size))
                .map(financeMapper::toInvoiceResponseDto));
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceResponseDto findById(UUID id, UUID branchId) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Facture", id));
        if (!invoice.getBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Facture", id);
        }
        InvoiceResponseDto dto = financeMapper.toInvoiceResponseDto(invoice);
        dto = financeMapper.withRefund(dto, findRefundFor(invoice));
        return financeMapper.withQrcode(dto, buildQrcode(invoice));
    }

    /**
     * QR code du reçu, généré à la demande comme le fait Laravel
     * (`InvoiceController@show`) : il encode le code normalisé DGI, et retombe
     * sur le nom du centre tant que la facture n'est pas normalisée.
     */
    private String buildQrcode(Invoice invoice) {
        String content = invoice.getCodeNormalise() != null && !invoice.getCodeNormalise().isBlank()
                ? invoice.getCodeNormalise()
                : "Centre ADECHINA Anatomie Pathologique";
        try {
            return qrCodeService.generateBase64(content, 300);
        } catch (Exception e) {
            log.warn("QR code non généré pour la facture {}: {}", invoice.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Remboursement rattaché à une facture d'avoir, ou null.
     *
     * <p>Réplique Laravel {@code InvoiceController::show} : le remboursement se
     * cherche par l'identifiant de la facture de vente d'origine ({@code reference}),
     * et non par celui de l'avoir lui-même.</p>
     */
    private InvoiceRefundDto findRefundFor(Invoice invoice) {
        if (invoice.getStatusInvoice() != 1 || invoice.getReference() == null) {
            return null;
        }
        return refundRequestRepository.findByInvoiceId(invoice.getReference().getId())
                .map(refund -> new InvoiceRefundDto(
                        refund.getCode(),
                        refund.getRefundReasonId() == null ? "" :
                                refundReasonRepository.findById(refund.getRefundReasonId())
                                        .map(RefundReason::getLabel).orElse(""),
                        refund.getMontant(),
                        refund.getInvoice() == null ? "" : refund.getInvoice().getCode()))
                .orElse(null);
    }

    @Override
    @Transactional
    public InvoiceResponseDto createFromOrder(UUID orderId, UUID branchId) {
        // Réplique Laravel storeFromOrder : une seule facture par bon d'examen.
        // Si une facture existe déjà pour ce bon, on la renvoie plutôt que d'échouer.
        return invoiceRepository.findByTestOrderId(orderId)
                .map(existing -> findById(existing.getId(), branchId))
                .orElseGet(() -> {
                    InvoiceRequestDto dto = new InvoiceRequestDto();
                    dto.setTestOrderId(orderId);
                    return create(dto, branchId);
                });
    }

    @Override
    @Transactional
    public InvoiceResponseDto create(InvoiceRequestDto dto, UUID branchId) {
        // 1. Récupérer le bon d'examen (isolation multi-tenant via branchId)
        TestOrder order = testOrderRepository.findByIdAndBranchId(dto.getTestOrderId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'examen", dto.getTestOrderId()));

        // 2. Vérifier qu'aucune facture active (vente, non soft-deleted) n'existe déjà pour ce bon
        if (invoiceRepository.existsByTestOrderIdAndStatusInvoice(order.getId(), 0)) {
            throw new InvalidOperationException("Il existe déjà une facture pour cette demande d'examen");
        }

        // 3. Créer la facture en copiant les informations du bon d'examen
        Invoice invoice = new Invoice();
        invoice.setBranchId(branchId);
        invoice.setTestOrder(order);
        invoice.setPatient(order.getPatient());
        invoice.setContrat(order.getContrat());

        // Copie des montants depuis le bon (subtotal/discount sont des Double côté entité)
        invoice.setSubtotal(order.getSubtotal() != null ? order.getSubtotal() : 0.0);
        invoice.setDiscount(order.getDiscount() != null ? order.getDiscount() : 0.0);
        invoice.setTotal(order.getTotal() != null
                ? BigDecimal.valueOf(order.getTotal())
                : BigDecimal.ZERO);

        invoice.setPaid(false);
        invoice.setStatus(InvoiceStatus.PENDING);
        invoice.setStatusInvoice(0); // vente
        invoice.setDate(LocalDate.now()); // calque Laravel : 'date' => Carbon::now()
        invoice.setDueDate(dto.getDueDate());

        // 4. Génération automatique du code facture (format FAYYNNNN)
        int year = LocalDate.now().getYear();
        int yearTwoDigits = year % 100;
        long count = invoiceRepository.countByBranchIdAndCreatedAtYear(branchId, year) + 1;
        invoice.setCode(String.format("FA%02d%04d", yearTwoDigits, count));

        // 5. Sauvegarder pour obtenir l'ID
        Invoice saved = invoiceRepository.save(invoice);

        // 6. Créer les lignes de détail à partir des analyses du bon d'examen
        List<DetailTestOrder> orderDetails = order.getDetails();
        if (orderDetails != null) {
            for (DetailTestOrder od : orderDetails) {
                InvoiceDetail detail = new InvoiceDetail();
                detail.setInvoice(saved);
                detail.setLabTest(od.getLabTest());
                detail.setTestName(od.getTestName());
                detail.setPrice(od.getPrice() != null ? od.getPrice() : 0.0);
                detail.setDiscount(od.getDiscount() != null ? od.getDiscount() : 0.0);
                detail.setTotal(od.getTotal() != null
                        ? BigDecimal.valueOf(od.getTotal())
                        : BigDecimal.ZERO);
                invoiceDetailRepository.save(detail);
            }
        }

        // Refresh pour récupérer la facture avec ses détails fraîchement insérés
        saved = invoiceRepository.findById(saved.getId()).orElse(saved);
        return financeMapper.toInvoiceResponseDto(saved);
    }

    @Override
    @Transactional
    public InvoiceResponseDto update(UUID id, InvoiceRequestDto dto, UUID branchId) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Facture", id));
        if (!invoice.getBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Facture", id);
        }
        invoice.setDueDate(dto.getDueDate());
        return financeMapper.toInvoiceResponseDto(invoiceRepository.save(invoice));
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID branchId) {
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Facture", id));
        if (!invoice.getBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Facture", id);
        }
        invoiceRepository.delete(invoice);
    }

    @Override
    @Transactional
    public InvoiceResponseDto markAsPaid(UUID invoiceId, InvoiceStatusUpdateDto dto, UUID branchId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Facture", invoiceId));
        if (!invoice.getBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Facture", invoiceId);
        }

        if (Boolean.TRUE.equals(invoice.getPaid())) {
            throw new InvalidOperationException("INVOICE_ALREADY_PAID");
        }

        invoice.setPaid(true);
        invoice.setPayment(dto.getPayment());
        invoice.setStatus(InvoiceStatus.PAID);

        // Laravel updateStatus : le code saisi n'est enregistré en code_normalise que
        // lorsque la normalisation automatique est désactivée. Quand elle est active,
        // c'est MECeF qui fournit le code et la saisie est ignorée.
        boolean invoiceNormalised = settingInvoiceRepository.findFirstByBranchId(invoice.getBranchId())
                .map(setting -> Boolean.TRUE.equals(setting.getStatus()))
                .orElse(false);
        if (!invoiceNormalised) {
            invoice.setCodeNormalise(dto.getCode());
        }

        // R2 — JAMAIS par ID hardcodé : utiliser findByBranchIdAndType
        if (invoice.getStatusInvoice() == 0) {
            // Facture de vente → crédit caisse vente
            Cashbox cashVente = cashboxRepository.findFirstByBranchIdAndType(invoice.getBranchId(), "vente")
                    .orElseThrow(() -> new ResourceNotFoundException("Caisse vente", null));
            cashVente.setBalance(cashVente.getBalance().add(invoice.getTotal()));
            cashboxRepository.save(cashVente);
        } else {
            // Avoir → débit caisse dépense
            Cashbox cashDepense = cashboxRepository.findFirstByBranchIdAndType(invoice.getBranchId(), "depense")
                    .orElseThrow(() -> new ResourceNotFoundException("Caisse dépense", null));
            cashDepense.setBalance(cashDepense.getBalance().subtract(invoice.getTotal()));
            cashboxRepository.save(cashDepense);
        }

        // R4 — Auto-clôture contrat si invoice_unique
        if (invoice.getContrat() != null && Boolean.TRUE.equals(invoice.getContrat().getInvoiceUnique())) {
            invoice.getContrat().setIsClose(true);
            contratRepository.save(invoice.getContrat());
        }

        Invoice saved = invoiceRepository.save(invoice);
        return financeMapper.withRefund(financeMapper.toInvoiceResponseDto(saved), findRefundFor(saved));
    }

    @Override
    @Transactional(readOnly = true)
    public BusinessDashboardDto getBusinessDashboard(UUID branchId) {
        LocalDate today = LocalDate.now();
        int currentMonth = today.getMonthValue();
        int currentYear = today.getYear();
        int lastMonth = today.minusMonths(1).getMonthValue();
        int lastYear = today.minusMonths(1).getYear();

        BigDecimal totalToday = invoiceRepository.sumPaidByBranchIdAndDate(branchId, today);
        BigDecimal totalMonth = invoiceRepository.sumPaidByBranchIdAndMonth(branchId, currentMonth, currentYear);
        BigDecimal totalLastMonth = invoiceRepository.sumPaidByBranchIdAndMonth(branchId, lastMonth, lastYear);

        return new BusinessDashboardDto(totalToday, totalMonth, totalLastMonth);
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceSearchResultDto searchByPeriod(LocalDate startDate, LocalDate endDate, UUID branchId) {
        BigDecimal ca = invoiceRepository.sumVenteByBranchIdAndDateRange(branchId, startDate, endDate);
        BigDecimal avoir = invoiceRepository.sumAvoirByBranchIdAndDateRange(branchId, startDate, endDate);
        BigDecimal facture = invoiceRepository.sumTotalByBranchIdAndDateRange(branchId, startDate, endDate);
        BigDecimal encaissement = ca.subtract(avoir);
        return new InvoiceSearchResultDto(ca, avoir, facture, encaissement);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean checkCode(String code, UUID branchId) {
        return invoiceRepository
                .findFirstByCodeMecefAndBranchIdOrCodeNormaliseAndBranchId(code, branchId, code, branchId)
                .isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getTotalEncashedToday(UUID branchId) {
        LocalDate today = LocalDate.now();
        BigDecimal result = invoiceRepository.sumPaidByBranchIdAndDate(branchId, today);
        return result != null ? result : BigDecimal.ZERO;
    }

    @Override
    @Transactional(readOnly = true)
    public InvoiceReportDto getReports(UUID branchId, Integer year, Integer month) {
        int y = year != null ? year : LocalDate.now().getYear();
        int m = month != null ? month : LocalDate.now().getMonthValue();

        BigDecimal sales = invoiceRepository.sumByBranchIdMonthYearAndStatus(branchId, m, y, 0);
        BigDecimal credits = invoiceRepository.sumByBranchIdMonthYearAndStatus(branchId, m, y, 1);
        BigDecimal collections = invoiceRepository.sumPaidByBranchIdMonthYear(branchId, m, y);
        if (sales == null) sales = BigDecimal.ZERO;
        if (credits == null) credits = BigDecimal.ZERO;
        if (collections == null) collections = BigDecimal.ZERO;

        BigDecimal turnover = sales.subtract(credits);

        List<InvoiceReportDto.ContractTotal> byContracts = invoiceRepository
                .sumByContractAndMonthYear(branchId, m, y)
                .stream()
                .map(row -> new InvoiceReportDto.ContractTotal(
                        (String) row[0],
                        row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO))
                .toList();

        String period = MOIS_FR[m] + " " + y;

        return new InvoiceReportDto(period, sales, credits, turnover, collections, byContracts);
    }

    /** Libellés de mois, indexés de 1 à 12 — la case 0 est un bouchon. */
    private static final String[] MOIS_FR = {"", "Janvier", "Février", "Mars", "Avril",
            "Mai", "Juin", "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"};

    @Override
    @Transactional(readOnly = true)
    public InvoiceReportDto getReportsForPeriod(UUID branchId, LocalDate debut, LocalDate fin) {
        if (debut == null || fin == null) {
            throw new InvalidOperationException("Les deux bornes de la période sont requises");
        }
        if (fin.isBefore(debut)) {
            throw new InvalidOperationException(
                    "La date de fin ne peut pas précéder la date de début");
        }

        // Borne haute EXCLUSIVE, au lendemain à 00:00. Les colonnes comparées
        // sont des timestamps : un `<=` posé sur la date de fin perdrait toutes
        // les factures du dernier jour créées après minuit — soit, en pratique,
        // toutes celles du dernier jour de la période.
        LocalDateTime debutInstant = debut.atStartOfDay();
        LocalDateTime finExclusive = fin.plusDays(1).atStartOfDay();

        Map<YearMonth, BigDecimal> ventes = agregerParMois(
                invoiceRepository.sumMonthlyByStatusInPeriod(branchId, debutInstant, finExclusive, 0));
        Map<YearMonth, BigDecimal> avoirs = agregerParMois(
                invoiceRepository.sumMonthlyByStatusInPeriod(branchId, debutInstant, finExclusive, 1));
        Map<YearMonth, BigDecimal> encaisses = agregerParMois(
                invoiceRepository.sumMonthlyPaidInPeriod(branchId, debutInstant, finExclusive));

        // On parcourt les mois civils de la période plutôt que les clés rendues
        // par la base : un mois sans aucune facture doit apparaître à zéro, sans
        // quoi le trou dans la suite se lit comme une donnée manquante.
        List<InvoiceReportDto.MonthlyRow> lignes = new ArrayList<>();
        BigDecimal totalVentes = BigDecimal.ZERO;
        BigDecimal totalAvoirs = BigDecimal.ZERO;
        BigDecimal totalEncaisses = BigDecimal.ZERO;

        YearMonth dernier = YearMonth.from(fin);
        for (YearMonth ym = YearMonth.from(debut); !ym.isAfter(dernier); ym = ym.plusMonths(1)) {
            BigDecimal v = ventes.getOrDefault(ym, BigDecimal.ZERO);
            BigDecimal a = avoirs.getOrDefault(ym, BigDecimal.ZERO);
            BigDecimal e = encaisses.getOrDefault(ym, BigDecimal.ZERO);

            lignes.add(new InvoiceReportDto.MonthlyRow(
                    ym.getYear(), ym.getMonthValue(),
                    MOIS_FR[ym.getMonthValue()] + " " + ym.getYear(),
                    v, a, v.subtract(a), e));

            totalVentes = totalVentes.add(v);
            totalAvoirs = totalAvoirs.add(a);
            totalEncaisses = totalEncaisses.add(e);
        }

        List<InvoiceReportDto.ContractTotal> parContrat = invoiceRepository
                .sumByContractInPeriod(branchId, debutInstant, finExclusive)
                .stream()
                .map(row -> new InvoiceReportDto.ContractTotal(
                        (String) row[0],
                        row[1] != null ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO))
                .toList();

        return new InvoiceReportDto(
                libellePeriode(debut, fin),
                totalVentes,
                totalAvoirs,
                totalVentes.subtract(totalAvoirs),
                totalEncaisses,
                parContrat,
                lignes);
    }

    /** Indexe par mois les lignes {@code [année, mois, total]} rendues par la base. */
    private static Map<YearMonth, BigDecimal> agregerParMois(List<Object[]> lignes) {
        Map<YearMonth, BigDecimal> parMois = new HashMap<>();
        for (Object[] row : lignes) {
            YearMonth ym = YearMonth.of(((Number) row[0]).intValue(), ((Number) row[1]).intValue());
            BigDecimal total = row[2] != null ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
            // `merge` et non `put` : la requête groupe déjà par mois, mais un
            // changement de regroupement ne doit pas écraser une valeur en silence.
            parMois.merge(ym, total, BigDecimal::add);
        }
        return parMois;
    }

    /**
     * Libellé lisible d'une période. Une période qui recouvre exactement un mois
     * civil s'annonce par ce mois — « Août 2026 » plutôt que
     * « 1 août – 31 août 2026 », qui dit la même chose en deux fois plus long.
     */
    private static String libellePeriode(LocalDate debut, LocalDate fin) {
        YearMonth ym = YearMonth.from(debut);
        if (YearMonth.from(fin).equals(ym)
                && debut.getDayOfMonth() == 1
                && fin.equals(ym.atEndOfMonth())) {
            return MOIS_FR[ym.getMonthValue()] + " " + ym.getYear();
        }
        DateTimeFormatter jourMois = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH);
        String gauche = debut.format(jourMois);
        // L'année n'est répétée à gauche que si la période enjambe deux années.
        if (debut.getYear() != fin.getYear()) {
            gauche += " " + debut.getYear();
        }
        return gauche + " – " + fin.format(jourMois) + " " + fin.getYear();
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceMonthlyStatsDto> getMonthlyStats(UUID branchId, Integer year) {
        int targetYear = year != null ? year : LocalDate.now().getYear();
        var rawStats = invoiceRepository.findMonthlyStatsRaw(branchId, targetYear);

        // Indexer par mois pour accès rapide
        java.util.Map<Integer, Object[]> byMonth = new java.util.HashMap<>();
        for (Object[] row : rawStats) {
            int m = ((Number) row[0]).intValue();
            byMonth.put(m, row);
        }

        String[] monthNames = {"", "Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
                "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"};

        // Déterminer le mois maximum à afficher
        int currentYear = LocalDate.now().getYear();
        int maxMonth = (targetYear == currentYear)
                ? LocalDate.now().getMonthValue()
                : 12;

        List<InvoiceMonthlyStatsDto> result = new java.util.ArrayList<>();
        for (int m = 1; m <= maxMonth; m++) {
            Object[] row = byMonth.get(m);
            BigDecimal fact = row != null && row[1] != null
                    ? new BigDecimal(row[1].toString()) : BigDecimal.ZERO;
            BigDecimal cred = row != null && row[2] != null
                    ? new BigDecimal(row[2].toString()) : BigDecimal.ZERO;
            BigDecimal turn = row != null && row[3] != null
                    ? new BigDecimal(row[3].toString()) : BigDecimal.ZERO;
            BigDecimal coll = turn.subtract(cred);

            result.add(new InvoiceMonthlyStatsDto(m, targetYear, monthNames[m], fact, cred, turn, coll));
        }
        return result;
    }
}
