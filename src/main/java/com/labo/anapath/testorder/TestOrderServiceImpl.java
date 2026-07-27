package com.labo.anapath.testorder;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.DuplicateResourceException;
import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.contract.ContratRepository;
import com.labo.anapath.contract.DetailsContrat;
import com.labo.anapath.contract.DetailsContratRepository;
import com.labo.anapath.doctor.DoctorRepository;
import com.labo.anapath.doctor.HospitalRepository;
import com.labo.anapath.finance.Invoice;
import com.labo.anapath.finance.InvoiceDetail;
import com.labo.anapath.finance.InvoiceDetailRepository;
import com.labo.anapath.finance.InvoiceRepository;
import com.labo.anapath.patient.PatientRepository;
import com.labo.anapath.report.LogReport;
import com.labo.anapath.report.LogReportRepository;
import com.labo.anapath.report.Report;
import com.labo.anapath.report.ReportRepository;
import com.labo.anapath.report.ReportStatus;
import com.labo.anapath.setting.SettingApp;
import com.labo.anapath.setting.SettingAppRepository;
import com.labo.anapath.setting.SettingRepository;
import com.labo.anapath.test.LabTest;
import com.labo.anapath.test.LabTestRepository;
import com.labo.anapath.test.TypeOrderRepository;
import com.labo.anapath.user.User;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implémentation du service des bons d'examen anatomopathologiques.
 *
 * <p>Orchestre le workflow central du LIS : création des bons, validation (avec génération
 * de code unique, création du compte-rendu DRAFT, journalisation et facturation),
 * mise à jour, suppression et livraison.
 *
 * <p>La protection contre les race conditions lors de la génération du code est assurée
 * par l'interception de {@link org.springframework.dao.DataIntegrityViolationException}
 * sur la contrainte d'unicité du champ {@code code}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TestOrderServiceImpl implements TestOrderService {

    private final TestOrderRepository testOrderRepository;
    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final HospitalRepository hospitalRepository;
    private final ContratRepository contratRepository;
    private final DetailsContratRepository detailsContratRepository;
    private final TypeOrderRepository typeOrderRepository;
    private final LabTestRepository labTestRepository;
    private final TestOrderMapper testOrderMapper;
    private final ReportRepository reportRepository;
    private final LogReportRepository logReportRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceDetailRepository invoiceDetailRepository;
    private final UserRepository userRepository;
    private final SettingRepository settingRepository;
    private final SettingAppRepository settingAppRepository;
    private final FileStorageService fileStorageService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /** Clé du préfixe de code de bon d'examen, dans {@code setting_apps} (comme Laravel). */
    private static final String PREFIXE_CODE_EXAMEN_KEY = "prefixe_code_demande_examen";

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TestOrderResponseDto> findAll(int page, int size, TestOrderFilterDto filter, UUID branchId) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<TestOrder> orderPage = testOrderRepository
                .findAll(TestOrderSpecification.filter(branchId, filter), pageRequest);

        // Batch fetch reports et invoices pour éviter les N+1
        List<UUID> ids = orderPage.getContent().stream().map(TestOrder::getId).toList();
        Map<UUID, Report> reportMap = reportRepository.findByTestOrder_IdIn(ids)
                .stream().collect(Collectors.toMap(
                        r -> r.getTestOrder().getId(), r -> r, (a, b) -> a));
        Map<UUID, Invoice> invoiceMap = invoiceRepository.findByTestOrder_IdIn(ids)
                .stream().collect(Collectors.toMap(
                        i -> i.getTestOrder().getId(), i -> i, (a, b) -> a));

        List<TestOrderResponseDto> content = orderPage.getContent().stream()
                .map(order -> enrichDto(testOrderMapper.toResponseDto(order),
                        reportMap.get(order.getId()), invoiceMap.get(order.getId())))
                .toList();

        return new PageResponse<>(content, orderPage.getNumber(), orderPage.getSize(),
                orderPage.getTotalElements(), orderPage.getTotalPages(), orderPage.isLast());
    }

    // -------------------------------------------------------------------------
    // Immunohistochimie
    // -------------------------------------------------------------------------

    /**
     * Retourne la page de bons d'examen restreinte aux types immuno
     * ({@code immuno-interne}, {@code immuno-exterme}) pour la branche donnée,
     * en appliquant les critères du filtre standard.
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<TestOrderResponseDto> findAllImmuno(int page, int size, TestOrderFilterDto filter, UUID branchId) {
        List<UUID> immunoTypeIds = typeOrderRepository.findImmunoTypeIds(branchId);
        if (immunoTypeIds.isEmpty()) {
            return PageResponse.of(Page.empty());
        }

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Specification<TestOrder> spec = TestOrderSpecification.filter(branchId, filter)
                .and(TestOrderSpecification.typeOrderIdIn(immunoTypeIds));

        Page<TestOrder> orderPage = testOrderRepository.findAll(spec, pageRequest);

        // Batch fetch reports et invoices pour éviter les N+1
        List<UUID> ids = orderPage.getContent().stream().map(TestOrder::getId).toList();
        Map<UUID, Report> reportMap = reportRepository.findByTestOrder_IdIn(ids)
                .stream().collect(Collectors.toMap(
                        r -> r.getTestOrder().getId(), r -> r, (a, b) -> a));
        Map<UUID, Invoice> invoiceMap = invoiceRepository.findByTestOrder_IdIn(ids)
                .stream().collect(Collectors.toMap(
                        i -> i.getTestOrder().getId(), i -> i, (a, b) -> a));

        List<TestOrderResponseDto> content = orderPage.getContent().stream()
                .map(order -> enrichDto(testOrderMapper.toResponseDto(order),
                        reportMap.get(order.getId()), invoiceMap.get(order.getId())))
                .toList();

        return new PageResponse<>(content, orderPage.getNumber(), orderPage.getSize(),
                orderPage.getTotalElements(), orderPage.getTotalPages(), orderPage.isLast());
    }

    /**
     * Compte les bons immuno en attente de compte rendu — reprise du helper Laravel
     * {@code getnbrTestOrderImmunopending()} (compte rendu existant, en statut
     * {@code DRAFT} ou {@code PENDING_REVIEW}).
     */
    @Override
    @Transactional(readOnly = true)
    public long countImmunoPending(UUID branchId) {
        List<UUID> immunoTypeIds = typeOrderRepository.findImmunoTypeIds(branchId);
        if (immunoTypeIds.isEmpty()) return 0L;
        return testOrderRepository.countPendingReportByTypeIds(branchId, immunoTypeIds);
    }

    /**
     * Compte les bons de cytologie/histologie en attente de compte rendu — reprise du
     * helper Laravel {@code getnbrTestOrderpending()} qui alimente le badge du menu
     * « Demandes d'examen ».
     */
    @Override
    @Transactional(readOnly = true)
    public long countPending(UUID branchId) {
        List<UUID> typeIds = typeOrderRepository.findCytoHistoTypeIds(branchId);
        if (typeIds.isEmpty()) return 0L;
        return testOrderRepository.countPendingReportByTypeIds(branchId, typeIds);
    }

    @Override
    @Transactional(readOnly = true)
    public TestOrderResponseDto findById(UUID id, UUID branchId) {
        TestOrder order = testOrderRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'examen", id));
        TestOrderResponseDto dto = testOrderMapper.toResponseDto(order);
        Report report = reportRepository.findByTestOrderId(order.getId()).orElse(null);
        Invoice invoice = invoiceRepository.findByTestOrderId(order.getId()).orElse(null);
        return enrichDto(dto, report, invoice);
    }

    /**
     * Crée un nouveau bon d'examen au statut PENDING.
     *
     * <p>Calcule automatiquement le total de chaque ligne d'analyse :
     * {@code total = prix - (prix × remise / 100)}.
     *
     * @param dto      données saisies par l'utilisateur
     * @param branchId branche du technicien connecté
     * @return le bon persisté avec ses détails
     */
    @Override
    @Transactional
    public TestOrderResponseDto create(TestOrderRequestDto dto, UUID branchId) {
        TestOrder order = new TestOrder();
        order.setBranchId(branchId);
        order.setCode(null);
        order.setStatus(TestOrderStatus.PENDING);
        order.setPrelevementDate(dto.getPrelevementDate());
        order.setReferenceHopital(dto.getReferenceHopital());
        order.setIsUrgent(dto.getIsUrgent() != null ? dto.getIsUrgent() : false);
        order.setOption(dto.getOption());
        order.setTestAffiliate(dto.getTestAffiliate());
        order.setSubtotal(dto.getSubtotal());
        order.setDiscount(dto.getDiscount());
        order.setTotal(dto.getTotal());
        if (dto.getAssignedToUserId() != null) {
            order.setAssignedToUserId(dto.getAssignedToUserId());
        }

        order.setPatient(patientRepository.findByIdAndBranchId(dto.getPatientId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", dto.getPatientId())));

        if (dto.getDoctorId() != null) {
            order.setDoctor(doctorRepository.findByIdAndBranchId(dto.getDoctorId(), branchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Médecin", dto.getDoctorId())));
        }
        if (dto.getHospitalId() != null) {
            order.setHospital(hospitalRepository.findByIdAndBranchId(dto.getHospitalId(), branchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Hôpital", dto.getHospitalId())));
        }
        if (dto.getContratId() != null) {
            order.setContrat(contratRepository.findByIdAndBranchId(dto.getContratId(), branchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Contrat", dto.getContratId())));
        }
        if (dto.getTypeOrderId() != null) {
            order.setTypeOrder(typeOrderRepository.findByIdAndBranchId(dto.getTypeOrderId(), branchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Type de bon", dto.getTypeOrderId())));
        }

        if (dto.getDetails() != null && !dto.getDetails().isEmpty()) {
            List<DetailTestOrder> details = dto.getDetails().stream().map(detailDto -> {
                LabTest labTest = labTestRepository.findByIdAndBranchId(detailDto.getLabTestId(), branchId)
                        .orElseThrow(() -> new ResourceNotFoundException("Analyse", detailDto.getLabTestId()));
                DetailTestOrder detail = new DetailTestOrder();
                detail.setTestOrder(order);
                detail.setLabTest(labTest);
                detail.setTestName(labTest.getName() != null ? labTest.getName() : "");
                double price = labTest.getPrice() != null ? labTest.getPrice().doubleValue() : 0.0;
                double disc = detailDto.getDiscount() != null ? detailDto.getDiscount() : 0.0;
                detail.setPrice(price);
                detail.setDiscount(disc);
                // La remise (disc) est un MONTANT (amountRemise du contrat), pas un
                // pourcentage : total = prix - remise (borné à 0), cohérent avec le
                // calcul du frontend. L'ancienne formule prix - prix*disc/100 donnait
                // des totaux aberrants (ex. remise 5000 sur 25000 → -1 225 000).
                detail.setTotal(Math.max(0.0, price - disc));
                return detail;
            }).toList();
            order.getDetails().addAll(details);
        }

        TestOrder saved = testOrderRepository.save(order);
        log.info("Bon d'examen créé: id={}, patient={}", saved.getId(), saved.getPatient().getId());
        return testOrderMapper.toResponseDto(saved);
    }

    /**
     * Met à jour un bon d'examen existant. Interdit sur un bon déjà validé.
     *
     * @param id       identifiant UUID du bon
     * @param dto      nouvelles données
     * @param branchId identifiant de la branche (isolation multi-tenant)
     * @return le bon mis à jour
     */
    @Override
    @Transactional
    public TestOrderResponseDto update(UUID id, TestOrderRequestDto dto, UUID branchId) {
        TestOrder order = testOrderRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'examen", id));
        if (order.getStatus() == TestOrderStatus.VALIDATED) {
            throw new InvalidOperationException("Impossible de modifier un bon d'examen déjà validé.");
        }
        order.setPrelevementDate(dto.getPrelevementDate());
        order.setReferenceHopital(dto.getReferenceHopital());
        if (dto.getIsUrgent() != null) order.setIsUrgent(dto.getIsUrgent());
        if (dto.getOption() != null) order.setOption(dto.getOption());
        if (dto.getTestAffiliate() != null) order.setTestAffiliate(dto.getTestAffiliate());
        // « Affecter à » (Laravel : attribuate_doctor_id) = docteur signataire.
        // On aligne attribuateDoctorId ET assignedToUserId sur le même utilisateur
        // pour que « Mon espace » et les stats d'assignation restent cohérents.
        if (dto.getAssignedToUserId() != null) {
            order.setAssignedToUserId(dto.getAssignedToUserId());
            order.setAttribuateDoctorId(dto.getAssignedToUserId());
        }

        if (dto.getDoctorId() != null) {
            order.setDoctor(doctorRepository.findByIdAndBranchId(dto.getDoctorId(), branchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Médecin", dto.getDoctorId())));
        }
        if (dto.getHospitalId() != null) {
            order.setHospital(hospitalRepository.findByIdAndBranchId(dto.getHospitalId(), branchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Hôpital", dto.getHospitalId())));
        }
        if (dto.getContratId() != null) {
            order.setContrat(contratRepository.findByIdAndBranchId(dto.getContratId(), branchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Contrat", dto.getContratId())));
        }
        if (dto.getTypeOrderId() != null) {
            order.setTypeOrder(typeOrderRepository.findByIdAndBranchId(dto.getTypeOrderId(), branchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Type de bon", dto.getTypeOrderId())));
        }

        // Remplacement des analyses (details) : le front envoie la liste complète
        // voulue à chaque ajout/modif/suppression. orphanRemoval supprime les
        // anciennes lignes, le cascade persiste les nouvelles.
        if (dto.getDetails() != null) {
            order.getDetails().clear();
            List<DetailTestOrder> details = dto.getDetails().stream().map(detailDto -> {
                LabTest labTest = labTestRepository.findByIdAndBranchId(detailDto.getLabTestId(), branchId)
                        .orElseThrow(() -> new ResourceNotFoundException("Analyse", detailDto.getLabTestId()));
                DetailTestOrder detail = new DetailTestOrder();
                detail.setTestOrder(order);
                detail.setLabTest(labTest);
                detail.setTestName(labTest.getName() != null ? labTest.getName() : "");
                double price = labTest.getPrice() != null ? labTest.getPrice().doubleValue() : 0.0;
                double disc = detailDto.getDiscount() != null ? detailDto.getDiscount() : 0.0;
                detail.setPrice(price);
                detail.setDiscount(disc);
                // La remise (disc) est un MONTANT (amountRemise du contrat), pas un
                // pourcentage : total = prix - remise (borné à 0), cohérent avec le
                // calcul du frontend. L'ancienne formule prix - prix*disc/100 donnait
                // des totaux aberrants (ex. remise 5000 sur 25000 → -1 225 000).
                detail.setTotal(Math.max(0.0, price - disc));
                return detail;
            }).toList();
            order.getDetails().addAll(details);
            double subtotal = details.stream().mapToDouble(DetailTestOrder::getPrice).sum();
            double total = details.stream().mapToDouble(DetailTestOrder::getTotal).sum();
            order.setSubtotal(subtotal);
            order.setTotal(total);
            order.setDiscount(subtotal - total);
        }

        return testOrderMapper.toResponseDto(testOrderRepository.save(order));
    }

    /**
     * Supprime (soft delete) un bon d'examen non encore validé.
     * Un bon validé ne peut pas être supprimé pour préserver la traçabilité.
     *
     * @param id       identifiant UUID du bon
     * @param branchId identifiant de la branche (isolation multi-tenant)
     */
    @Override
    @Transactional
    public void delete(UUID id, UUID branchId) {
        TestOrder order = testOrderRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'examen", id));
        if (order.getStatus() == TestOrderStatus.VALIDATED) {
            throw new InvalidOperationException("Impossible de supprimer un bon d'examen déjà validé.");
        }
        testOrderRepository.delete(order);
        log.info("Bon d'examen supprimé (soft): {}", id);
    }

    /**
     * Déclenche le workflow de validation du bon d'examen (passage au statut VALIDATED).
     *
     * <p>Séquence complète :
     * <ol>
     *   <li>Seul le statut {@code VALIDATED} est accepté par cet endpoint</li>
     *   <li>Récupération du bon filtrée par branchId (sécurité multi-tenant)</li>
     *   <li>Vérification de la présence du contrat (obligatoire)</li>
     *   <li>Génération du code unique si le bon n'en possède pas encore ;
     *       une {@link org.springframework.dao.DataIntegrityViolationException} est traduite
     *       en HTTP 409 pour protéger contre les race conditions concurrentes</li>
     *   <li>Création ou mise à jour du {@link Report} associé (code = "CO" + code du bon)</li>
     *   <li>Journalisation de l'action dans {@link LogReport}</li>
     *   <li>Facturation : individuelle si {@code invoice_unique=false}, groupée sinon</li>
     * </ol>
     *
     * @param id       identifiant UUID du bon
     * @param status   valeur attendue : {@code "VALIDATED"}
     * @param userId   identifiant de l'utilisateur effectuant la validation (pour le log)
     * @param branchId identifiant de la branche (isolation multi-tenant)
     * @return le bon validé avec son code généré
     */
    @Override
    @Transactional
    public TestOrderResponseDto updateStatus(UUID id, String status, UUID userId, UUID branchId) {
        // AC3: seul VALIDATED est supporté via cet endpoint
        if (!"VALIDATED".equalsIgnoreCase(status)) {
            throw new BusinessException("Seul le statut VALIDATED est géré par cet endpoint");
        }

        // AC4: bon doit exister, filtré par branchId (sécurité multi-tenant)
        TestOrder order = testOrderRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'examen", id));

        // AC10: contrat obligatoire
        if (order.getContrat() == null) {
            throw new BusinessException("TEST_ORDER_NO_CONTRACT");
        }

        // AC1/AC2: génération code uniquement si pas encore validé
        if (order.getCode() == null) {
            String code = generateCodeExamen(branchId);
            order.setCode(code);
            order.setStatus(TestOrderStatus.VALIDATED);
            try {
                testOrderRepository.saveAndFlush(order);
            } catch (DataIntegrityViolationException e) {
                // AC9: race condition — collision sur le code unique → HTTP 409
                log.warn("Race condition lors de la validation du bon {}: code={}", id, code);
                throw new DuplicateResourceException("CODE_GENERATION_CONFLICT");
            }
        }

        // AC5: Report — création ou mise à jour du code
        Report report = reportRepository.findByTestOrderId(order.getId()).orElse(null);
        if (report == null) {
            report = new Report();
            report.setBranchId(branchId);
            report.setTestOrder(order);
            report.setStatus(ReportStatus.DRAFT);
            String placeholder = settingRepository
                    .findByKeyAndBranchId("prefixe_code_demande_examen", branchId)
                    .map(s -> s.getPlaceholder() != null ? s.getPlaceholder() : "")
                    .orElse("");
            report.setDescription(placeholder);
        }
        report.setCode("CO" + order.getCode());
        report = reportRepository.save(report);

        // AC6: LogReport
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", userId));
        LogReport logReport = new LogReport();
        logReport.setBranchId(branchId);
        logReport.setReport(report);
        logReport.setUser(user);
        logReport.setAction("Créer un nouveau report");
        logReportRepository.save(logReport);
        log.info("Report créé/mis à jour pour bon {}: code={}", id, report.getCode());

        // AC7/AC8: Facturation
        boolean invoiceGrouped = Boolean.TRUE.equals(order.getContrat().getInvoiceUnique());
        if (!invoiceGrouped) {
            processIndividualInvoice(order, branchId);
        } else {
            processGroupedInvoice(order);
        }

        return testOrderMapper.toResponseDto(
                testOrderRepository.findByIdAndBranchId(id, branchId).orElseThrow());
    }

    /**
     * Enrichit un {@link TestOrderResponseDto} avec les informations du rapport et de la facture associés.
     *
     * @param dto     DTO de base issu du mapper
     * @param report  rapport associé au bon (null si pas encore créé)
     * @param invoice facture associée au bon (null si pas encore créée)
     * @return un nouveau DTO avec les champs reportId, reportStatus, reportIsDelivered, invoiceId renseignés
     */
    private TestOrderResponseDto enrichDto(TestOrderResponseDto dto, Report report, Invoice invoice) {
        return new TestOrderResponseDto(
                dto.id(), dto.code(), dto.status(), dto.prelevementDate(),
                dto.referenceHopital(), dto.isUrgent(), dto.subtotal(), dto.discount(), dto.total(),
                dto.patientId(), dto.patientFirstname(), dto.patientLastname(),
                dto.doctorId(), dto.doctorName(), dto.hospitalId(), dto.hospitalName(),
                dto.contratId(), dto.contratName(), dto.typeOrderId(), dto.typeOrderTitle(),
                dto.attribuateDoctorId(), dto.assignedToUserId(),
                dto.details(), dto.branchId(), dto.createdAt(),
                // champs enrichis :
                report != null ? report.getId() : null,
                report != null ? report.getStatus().name() : null,
                report != null && report.isDelivered(),
                invoice != null ? invoice.getId() : null,
                dto.archive(),
                dto.testAffiliate(),
                dto.option()
        );
    }

    /**
     * Traite la facturation individuelle pour un contrat avec {@code invoice_unique=false}.
     *
     * <p>Crée une nouvelle facture dédiée à ce bon d'examen si elle n'existe pas encore,
     * sinon met à jour les montants de la facture existante.
     * Dans tous les cas, ajoute les lignes de détail non encore facturées.
     *
     * @param order    le bon d'examen à facturer
     * @param branchId identifiant de la branche (nécessaire pour la création de la facture)
     */
    // AC7: contrat individuel (invoice_unique=false)
    private void processIndividualInvoice(TestOrder order, UUID branchId) {
        Invoice invoice = invoiceRepository.findByTestOrderId(order.getId()).orElse(null);
        if (invoice == null) {
            invoice = new Invoice();
            invoice.setBranchId(branchId);
            invoice.setDate(LocalDate.now()); // calque Laravel : 'date' => Carbon::now()
            invoice.setTestOrder(order);
            invoice.setPatient(order.getPatient());
            invoice.setContrat(order.getContrat());
            invoice.setClientName(
                    order.getPatient().getFirstname() + " " + order.getPatient().getLastname());
            invoice.setSubtotal(order.getSubtotal());
            invoice.setDiscount(order.getDiscount());
            invoice.setTotal(order.getTotal() != null
                    ? BigDecimal.valueOf(order.getTotal()) : BigDecimal.ZERO);
            invoice.setCode(generateCodeFacture(branchId));
            invoice = invoiceRepository.save(invoice);
        } else {
            invoice.setSubtotal(order.getSubtotal());
            invoice.setDiscount(order.getDiscount());
            invoice.setTotal(order.getTotal() != null
                    ? BigDecimal.valueOf(order.getTotal()) : BigDecimal.ZERO);
            invoiceRepository.save(invoice);
        }
        addInvoiceDetails(invoice, order.getDetails());
    }

    /**
     * Traite la facturation groupée pour un contrat avec {@code invoice_unique=true}.
     *
     * <p>Récupère la facture ouverte la plus récente du contrat et cumule les montants du bon.
     * Lève une {@link com.labo.anapath.common.exception.BusinessException} si aucune facture
     * ouverte n'existe ({@code CONTRACT_NO_INVOICE}) ou si elle est déjà payée
     * ({@code CONTRACT_INVOICE_ALREADY_PAID}).
     *
     * @param order le bon d'examen dont les montants sont à cumuler sur la facture groupée
     */
    // AC8: contrat groupé (invoice_unique=true) — équivalent de ->where('contrat_id', id)->first() Laravel
    private void processGroupedInvoice(TestOrder order) {
        Invoice invoice = invoiceRepository
                .findFirstByContratIdOrderByCreatedAtDesc(order.getContrat().getId())
                .orElse(null);
        if (invoice == null) {
            throw new BusinessException("CONTRACT_NO_INVOICE");
        }
        if (Boolean.TRUE.equals(invoice.getPaid())) {
            throw new BusinessException("CONTRACT_INVOICE_ALREADY_PAID");
        }
        double newSubtotal = (invoice.getSubtotal() != null ? invoice.getSubtotal() : 0.0)
                + (order.getSubtotal() != null ? order.getSubtotal() : 0.0);
        double newDiscount = (invoice.getDiscount() != null ? invoice.getDiscount() : 0.0)
                + (order.getDiscount() != null ? order.getDiscount() : 0.0);
        BigDecimal currentTotal = invoice.getTotal() != null ? invoice.getTotal() : BigDecimal.ZERO;
        BigDecimal orderTotal = order.getTotal() != null
                ? BigDecimal.valueOf(order.getTotal()) : BigDecimal.ZERO;
        invoice.setSubtotal(newSubtotal);
        invoice.setDiscount(newDiscount);
        invoice.setTotal(currentTotal.add(orderTotal));
        invoiceRepository.save(invoice);
        addInvoiceDetails(invoice, order.getDetails());
    }

    /**
     * Ajoute les lignes de détail non encore facturées à une facture.
     *
     * <p>Un détail est considéré "non facturé" si son champ {@code status} vaut {@code null}
     * ou {@code true}. Une fois traité, il est marqué {@code status=false} pour éviter
     * une double facturation lors d'un rechargement de la transaction.
     *
     * @param invoice la facture cible
     * @param details la liste des détails du bon d'examen
     */
    // Crée les InvoiceDetail pour les détails non encore facturés (status != false)
    private void addInvoiceDetails(Invoice invoice, List<DetailTestOrder> details) {
        for (DetailTestOrder detail : details) {
            if (!Boolean.FALSE.equals(detail.getStatus())) { // null ou true = non facturé
                InvoiceDetail invoiceDetail = new InvoiceDetail();
                invoiceDetail.setInvoice(invoice);
                invoiceDetail.setLabTest(detail.getLabTest());
                invoiceDetail.setTestName(detail.getTestName());
                invoiceDetail.setPrice(detail.getPrice());
                invoiceDetail.setDiscount(detail.getDiscount());
                BigDecimal total = detail.getTotal() != null
                        ? BigDecimal.valueOf(detail.getTotal()) : BigDecimal.ZERO;
                invoiceDetail.setTotal(total);
                invoiceDetail.setUnitPrice(detail.getPrice() != null
                        ? BigDecimal.valueOf(detail.getPrice()) : BigDecimal.ZERO);
                invoiceDetailRepository.save(invoiceDetail);

                detail.setStatus(false); // marquer comme facturé
                // sauvegarde via cascade TestOrder → DetailTestOrder
            }
        }
    }

    /**
     * Génère le prochain code unique de bon d'examen pour la branche et l'année en cours.
     *
     * <p>Format Laravel de référence : {@code {préfixe}{aa}-{séq4}} — soit {@code 26-0001}
     * avec le préfixe par défaut, qui est <b>vide</b> (cf. {@code SettingAppMissingKeysSeeder}
     * et les codes historiques « 25-3091 »). Le préfixe est le réglage
     * {@code prefixe_code_demande_examen} de la table {@code setting_apps} — la même que
     * lit le helper {@code generateCodeExamen()} de Laravel.
     * La séquence repart à {@code 0001} chaque début d'année civile.
     *
     * @param branchId identifiant de la branche pour la numérotation isolée
     * @return le code généré, non encore persisté (peut échouer sur contrainte d'unicité)
     */
    // Algorithme generateCodeExamen — équivalent Java de la fonction PHP Laravel
    // Format : {prefix}{yy2digit}-{seq4}  ex: 26-0001
    //
    // Robustesse (cas des données migrées Laravel) :
    //  - la séquence max est calculée NUMÉRIQUEMENT (pas en tri texte) et filtrée
    //    sur l'année portée par le CODE lui-même (pas created_at) ;
    //  - une pré-vérification d'existence incrémente la séquence tant que le code
    //    candidat existe déjà (trous, préfixes/longueurs hétérogènes, doublons hérités),
    //    ce qui évite de générer un code déjà pris.
    private String generateCodeExamen(UUID branchId) {
        int year = LocalDate.now().getYear();
        String yearToken = String.valueOf(year % 100);

        // Préfixe historique Laravel, lu dans `setting_apps` comme le helper PHP
        // (SettingApp::where('key','prefixe_code_demande_examen')). Vide par défaut
        // → « 26-0003 », et non « EX26-0003 » ni « ABCD26-0003 ».
        String prefix = settingAppRepository
                .findByKeyAndBranchId(PREFIXE_CODE_EXAMEN_KEY, branchId)
                .map(SettingApp::getValue)
                .map(String::trim)
                .orElse("");

        int next = testOrderRepository.findMaxSequenceForYear(branchId, yearToken) + 1;
        String code = prefix + yearToken + "-" + String.format("%04d", next);

        // Pré-vérification : saute les codes déjà existants (y compris supprimés)
        // pour ne jamais proposer un code en collision. Borne de sécurité pour
        // éviter toute boucle infinie sur des données anormales.
        int guard = 0;
        while (testOrderRepository.existsByCodeIncludingDeleted(code) && guard < 10_000) {
            next++;
            guard++;
            code = prefix + yearToken + "-" + String.format("%04d", next);
        }

        return code;
    }

    /**
     * Génère le prochain code unique de facture individuelle pour la branche et l'année en cours.
     *
     * <p>Format fixe : {@code FA{aa}{séq4}} — ex. {@code FA260001}.
     * La séquence repart à {@code 0001} chaque début d'année civile.
     *
     * @param branchId identifiant de la branche pour la numérotation isolée
     * @return le code facture généré
     */
    // Algorithme generateCodeFacture — équivalent Java de la fonction PHP Laravel
    // Format : FA{yy2digit}{seq4}  ex: FA260001
    private String generateCodeFacture(UUID branchId) {
        int year = LocalDate.now().getYear();
        List<Invoice> invoices = invoiceRepository.findByBranchIdAndCodeNotNullAndYear(
                branchId, year, PageRequest.of(0, 1));

        String seq;
        if (invoices.isEmpty()) {
            seq = "0001";
        } else {
            String lastCode = invoices.get(0).getCode();
            String last4 = lastCode.length() >= 4
                    ? lastCode.substring(lastCode.length() - 4) : lastCode;
            try {
                int next = Integer.parseInt(last4) + 1;
                seq = String.format("%04d", next);
            } catch (NumberFormatException e) {
                seq = "0001";
            }
        }

        return "FA" + (year % 100) + seq;
    }

    /**
     * Marque un bon d'examen comme livré (passage au statut DELIVERED).
     *
     * <p>Seuls les bons au statut {@code VALIDATED} peuvent être livrés.
     * Positionne également {@code report.isDelivered = true} sur le compte-rendu associé
     * pour tracer la remise physique du résultat.
     *
     * @param id identifiant UUID du bon à livrer
     * @return le bon mis à jour
     */
    @Override
    @Transactional
    public TestOrderResponseDto markAsDelivered(UUID id, UUID branchId) {
        TestOrder order = testOrderRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'examen", id));
        if (order.getStatus() != TestOrderStatus.VALIDATED) {
            throw new InvalidOperationException("Seul un bon VALIDATED peut être livré.");
        }
        order.setStatus(TestOrderStatus.DELIVERED);
        testOrderRepository.save(order);

        Report report = reportRepository.findByTestOrderId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compte-rendu du bon", id));
        report.setDelivered(true);
        // Le statut du rapport doit aussi passer à DELIVERED : la liste des demandes
        // affiche reportStatus (report.getStatus()), pas seulement le drapeau delivered.
        report.setStatus(ReportStatus.DELIVERED);
        reportRepository.save(report);

        log.info("Bon d'examen livré: id={}", id);
        return testOrderMapper.toResponseDto(
                testOrderRepository.findByIdAndBranchId(id, branchId).orElseThrow());
    }

    /**
     * Assigne un médecin pathologiste à un bon d'examen.
     *
     * <p>Positionne {@code attribuateDoctorId}, {@code assignedToUserId} (même valeur)
     * et {@code assignmentDate = now()}. Alignement avec le flux Laravel complet
     * (TestOrderController.php lignes 1510–1512).
     *
     * @param id       identifiant UUID du bon
     * @param doctorId identifiant UUID du médecin à assigner
     * @param branchId identifiant de la branche (isolation multi-tenant)
     * @return le bon mis à jour
     */
    @Override
    @Transactional
    public TestOrderResponseDto assignDoctor(UUID id, UUID doctorId, UUID branchId) {
        TestOrder order = testOrderRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'examen", id));
        order.setAttribuateDoctorId(doctorId);
        order.setAssignedToUserId(doctorId);
        order.setAssignmentDate(LocalDateTime.now());
        log.info("Médecin assigné au bon {}: doctorId={}", id, doctorId);
        return testOrderMapper.toResponseDto(testOrderRepository.save(order));
    }

    @Override
    @Transactional
    public java.util.List<String> uploadImages(UUID id, UUID branchId, java.util.List<org.springframework.web.multipart.MultipartFile> files) {
        TestOrder order = testOrderRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'examen", id));
        java.util.List<String> existing = parseFilesName(order.getFilesName());
        for (org.springframework.web.multipart.MultipartFile file : files) {
            try {
                existing.add(fileStorageService.store(file));
            } catch (java.io.IOException e) {
                throw new com.labo.anapath.common.exception.BusinessException("Erreur lors du stockage du fichier: " + file.getOriginalFilename());
            }
        }
        try {
            order.setFilesName(objectMapper.writeValueAsString(existing));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new com.labo.anapath.common.exception.BusinessException("Erreur de sérialisation JSON");
        }
        testOrderRepository.save(order);
        return existing;
    }

    @Override
    @Transactional
    public String uploadArchive(UUID id, UUID branchId, org.springframework.web.multipart.MultipartFile file) {
        TestOrder order = testOrderRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'examen", id));
        try {
            order.setArchive(fileStorageService.store(file));
        } catch (java.io.IOException e) {
            throw new com.labo.anapath.common.exception.BusinessException(
                    "Erreur lors du stockage de la pièce jointe: " + file.getOriginalFilename());
        }
        testOrderRepository.save(order);
        return order.getArchive();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<ImageDto> getImages(UUID id, UUID branchId) {
        TestOrder order = testOrderRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'examen", id));
        java.util.List<String> filenames = parseFilesName(order.getFilesName());
        java.util.List<ImageDto> result = new java.util.ArrayList<>();
        for (int i = 0; i < filenames.size(); i++) {
            result.add(new ImageDto(i, filenames.get(i), fileStorageService.getUrl(filenames.get(i))));
        }
        return result;
    }

    @Override
    @Transactional
    public void deleteImage(UUID id, int index, UUID branchId) {
        TestOrder order = testOrderRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'examen", id));
        java.util.List<String> filenames = parseFilesName(order.getFilesName());
        if (index < 0 || index >= filenames.size()) {
            throw new InvalidOperationException("Index d'image invalide: " + index);
        }
        try {
            fileStorageService.delete(filenames.get(index));
        } catch (java.io.IOException e) {
            log.warn("Impossible de supprimer le fichier physique à l'index {}: {}", index, e.getMessage());
        }
        filenames.remove(index);
        try {
            order.setFilesName(objectMapper.writeValueAsString(filenames));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new com.labo.anapath.common.exception.BusinessException("Erreur de sérialisation JSON");
        }
        testOrderRepository.save(order);
    }

    @SuppressWarnings("unchecked")
    private java.util.List<String> parseFilesName(String json) {
        if (json == null || json.isBlank()) return new java.util.ArrayList<>();
        try {
            return objectMapper.readValue(json, java.util.List.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return new java.util.ArrayList<>();
        }
    }

    // -------------------------------------------------------------------------
    // Myspace
    // -------------------------------------------------------------------------

    /**
     * Calcule les statistiques des bons d'examen assignés à l'utilisateur connecté.
     *
     * <p>Le seuil de retard est fixé à 48 heures à partir de la date d'assignation.
     *
     * @param userId   identifiant de l'utilisateur
     * @param branchId identifiant de la branche (isolation multi-tenant)
     * @return DTO de statistiques
     */
    @Override
    @Transactional(readOnly = true)
    public MyspaceStatsDto getMyspaceStats(UUID userId, UUID branchId) {
        // Un super-admin voit TOUTE la donnée de la branche active (et non ses
        // seules assignations) : « Mon espace » lui sert de vue d'ensemble du labo.
        boolean seeAll = userRepository.isSuperAdmin(userId);
        // En attente / terminé sont déterminés par le statut du RAPPORT (comme Laravel),
        // car tous les bons assignés sont déjà au statut VALIDATED côté bon d'examen.
        long totalAssigned  = testOrderRepository.countByAssignedToUserIdAndBranchId(userId, branchId, seeAll);
        long totalPending   = testOrderRepository.countAssignedReportPending(userId, branchId, seeAll);
        long totalValidated = testOrderRepository.countAssignedReportDone(userId, branchId, seeAll);
        long totalUrgent    = testOrderRepository.countUrgentByAssignedToUserIdAndBranchId(userId, branchId, seeAll);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(21);
        long totalLate      = testOrderRepository.countLateByAssignedToUserIdAndBranchId(userId, branchId, cutoff, seeAll);

        List<UUID> immunoTypeIds = typeOrderRepository.findImmunoTypeIds(branchId);
        long totalImmunoPending = immunoTypeIds.isEmpty() ? 0L
                : testOrderRepository.countImmunoPendingByAssignedToUserId(
                        userId, branchId, immunoTypeIds, seeAll);

        return new MyspaceStatsDto(totalAssigned, totalPending, totalValidated,
                totalImmunoPending, totalUrgent, totalLate);
    }

    /**
     * Retourne la page de bons d'examen assignés à l'utilisateur, avec filtres optionnels.
     *
     * @param userId   identifiant de l'utilisateur
     * @param branchId identifiant de la branche (isolation multi-tenant)
     * @param page     numéro de page (0-based)
     * @param size     taille de la page
     * @param status   filtre optionnel sur le statut
     * @param search   recherche textuelle optionnelle (code ou nom patient)
     * @return page de {@link TestOrderResponseDto}
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<TestOrderResponseDto> getMyspaceOrders(UUID userId, UUID branchId, int page, int size,
                                                               TestOrderStatus status, UUID typeOrderId,
                                                               String priority, String from, String to, String search) {
        // La requête native findMyspaceOrders trie déjà par t.created_at DESC.
        // Ne PAS ajouter de Sort ici : Spring l'appliquerait tel quel sur la requête
        // native (ORDER BY t.createdAt) → "column t.createdat does not exist" (500).
        PageRequest pageRequest = PageRequest.of(page, size);
        String searchParam = (search != null && !search.isBlank()) ? search : null;
        String statusParam = (status != null) ? status.name() : null;
        String typeOrderParam = (typeOrderId != null) ? typeOrderId.toString() : null;
        String priorityParam = (priority != null && !priority.isBlank()) ? priority : null;
        String fromParam = (from != null && !from.isBlank()) ? from : null;
        String toParam = (to != null && !to.isBlank()) ? to : null;
        // Un super-admin voit tous les bons de la branche, pas seulement les siens.
        boolean seeAll = userRepository.isSuperAdmin(userId);
        Page<TestOrder> orderPage = testOrderRepository
                .findMyspaceOrders(userId, branchId, statusParam, typeOrderParam, priorityParam,
                        fromParam, toParam, searchParam, seeAll, pageRequest);

        // Enrichissement report + facture (batch, anti N+1) pour que la colonne
        // « Compte rendu » reflète le vrai statut (Valider / En attente / Non enregistré)
        // au lieu de rester « Non renseigné » partout.
        List<UUID> ids = orderPage.getContent().stream().map(TestOrder::getId).toList();
        Map<UUID, Report> reportMap = reportRepository.findByTestOrder_IdIn(ids)
                .stream().collect(Collectors.toMap(
                        r -> r.getTestOrder().getId(), r -> r, (a, b) -> a));
        Map<UUID, Invoice> invoiceMap = invoiceRepository.findByTestOrder_IdIn(ids)
                .stream().collect(Collectors.toMap(
                        i -> i.getTestOrder().getId(), i -> i, (a, b) -> a));

        Page<TestOrderResponseDto> result = orderPage.map(order -> enrichDto(
                testOrderMapper.toResponseDto(order),
                reportMap.get(order.getId()), invoiceMap.get(order.getId())));
        return PageResponse.of(result);
    }

    // -------------------------------------------------------------------------
    // Tarification contractuelle
    // -------------------------------------------------------------------------

    /**
     * Retourne la tarification d'une analyse pour un contrat donné.
     *
     * <p>Si l'analyse figure dans le contrat, retourne le prix négocié et la remise.
     * Sinon, retourne le prix catalogue avec une remise de zéro.
     *
     * @param contratId identifiant du contrat
     * @param labTestId identifiant de l'analyse
     * @param branchId  identifiant de la branche (pour récupérer l'analyse du catalogue)
     * @return DTO de tarification
     */
    @Override
    @Transactional(readOnly = true)
    public DiscountDto getDiscount(UUID contratId, UUID labTestId, UUID branchId) {
        LabTest labTest = labTestRepository.findByIdAndBranchId(labTestId, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Analyse", labTestId));
        BigDecimal basePrice = labTest.getPrice() != null ? labTest.getPrice() : BigDecimal.ZERO;

        // Résolution de la règle tarifaire comme dans Laravel : d'abord une règle
        // propre à l'analyse (lab_test_id), sinon une règle par catégorie
        // (les contrats migrés définissent surtout des remises par catégorie).
        DetailsContrat details = detailsContratRepository
                .findByContratIdAndLabTestId(contratId, labTestId)
                .orElse(null);
        if (details == null && labTest.getCategoryTest() != null) {
            details = detailsContratRepository
                    .findByContratIdAndCategoryTestId(contratId, labTest.getCategoryTest().getId())
                    .orElse(null);
        }

        if (details == null) {
            return new DiscountDto(basePrice, null, BigDecimal.ZERO, basePrice);
        }

        // La remise est un MONTANT : amount_remise s'il est défini, sinon calculé
        // depuis le pourcentage (règles par catégorie migrées : pourcentage seul).
        BigDecimal discount;
        if (details.getAmountRemise() != null && details.getAmountRemise().signum() > 0) {
            discount = details.getAmountRemise();
        } else if (details.getPourcentage() != null && details.getPourcentage().signum() > 0) {
            discount = basePrice.multiply(details.getPourcentage())
                    .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        } else {
            discount = BigDecimal.ZERO;
        }
        BigDecimal priceAfterDiscount = basePrice.subtract(discount).max(BigDecimal.ZERO);
        BigDecimal contractPrice = details.getPrice();
        return new DiscountDto(basePrice, contractPrice, discount, priceAfterDiscount);
    }
}
