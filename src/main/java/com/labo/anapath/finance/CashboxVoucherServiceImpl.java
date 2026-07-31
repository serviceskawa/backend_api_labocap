package com.labo.anapath.finance;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.inventory.Article;
import com.labo.anapath.inventory.ArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CashboxVoucherServiceImpl implements CashboxVoucherService {

    /** Statut initial d'un bon de caisse (valeur Laravel, stockée telle quelle). */
    private static final String STATUS_PENDING = "en attente";

    private final CashboxVoucherRepository voucherRepository;
    private final CashboxVoucherDetailRepository detailRepository;
    private final ExpenseRepository expenseRepository;
    private final ExpenceDetailRepository expenceDetailRepository;
    private final ArticleRepository articleRepository;
    private final com.labo.anapath.inventory.SupplierRepository supplierRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CashboxVoucherResponseDto> findAll(int page, int size, UUID branchId) {
        return PageResponse.of(voucherRepository.findByBranchId(branchId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(this::toDto));
    }

    @Override
    @Transactional(readOnly = true)
    public long countPending(UUID branchId) {
        return voucherRepository.countByBranchIdAndStatus(branchId, STATUS_PENDING);
    }

    @Override
    @Transactional(readOnly = true)
    public CashboxVoucherResponseDto findById(UUID id, UUID branchId) {
        CashboxVoucher voucher = findVoucher(id);
        if (!voucher.getBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Bon de caisse", id);
        }
        return toDto(voucher);
    }

    @Override
    @Transactional
    public CashboxVoucherResponseDto create(CashboxVoucherRequestDto dto, UUID branchId) {
        CashboxVoucher voucher = new CashboxVoucher();
        voucher.setBranchId(branchId);
        voucher.setDescription(dto.getDescription());
        voucher.setSupplierId(dto.getSupplierId());
        voucher.setExpenseCategoryId(dto.getExpenseCategoryId());
        voucher.setTicketFile(dto.getTicketFile());
        voucher.setAmount(BigDecimal.ZERO);
        voucher.setStatus(STATUS_PENDING);

        CashboxVoucher saved = voucherRepository.save(voucher);
        saved.setCode(generateCode(branchId, saved));
        return toDto(voucherRepository.save(saved));
    }

    @Override
    @Transactional
    public CashboxVoucherResponseDto update(UUID id, CashboxVoucherRequestDto dto) {
        CashboxVoucher voucher = findVoucher(id);
        voucher.setDescription(dto.getDescription());
        voucher.setSupplierId(dto.getSupplierId());
        voucher.setExpenseCategoryId(dto.getExpenseCategoryId());
        // Fichier absent → conserver la pièce jointe existante (ne pas écraser avec null).
        if (dto.getTicketFile() != null && !dto.getTicketFile().isBlank()) {
            voucher.setTicketFile(dto.getTicketFile());
        }
        return toDto(voucherRepository.save(voucher));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        CashboxVoucher voucher = findVoucher(id);
        voucherRepository.delete(voucher);
    }

    @Override
    @Transactional
    public CashboxVoucherResponseDto addDetail(UUID voucherId, CashboxVoucherDetailRequestDto dto, UUID branchId) {
        CashboxVoucher voucher = findVoucher(voucherId);

        BigDecimal lineAmount = dto.getUnitPrice().multiply(dto.getQuantity());

        CashboxVoucherDetail detail = new CashboxVoucherDetail();
        detail.setBranchId(branchId);
        detail.setCashboxVoucher(voucher);
        detail.setItemName(dto.getItemName());
        detail.setQuantity(dto.getQuantity());
        detail.setUnitPrice(dto.getUnitPrice());
        detail.setLineAmount(lineAmount);
        detailRepository.save(detail);

        voucher.setAmount(voucher.getAmount().add(lineAmount));
        return toDto(voucherRepository.save(voucher));
    }

    @Override
    @Transactional
    public void removeDetail(UUID voucherId, UUID detailId) {
        CashboxVoucher voucher = findVoucher(voucherId);
        CashboxVoucherDetail detail = detailRepository.findById(detailId)
                .orElseThrow(() -> new ResourceNotFoundException("CashboxVoucherDetail", detailId));

        BigDecimal lineAmount = detail.getLineAmount() != null ? detail.getLineAmount() : BigDecimal.ZERO;
        voucher.setAmount(voucher.getAmount().subtract(lineAmount));
        voucherRepository.save(voucher);
        detailRepository.delete(detail);
    }

    @Override
    @Transactional
    public CashboxVoucherResponseDto updateStatus(UUID voucherId, CashboxVoucherStatusDto dto, UUID branchId, UUID userId) {
        CashboxVoucher voucher = findVoucher(voucherId);
        String newStatus = dto.getStatus();

        if (!List.of("en attente", "approuve", "rejete").contains(newStatus)) {
            throw new BusinessException("Statut invalide: " + newStatus);
        }

        voucher.setStatus(newStatus);
        voucherRepository.save(voucher);

        if ("approuve".equals(newStatus)) {
            approveVoucher(voucher, branchId);
        }

        return toDto(voucher);
    }

    private void approveVoucher(CashboxVoucher voucher, UUID branchId) {
        Expense expense = new Expense();
        expense.setBranchId(branchId);
        expense.setAmount(voucher.getAmount());
        expense.setDescription(voucher.getDescription());
        expense.setSupplierId(voucher.getSupplierId());
        expense.setExpenseCategorieId(voucher.getExpenseCategoryId());
        expense.setCashboxVoucherId(voucher.getId());
        expense.setReceipt(voucher.getTicketFile());
        expense.setPaid(0);
        Expense savedExpense = expenseRepository.save(expense);

        List<CashboxVoucherDetail> voucherDetails = detailRepository.findByCashboxVoucherId(voucher.getId());
        for (CashboxVoucherDetail vd : voucherDetails) {
            Article article = null;
            if (vd.getItemName() != null) {
                article = articleRepository.findFirstByBranchIdAndName(branchId, vd.getItemName()).orElse(null);
            }

            ExpenceDetail ed = new ExpenceDetail();
            ed.setBranchId(branchId);
            ed.setExpense(savedExpense);
            ed.setArticleName(vd.getItemName());
            ed.setArticleId(article != null ? article.getId() : null);
            ed.setQuantity(vd.getQuantity());
            ed.setUnitPrice(vd.getUnitPrice());
            ed.setLineAmount(vd.getLineAmount());
            expenceDetailRepository.save(ed);
        }
    }

    /**
     * Code d'un bon de caisse, au format Laravel {@code generateCodeTicket()} :
     * {@code BC} + les deux derniers chiffres de l'année + {@code -} + un
     * compteur sur 4 chiffres remis à {@code 0001} chaque année — p. ex.
     * {@code BC26-0001}.
     */
    private String generateCode(UUID branchId, CashboxVoucher voucher) {
        LocalDate today = voucher.getCreatedAt() != null
                ? voucher.getCreatedAt().toLocalDate()
                : LocalDate.now();
        String yearPrefix = String.format("BC%02d-", today.getYear() % 100);
        Integer maxSequence = voucherRepository.findMaxSequenceForYear(yearPrefix);
        int next = (maxSequence == null ? 0 : maxSequence) + 1;
        return yearPrefix + String.format("%04d", next);
    }

    private CashboxVoucher findVoucher(UUID id) {
        return voucherRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CashboxVoucher", id));
    }

    private CashboxVoucherResponseDto toDto(CashboxVoucher v) {
        List<CashboxVoucherDetailResponseDto> details = v.getDetails().stream()
                .map(d -> new CashboxVoucherDetailResponseDto(
                        d.getId(), d.getItemName(), d.getQuantity(), d.getUnitPrice(), d.getLineAmount()))
                .toList();
        // Colonne « Fournisseur » de la vue Laravel « Bon de caisse ».
        String supplierName = v.getSupplierId() == null ? null :
                supplierRepository.findById(v.getSupplierId())
                        .map(com.labo.anapath.inventory.Supplier::getName).orElse(null);
        return new CashboxVoucherResponseDto(
                v.getId(),
                v.getCashbox() != null ? v.getCashbox().getId() : null,
                v.getCode(),
                v.getAmount(),
                v.getDescription(),
                v.getStatus(),
                v.getSupplierId(),
                supplierName,
                v.getExpenseCategoryId(),
                v.getTicketFile(),
                details,
                v.getBranchId(),
                v.getCreatedAt()
        );
    }
}
