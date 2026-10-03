package com.labo.anapath.finance;

import com.labo.anapath.common.branch.BranchContext;
import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Implémentation de {@link PaymentService} pour l'enregistrement des paiements.
 *
 * <p>Si le mode de paiement n'est pas renseigné dans le DTO, le mode par défaut
 * {@link PaymentMethod#CASH} est appliqué automatiquement.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final FinanceMapper financeMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponseDto> findAll(int page, int size, UUID branchId) {
        return PageResponse.of(paymentRepository.findByBranchId(branchId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(financeMapper::toPaymentResponseDto));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PaymentResponseDto findById(UUID id, UUID branchId) {
        Payment payment = paymentRepository.findByIdAndBranchId(id, branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement", id));
        return financeMapper.toPaymentResponseDto(payment);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Si le champ {@code method} du DTO est null, le mode {@link PaymentMethod#CASH}
     * est utilisé par défaut (comportement défensif).</p>
     */
    @Override
    @Transactional
    public PaymentResponseDto create(PaymentRequestDto dto, UUID branchId) {
        Payment payment = new Payment();
        payment.setBranchId(branchId);
        payment.setAmount(dto.getAmount());
        // Utilise CASH par défaut si le mode de paiement n'est pas précisé
        payment.setMethod(dto.getMethod() != null ? dto.getMethod() : PaymentMethod.CASH);
        payment.setPaymentDate(dto.getPaymentDate());
        payment.setNotes(dto.getNotes());
        payment.setInvoice(invoiceRepository.findByIdAndBranchId(dto.getInvoiceId(), branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Facture", dto.getInvoiceId())));
        return financeMapper.toPaymentResponseDto(paymentRepository.save(payment));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(UUID id) {
        paymentRepository.findByIdAndBranchId(id, BranchContext.get())
                .orElseThrow(() -> new ResourceNotFoundException("Paiement", id));
        paymentRepository.deleteById(id);
    }
}
