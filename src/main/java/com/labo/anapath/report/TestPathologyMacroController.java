package com.labo.anapath.report;

import com.labo.anapath.common.NomComplet;

import com.labo.anapath.common.dto.ApiResponse;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.common.security.UserPrincipal;
import com.labo.anapath.hr.Employee;
import com.labo.anapath.hr.EmployeeRepository;
import com.labo.anapath.testorder.TestOrder;
import com.labo.anapath.testorder.TestOrderRepository;
import com.labo.anapath.testorder.TestOrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.validation.annotation.Validated;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Contrôleur REST pour la gestion des macros de texte anatomopathologiques
 * ({@code /api/v1/pathology-macros}).
 *
 * <p>Les macros sont des templates de texte prédéfinis que les pathologistes peuvent
 * insérer dans leurs comptes-rendus pour accélérer la rédaction. CRUD complet exposé.
 *
 * <p>Depuis la V47, ce contrôleur expose également les endpoints de gestion du workflow
 * macroscopie : liste paginée, demandes en attente, assignation laborantin, et mise à
 * jour des étapes histologiques.
 */
@RestController
@RequestMapping("/api/v1/pathology-macros")
@RequiredArgsConstructor
@Validated
public class TestPathologyMacroController {

    private final TestPathologyMacroRepository testPathologyMacroRepository;
    private final EmployeeRepository employeeRepository;
    private final TestOrderRepository testOrderRepository;

    // -------------------------------------------------------------------------
    // DTOs internes — macros texte (usage existant)
    // -------------------------------------------------------------------------

    /** DTO de réponse pour une macro anatomopathologique (template texte). */
    record TestPathologyMacroTextDto(UUID id, String title, String content, UUID branchId, LocalDateTime createdAt) {}

    /** DTO de requête interne pour la création ou mise à jour d'une macro. */
    @Getter
    @Setter
    static class TestPathologyMacroRequestDto {
        @NotBlank(message = "Le titre de la macro est obligatoire")
        private String title;

        private String content;
    }

    /** DTO de requête pour la création en masse de macros. */
    @Getter
    @Setter
    static class BulkMacroRequestDto {
        @NotEmpty(message = "La liste de macros ne peut pas être vide")
        private List<@Valid TestPathologyMacroRequestDto> macros;
    }

    // -------------------------------------------------------------------------
    // DTOs internes — workflow macroscopie (nouveaux endpoints)
    // -------------------------------------------------------------------------

    /**
     * DTO de réponse complet pour un enregistrement macroscopie (page liste).
     * Inclut les informations du bon d'examen et du laborantin assigné.
     */
    record TestPathologyMacroResponseDto(
            UUID id,
            UUID testOrderId,
            String testOrderCode,
            UUID employeeId,
            String employeeName,
            Boolean circulation,
            Boolean embedding,
            Boolean microtomySpreading,
            Boolean staining,
            Boolean mounting,
            LocalDate macroDate,
            LocalDateTime createdAt
    ) {}

    /**
     * DTO représentant un bon d'examen validé sans macroscopie assignée.
     * Utilisé pour afficher les demandes urgentes ou en attente.
     */
    record PendingMacroDto(
            UUID id,
            String code,
            String patientName,
            Boolean isUrgent,
            LocalDateTime createdAt,
            String typeOrderTitle,
            /**
             * Slug du type de bon. C'est sur lui que Laravel répartit les onglets
             * (Histologie-Biopsie / Pièce opératoire / Cytologie) via
             * {@code whereIn('slug', …)} ; le titre seul ne suffit pas, l'onglet
             * « Histologie-Biopsie » couvrant deux slugs distincts.
             */
            String typeOrderSlug
    ) {}

    /** DTO de requête pour l'assignation d'un laborantin à un bon d'examen. */
    record AssignMacroRequest(UUID testOrderId, UUID employeeId, LocalDate macroDate) {}

    /**
     * DTO de requête pour la mise à jour d'une étape histologique.
     * La valeur {@code step} suit la logique cumulative : chaque étape active
     * toutes les étapes précédentes.
     */
    record StepUpdateRequest(String step) {}

    // -------------------------------------------------------------------------
    // Endpoints existants — macros texte
    // -------------------------------------------------------------------------

    /**
     * Retourne la liste paginée des macros (templates texte) de la branche de l'utilisateur connecté.
     *
     * @param page      numéro de page (0-based, défaut 0)
     * @param size      taille de la page (défaut 20)
     * @param principal principal Spring Security contenant le branchId
     * @return page de {@link TestPathologyMacroTextDto}
     */
    @GetMapping
    @PreAuthorize("hasAuthority('view-reports')")
    public ResponseEntity<ApiResponse<PageResponse<TestPathologyMacroTextDto>>> findAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(
                testPathologyMacroRepository.findByBranchId(principal.getBranchId(),
                        PageRequest.of(page, size)).map(m -> new TestPathologyMacroTextDto(
                        m.getId(), m.getTitle(), m.getContent(), m.getBranchId(), m.getCreatedAt())))));
    }

    /**
     * Retourne une macro par son identifiant.
     *
     * @param id identifiant UUID de la macro
     * @return la macro correspondante, ou 404 si elle n'existe pas
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('view-reports')")
    public ResponseEntity<ApiResponse<TestPathologyMacroTextDto>> findById(@PathVariable UUID id) {
        TestPathologyMacro m = testPathologyMacroRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Macro pathologie", id));
        return ResponseEntity.ok(ApiResponse.success(
                new TestPathologyMacroTextDto(m.getId(), m.getTitle(), m.getContent(), m.getBranchId(), m.getCreatedAt())));
    }

    /**
     * Crée une nouvelle macro pour la branche de l'utilisateur connecté.
     *
     * @param dto       données de la macro (titre obligatoire, contenu optionnel)
     * @param principal principal Spring Security contenant le branchId
     * @return la macro créée avec le code HTTP 201
     */
    @PostMapping
    @PreAuthorize("hasAuthority('edit-reports')")
    @Transactional
    public ResponseEntity<ApiResponse<TestPathologyMacroTextDto>> create(
            @Valid @RequestBody TestPathologyMacroRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        TestPathologyMacro m = new TestPathologyMacro();
        m.setBranchId(principal.getBranchId());
        m.setTitle(dto.getTitle());
        m.setContent(dto.getContent());
        TestPathologyMacro saved = testPathologyMacroRepository.save(m);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Macro créée", new TestPathologyMacroTextDto(
                        saved.getId(), saved.getTitle(), saved.getContent(), saved.getBranchId(), saved.getCreatedAt())));
    }

    /**
     * Met à jour le titre et le contenu d'une macro existante.
     *
     * @param id  identifiant UUID de la macro
     * @param dto nouvelles données
     * @return la macro mise à jour
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-reports')")
    @Transactional
    public ResponseEntity<ApiResponse<TestPathologyMacroTextDto>> update(
            @PathVariable UUID id, @Valid @RequestBody TestPathologyMacroRequestDto dto) {
        TestPathologyMacro m = testPathologyMacroRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Macro pathologie", id));
        m.setTitle(dto.getTitle());
        m.setContent(dto.getContent());
        TestPathologyMacro saved = testPathologyMacroRepository.save(m);
        return ResponseEntity.ok(ApiResponse.success("Macro mise à jour", new TestPathologyMacroTextDto(
                saved.getId(), saved.getTitle(), saved.getContent(), saved.getBranchId(), saved.getCreatedAt())));
    }

    /**
     * Crée plusieurs macros en une seule transaction.
     *
     * @param dto       liste des macros à créer
     * @param principal principal Spring Security contenant le branchId
     * @return liste des macros créées avec le code HTTP 201
     */
    @PostMapping("/bulk")
    @PreAuthorize("hasAuthority('edit-reports')")
    @Transactional
    public ResponseEntity<ApiResponse<List<TestPathologyMacroTextDto>>> createBulk(
            @Valid @RequestBody BulkMacroRequestDto dto,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<TestPathologyMacro> macros = dto.getMacros().stream().map(req -> {
            TestPathologyMacro m = new TestPathologyMacro();
            m.setBranchId(principal.getBranchId());
            m.setTitle(req.getTitle());
            m.setContent(req.getContent());
            return m;
        }).toList();
        List<TestPathologyMacroTextDto> saved = testPathologyMacroRepository.saveAll(macros).stream()
                .map(m -> new TestPathologyMacroTextDto(m.getId(), m.getTitle(), m.getContent(), m.getBranchId(), m.getCreatedAt()))
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Macros créées", saved));
    }

    /**
     * Recherche des macros par titre ou contenu (insensible à la casse).
     *
     * @param q         terme de recherche (min 2 caractères)
     * @param principal principal Spring Security contenant le branchId
     * @return liste de macros correspondantes (max 50)
     */
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('view-reports')")
    public ResponseEntity<ApiResponse<List<TestPathologyMacroTextDto>>> search(
            @RequestParam @Size(min = 2, message = "Le terme de recherche doit contenir au moins 2 caractères") String q,
            @AuthenticationPrincipal UserPrincipal principal) {
        List<TestPathologyMacroTextDto> results = testPathologyMacroRepository
                .search(principal.getBranchId(), q, PageRequest.of(0, 50)).stream()
                .map(m -> new TestPathologyMacroTextDto(m.getId(), m.getTitle(), m.getContent(), m.getBranchId(), m.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    /**
     * Supprime (soft delete) une macro.
     *
     * @param id identifiant UUID de la macro à supprimer
     * @return réponse vide 200 en cas de succès
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('edit-reports')")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        testPathologyMacroRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Macro pathologie", id));
        testPathologyMacroRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success("Macro supprimée", null));
    }

    // -------------------------------------------------------------------------
    // Nouveaux endpoints — workflow macroscopie
    // -------------------------------------------------------------------------

    /**
     * Retourne la liste paginée des macroscopies réalisées avec leurs détails (laborantin,
     * étapes complétées, bon d'examen associé).
     *
     * <p>Filtres optionnels : {@code employeeId}, {@code testOrderId}, {@code search}
     * (recherche sur le code du bon ou le nom du patient).
     *
     * @param page       numéro de page (0-based, défaut 0)
     * @param size       taille de la page (défaut 20)
     * @param employeeId filtre par laborantin (optionnel)
     * @param testOrderId filtre par bon d'examen (optionnel)
     * @param search     terme de recherche libre (optionnel)
     * @param principal  principal Spring Security
     * @return page de {@link TestPathologyMacroResponseDto}
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('view-reports')")
    public ResponseEntity<ApiResponse<PageResponse<TestPathologyMacroResponseDto>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID employeeId,
            @RequestParam(required = false) UUID testOrderId,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID branchId = principal.getBranchId();

        // Construction de la spécification dynamique
        Specification<TestPathologyMacro> spec = (root, query, cb) -> {
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("branchId"), branchId));
            // Exclure les macros texte pures (sans testOrderId)
            predicates.add(cb.isNotNull(root.get("testOrderId")));
            if (employeeId != null) {
                predicates.add(cb.equal(root.get("employeeId"), employeeId));
            }
            if (testOrderId != null) {
                predicates.add(cb.equal(root.get("testOrderId"), testOrderId));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<TestPathologyMacro> macroPage = testPathologyMacroRepository.findAll(
                spec, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        // Collecte des IDs pour enrichissement (employés, bons)
        Set<UUID> employeeIds = macroPage.stream()
                .filter(m -> m.getEmployeeId() != null)
                .map(TestPathologyMacro::getEmployeeId)
                .collect(Collectors.toSet());

        Set<UUID> orderIds = macroPage.stream()
                .filter(m -> m.getTestOrderId() != null)
                .map(TestPathologyMacro::getTestOrderId)
                .collect(Collectors.toSet());

        Map<UUID, Employee> employeeMap = employeeRepository.findAllById(employeeIds).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));

        Map<UUID, TestOrder> orderMap = testOrderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(TestOrder::getId, Function.identity()));

        // Filtre textuel côté application (search sur code bon ou nom patient)
        Page<TestPathologyMacroResponseDto> resultPage = macroPage.map(m -> {
            Employee emp = m.getEmployeeId() != null ? employeeMap.get(m.getEmployeeId()) : null;
            TestOrder order = m.getTestOrderId() != null ? orderMap.get(m.getTestOrderId()) : null;
            String empName = emp != null ? emp.getFirstName() + " " + emp.getLastName() : null;
            String orderCode = order != null ? order.getCode() : null;
            return new TestPathologyMacroResponseDto(
                    m.getId(),
                    m.getTestOrderId(),
                    orderCode,
                    m.getEmployeeId(),
                    empName,
                    m.getCirculation(),
                    m.getEmbedding(),
                    m.getMicrotomySpreading(),
                    m.getStaining(),
                    m.getMounting(),
                    m.getMacroDate(),
                    m.getCreatedAt()
            );
        });

        // Filtre textuel en post-processing si search fourni
        if (search != null && !search.isBlank()) {
            String lc = search.toLowerCase();
            List<TestPathologyMacroResponseDto> filtered = resultPage.getContent().stream()
                    .filter(dto -> (dto.testOrderCode() != null && dto.testOrderCode().toLowerCase().contains(lc))
                            || (dto.employeeName() != null && dto.employeeName().toLowerCase().contains(lc)))
                    .toList();
            return ResponseEntity.ok(ApiResponse.success(PageResponse.of(
                    new org.springframework.data.domain.PageImpl<>(filtered, macroPage.getPageable(), filtered.size()))));
        }

        return ResponseEntity.ok(ApiResponse.success(PageResponse.of(resultPage)));
    }

    /**
     * Retourne les bons d'examen validés ({@code status=VALIDATED}) de la branche courante
     * qui n'ont pas encore de macroscopie assignée.
     *
     * <p>Ces demandes sont affichées dans le tableau « Demandes urgentes » de la page Macroscopie
     * et représentent le travail en attente d'assignation à un laborantin.
     *
     * @param principal principal Spring Security
     * @return liste de {@link PendingMacroDto}
     */
    @GetMapping("/pending")
    @PreAuthorize("hasAuthority('view-reports')")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ResponseEntity<ApiResponse<List<PendingMacroDto>>> getPending(
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID branchId = principal.getBranchId();

        // Récupérer tous les testOrderId déjà assignés à une macroscopie dans cette branche
        Set<UUID> assignedOrderIds = testPathologyMacroRepository
                .findAll((root, query, cb) -> cb.and(
                        cb.equal(root.get("branchId"), branchId),
                        cb.isNotNull(root.get("testOrderId"))
                ))
                .stream()
                .map(TestPathologyMacro::getTestOrderId)
                .collect(Collectors.toSet());

        // Bons validés de la branche, triés par urgence puis date
        List<TestOrder> validatedOrders = testOrderRepository.findAll(
                (root, query, cb) -> {
                    query.orderBy(
                            cb.desc(root.get("isUrgent")),
                            cb.asc(root.get("createdAt"))
                    );
                    return cb.and(
                            cb.equal(root.get("branchId"), branchId),
                            cb.equal(root.get("status"), TestOrderStatus.VALIDATED)
                    );
                }
        );

        List<PendingMacroDto> pending = validatedOrders.stream()
                .filter(o -> !assignedOrderIds.contains(o.getId()))
                .map(o -> {
                    String patientName = o.getPatient() != null
                            ? NomComplet.de(o.getPatient().getLastname(), o.getPatient().getFirstname())
                            : "";
                    String typeTitle = o.getTypeOrder() != null ? o.getTypeOrder().getTitle() : null;
                    String typeSlug = o.getTypeOrder() != null ? o.getTypeOrder().getSlug() : null;
                    return new PendingMacroDto(
                            o.getId(),
                            o.getCode(),
                            patientName,
                            o.getIsUrgent(),
                            o.getCreatedAt(),
                            typeTitle,
                            typeSlug
                    );
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.success(pending));
    }

    /**
     * Assigne un laborantin à un bon d'examen et crée l'enregistrement macroscopie.
     *
     * <p>Si une macroscopie existe déjà pour le bon ({@code testOrderId}), une
     * {@link InvalidOperationException} est levée.
     *
     * @param request   données d'assignation (bon, employé, date)
     * @param principal principal Spring Security
     * @return la macroscopie créée avec le code HTTP 201
     */
    @PostMapping("/assign")
    @PreAuthorize("hasAuthority('edit-reports')")
    @Transactional
    public ResponseEntity<ApiResponse<TestPathologyMacroResponseDto>> assign(
            @RequestBody AssignMacroRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        UUID branchId = principal.getBranchId();

        // Vérifier que le bon existe et appartient à la branche
        TestOrder order = testOrderRepository.findByIdAndBranchId(request.testOrderId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Bon d'examen", request.testOrderId()));

        // Vérifier qu'il n'y a pas déjà une macroscopie pour ce bon
        testPathologyMacroRepository.findByTestOrderId(request.testOrderId())
                .ifPresent(existing -> {
                    throw new InvalidOperationException("Une macroscopie est déjà assignée pour ce bon d'examen");
                });

        // Vérifier que l'employé existe
        Employee employee = employeeRepository.findById(request.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employé", request.employeeId()));

        TestPathologyMacro macro = new TestPathologyMacro();
        macro.setBranchId(branchId);
        macro.setTestOrderId(request.testOrderId());
        macro.setEmployeeId(request.employeeId());
        macro.setMacroDate(request.macroDate() != null ? request.macroDate() : LocalDate.now());
        // Titre minimal pour satisfaire la contrainte NOT NULL héritée du modèle texte
        macro.setTitle("Macroscopie " + (order.getCode() != null ? order.getCode() : request.testOrderId()));

        TestPathologyMacro saved = testPathologyMacroRepository.save(macro);

        String empName = employee.getFirstName() + " " + employee.getLastName();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Laborantin assigné", toResponseDto(saved, order.getCode(), empName)));
    }

    /**
     * Met à jour une étape histologique d'une macroscopie.
     *
     * <p>La logique est cumulative : chaque étape active toutes les étapes précédentes.
     * Valeurs acceptées : {@code circulation}, {@code embedding}, {@code microtomy_spreading},
     * {@code staining}, {@code mounting}.
     *
     * @param id      identifiant UUID de la macroscopie
     * @param request corps contenant le nom de l'étape à activer
     * @param principal principal Spring Security
     * @return la macroscopie mise à jour
     */
    @PatchMapping("/{id}/step")
    @PreAuthorize("hasAuthority('edit-reports')")
    @Transactional
    public ResponseEntity<ApiResponse<TestPathologyMacroResponseDto>> updateStep(
            @PathVariable UUID id,
            @RequestBody StepUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        TestPathologyMacro macro = testPathologyMacroRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Macroscopie", id));

        // Logique cumulative : chaque étape active toutes les précédentes
        switch (request.step()) {
            case "mounting" -> macro.setAllStepsTrue();
            case "staining" -> {
                macro.setCirculation(true);
                macro.setEmbedding(true);
                macro.setMicrotomySpreading(true);
                macro.setStaining(true);
            }
            case "microtomy_spreading" -> {
                macro.setCirculation(true);
                macro.setEmbedding(true);
                macro.setMicrotomySpreading(true);
            }
            case "embedding" -> {
                macro.setCirculation(true);
                macro.setEmbedding(true);
            }
            case "circulation" -> macro.setCirculation(true);
            default -> throw new InvalidOperationException(
                    "Étape inconnue : " + request.step() + ". Valeurs acceptées : circulation, embedding, microtomy_spreading, staining, mounting");
        }

        TestPathologyMacro saved = testPathologyMacroRepository.save(macro);

        // Enrichissement avec code bon et nom employé
        String orderCode = null;
        if (saved.getTestOrderId() != null) {
            orderCode = testOrderRepository.findById(saved.getTestOrderId())
                    .map(TestOrder::getCode).orElse(null);
        }
        String empName = null;
        if (saved.getEmployeeId() != null) {
            empName = employeeRepository.findById(saved.getEmployeeId())
                    .map(e -> e.getFirstName() + " " + e.getLastName()).orElse(null);
        }

        return ResponseEntity.ok(ApiResponse.success("Étape mise à jour", toResponseDto(saved, orderCode, empName)));
    }

    // -------------------------------------------------------------------------
    // Méthode utilitaire
    // -------------------------------------------------------------------------

    private TestPathologyMacroResponseDto toResponseDto(TestPathologyMacro m, String orderCode, String empName) {
        return new TestPathologyMacroResponseDto(
                m.getId(),
                m.getTestOrderId(),
                orderCode,
                m.getEmployeeId(),
                empName,
                m.getCirculation(),
                m.getEmbedding(),
                m.getMicrotomySpreading(),
                m.getStaining(),
                m.getMounting(),
                m.getMacroDate(),
                m.getCreatedAt()
        );
    }
}
