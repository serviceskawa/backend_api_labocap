package com.labo.anapath.finance;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.inventory.ArticleRepository;
import com.labo.anapath.inventory.Movement;
import com.labo.anapath.inventory.MovementRepository;
import com.labo.anapath.inventory.MovementType;
import com.labo.anapath.inventory.Supplier;
import com.labo.anapath.inventory.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExpenseServiceImpl implements ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenceDetailRepository expenceDetailRepository;
    private final CashboxRepository cashboxRepository;
    private final CashboxOperationRepository cashboxOperationRepository;
    private final ArticleRepository articleRepository;
    private final MovementRepository movementRepository;
    private final SupplierRepository supplierRepository;
    private final com.labo.anapath.testorder.FileStorageService fileStorageService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExpenseResponseDto> findAll(int page, int size, UUID branchId, Integer paid, UUID expenseCategorieId) {
        return PageResponse.of(expenseRepository.findWithFilters(
                branchId, paid, expenseCategorieId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(this::toDto));
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseResponseDto findById(UUID id) {
        return toDto(findExpense(id));
    }

    @Override
    @Transactional
    public ExpenseResponseDto create(ExpenseCreateRequestDto dto, UUID branchId) {
        Expense expense = new Expense();
        expense.setBranchId(branchId);
        expense.setExpenseCategorieId(dto.getExpenseCategorieId());
        expense.setDescription(dto.getDescription());
        expense.setSupplierId(resolveSupplierId(dto, branchId));
        // Le montant est saisi sur la page détail, ou construit par l'ajout de
        // lignes d'articles : la dépense naît à zéro et non payée.
        expense.setAmount(BigDecimal.ZERO);
        expense.setDate(LocalDate.now());
        expense.setPaid(0);
        return toDto(expenseRepository.save(expense));
    }

    /**
     * Fournisseur de la dépense : celui explicitement choisi, sinon celui dont le
     * nom a été saisi. Un nom inconnu crée le fournisseur, comme dans Laravel.
     * La recherche ignore la casse (pour ne pas créer « ACME » à côté d'« Acme »)
     * et un nom vide ne crée rien (la colonne name est NOT NULL).
     */
    private UUID resolveSupplierId(ExpenseCreateRequestDto dto, UUID branchId) {
        if (dto.getSupplierId() != null) return dto.getSupplierId();

        String name = dto.getSupplierName() != null ? dto.getSupplierName().trim() : "";
        if (name.isEmpty()) return null;

        return supplierRepository.findFirstByBranchIdAndNameIgnoreCase(branchId, name)
                .map(Supplier::getId)
                .orElseGet(() -> {
                    Supplier created = new Supplier();
                    created.setBranchId(branchId);
                    created.setName(name);
                    return supplierRepository.save(created).getId();
                });
    }

    @Override
    @Transactional
    public ExpenseResponseDto update(UUID id, ExpenseRequestDto dto) {
        Expense expense = findExpense(id);
        expense.setAmount(dto.getAmount());
        expense.setExpenseCategorieId(dto.getExpenseCategorieId());
        expense.setDescription(dto.getDescription());
        expense.setSupplierId(dto.getSupplierId());
        expense.setInvoiceNumber(dto.getInvoiceNumber());
        if (dto.getDate() != null) expense.setDate(dto.getDate());
        expense.setPayment(dto.getPayment());
        expense.setReceipt(dto.getReceipt());
        return toDto(expenseRepository.save(expense));
    }

    /**
     * Enregistre la preuve d'achat et la rattache à la dépense. Le répertoire
     * « preuves » reprend celui utilisé par Laravel.
     */
    @Override
    @Transactional
    public ExpenseResponseDto uploadReceipt(UUID id, MultipartFile file) {
        Expense expense = findExpense(id);
        try {
            expense.setReceipt(fileStorageService.store(file, "preuves"));
        } catch (java.io.IOException e) {
            throw new BusinessException(
                    "Erreur lors du stockage du fichier: " + file.getOriginalFilename());
        }
        return toDto(expenseRepository.save(expense));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Expense expense = findExpense(id);
        expenseRepository.delete(expense);
    }

    @Override
    @Transactional
    public ExpenseResponseDto addDetail(UUID expenseId, ExpenceDetailRequestDto dto, UUID branchId) {
        Expense expense = findExpense(expenseId);

        BigDecimal lineAmount = dto.getUnitPrice().multiply(dto.getQuantity());

        ExpenceDetail detail = new ExpenceDetail();
        detail.setBranchId(branchId);
        detail.setExpense(expense);
        detail.setArticleName(dto.getArticleName());
        detail.setQuantity(dto.getQuantity());
        detail.setUnitPrice(dto.getUnitPrice());
        detail.setLineAmount(lineAmount);
        expenceDetailRepository.save(detail);

        expense.setAmount(expense.getAmount().add(lineAmount));
        return toDto(expenseRepository.save(expense));
    }

    @Override
    @Transactional
    public void removeDetail(UUID expenseId, UUID detailId) {
        Expense expense = findExpense(expenseId);
        ExpenceDetail detail = expenceDetailRepository.findById(detailId)
                .orElseThrow(() -> new ResourceNotFoundException("ExpenceDetail", detailId));

        BigDecimal lineAmount = detail.getLineAmount() != null ? detail.getLineAmount() : BigDecimal.ZERO;
        expense.setAmount(expense.getAmount().subtract(lineAmount));
        expenseRepository.save(expense);
        expenceDetailRepository.delete(detail);
    }

    @Override
    @Transactional
    public ExpenseResponseDto payExpense(UUID expenseId, UUID branchId, UUID userId) {
        Expense expense = findExpense(expenseId);

        if (expense.getPaid() > 0) {
            throw new BusinessException("Cette dépense est déjà payée");
        }

        debitCaisseDepense(expense, branchId);
        expense.setPaid(1);
        return toDto(expenseRepository.save(expense));
    }

    @Override
    @Transactional
    public ExpenseResponseDto updateStock(UUID expenseId, UUID branchId, UUID userId) {
        Expense expense = findExpense(expenseId);

        if (expense.getPaid() == 0) {
            // Faire d'abord le paiement
            debitCaisseDepense(expense, branchId);
        }

        expense.setPaid(2);
        expenseRepository.save(expense);

        List<ExpenceDetail> details = expenceDetailRepository.findByExpenseId(expenseId);
        for (ExpenceDetail detail : details) {
            if (detail.getArticleName() == null) continue;
            articleRepository.findFirstByBranchIdAndName(branchId, detail.getArticleName())
                    .ifPresent(article -> {
                        BigDecimal qty = detail.getQuantity() != null ? detail.getQuantity() : BigDecimal.ZERO;
                        article.setQuantity(article.getQuantity().add(qty));
                        articleRepository.save(article);

                        Movement movement = new Movement();
                        movement.setBranchId(branchId);
                        movement.setArticle(article);
                        movement.setType(MovementType.IN);
                        movement.setQuantity(qty);
                        movement.setNotes("Dépense " + expenseId);
                        movementRepository.save(movement);
                    });
        }

        return toDto(expense);
    }

    private void debitCaisseDepense(Expense expense, UUID branchId) {
        Cashbox caisse = cashboxRepository.findFirstByBranchIdAndType(branchId, "depense")
                .orElseThrow(() -> new ResourceNotFoundException("Caisse dépense introuvable pour la branche"));

        caisse.setBalance(caisse.getBalance().subtract(expense.getAmount()));
        cashboxRepository.save(caisse);

        CashboxOperation operation = new CashboxOperation();
        operation.setBranchId(branchId);
        operation.setCashbox(caisse);
        operation.setAmount(expense.getAmount());
        operation.setType("DEBIT");
        operation.setDescription("Paiement dépense " + expense.getId());
        operation.setOperationDate(LocalDate.now());
        cashboxOperationRepository.save(operation);
    }

    private Expense findExpense(UUID id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Dépense", id));
    }

    private ExpenseResponseDto toDto(Expense e) {
        List<ExpenceDetailResponseDto> details = expenceDetailRepository.findByExpenseId(e.getId())
                .stream()
                .map(d -> new ExpenceDetailResponseDto(
                        d.getId(), d.getArticleName(), d.getArticleId(),
                        d.getQuantity(), d.getUnitPrice(), d.getLineAmount()))
                .toList();
        return new ExpenseResponseDto(
                e.getId(),
                e.getAmount(),
                e.getDescription(),
                e.getSupplierId(),
                e.getExpenseCategorieId(),
                e.getCashboxVoucherId(),
                e.getPaid(),
                e.getDate(),
                e.getInvoiceNumber(),
                e.getPayment(),
                e.getReceipt(),
                details,
                e.getBranchId(),
                e.getCreatedAt()
        );
    }
}
