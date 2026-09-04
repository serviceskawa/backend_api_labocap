package com.labo.anapath.finance;

import com.labo.anapath.common.NomComplet;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.InvalidOperationException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final RefundRequestRepository refundRequestRepository;
    private final RefundRequestLogRepository refundRequestLogRepository;
    private final InvoiceRepository invoiceRepository;
    private final RefundReasonRepository refundReasonRepository;
    private final com.labo.anapath.user.UserRepository userRepository;

    @Override
    @Transactional
    public RefundRequestResponseDto create(RefundRequestCreateDto dto, UUID branchId, UUID userId) {
        if (refundRequestRepository.existsByInvoiceId(dto.getInvoiceId())) {
            throw new InvalidOperationException("REFUND_ALREADY_EXISTS");
        }

        Invoice invoice = invoiceRepository.findById(dto.getInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Facture", dto.getInvoiceId()));

        if (dto.getMontant().compareTo(invoice.getTotal()) > 0) {
            throw new InvalidOperationException("REFUND_AMOUNT_EXCEEDS_INVOICE");
        }

        RefundRequest refund = new RefundRequest();
        refund.setBranchId(branchId);
        refund.setInvoice(invoice);
        refund.setRefundReasonId(dto.getRefundReasonId());
        refund.setMontant(dto.getMontant());
        refund.setNote(dto.getNote());
        refund.setAttachment(dto.getAttachment());
        refund.setCode(generateCodeDemandeRemboursement(branchId));
        refund.setStatus("En attente");
        RefundRequest saved = refundRequestRepository.save(refund);

        createLog(saved, userId, "En attente");

        return toResponseDto(saved);
    }

    /**
     * Édite une demande de remboursement (facture, motif, montant, note, pièce jointe).
     *
     * <p>Calque Laravel `refund.request.update` : un fichier absent conserve la
     * pièce jointe existante ; le montant ne peut dépasser le total de la facture.</p>
     */
    @Override
    @Transactional
    public RefundRequestResponseDto update(UUID id, RefundRequestUpdateDto dto, UUID userId) {
        RefundRequest refund = refundRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de remboursement", id));

        Invoice invoice = invoiceRepository.findById(dto.getInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Facture", dto.getInvoiceId()));

        if (dto.getMontant().compareTo(invoice.getTotal()) > 0) {
            throw new InvalidOperationException("REFUND_AMOUNT_EXCEEDS_INVOICE");
        }

        refund.setInvoice(invoice);
        refund.setRefundReasonId(dto.getRefundReasonId());
        refund.setMontant(dto.getMontant());
        refund.setNote(dto.getNote());
        // Fichier absent → on conserve la pièce jointe existante.
        if (dto.getAttachment() != null && !dto.getAttachment().isBlank()) {
            refund.setAttachment(dto.getAttachment());
        }

        return toResponseDto(refundRequestRepository.save(refund));
    }

    @Override
    @Transactional
    public RefundRequestStatusResult updateStatus(UUID id, RefundRequestStatusUpdateDto dto, UUID userId) {
        RefundRequest refund = refundRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de remboursement", id));

        refund.setStatus(dto.getStatus());
        refundRequestRepository.save(refund);
        createLog(refund, userId, dto.getStatus());

        // R1 : "Aprouvé" avec UN SEUL 'p' — valeur exacte de la source Laravel
        if ("Aprouvé".equals(dto.getStatus())) {
            Invoice avoir = new Invoice();
            avoir.setBranchId(refund.getBranchId());
            avoir.setDate(LocalDate.now()); // calque Laravel : 'date' => Carbon::now()
            avoir.setClientName(refund.getInvoice() != null ? refund.getInvoice().getClientName() : null);
            avoir.setClientAddress(refund.getInvoice() != null ? refund.getInvoice().getClientAddress() : null);
            avoir.setTotal(refund.getMontant());
            avoir.setStatusInvoice(1);
            avoir.setReference(refund.getInvoice());
            avoir.setCode(generateCodeFacture(refund.getBranchId()));
            avoir.setStatus(InvoiceStatus.PENDING);
            avoir.setPaid(false);
            Invoice savedAvoir = invoiceRepository.save(avoir);

            // Auto-clôture si pièce jointe présente ET avoir déjà payé
            if (refund.getAttachment() != null && Boolean.TRUE.equals(savedAvoir.getPaid())) {
                refund.setStatus("Clôturé");
                refundRequestRepository.save(refund);
                createLog(refund, userId, "Clôturé");
            }

            return new RefundRequestStatusResult(savedAvoir.getId(), "Aprouvé");

        } else if (!"En attente".equals(dto.getStatus())) {
            // Tout statut autre qu'En attente et Aprouvé → clôture
            refund.setStatus("Clôturé");
            refundRequestRepository.save(refund);
            createLog(refund, userId, "Clôturé");
            return new RefundRequestStatusResult(null, "Clôturé");
        }

        return new RefundRequestStatusResult(null, dto.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<RefundRequestResponseDto> findAll(int page, int size, UUID branchId) {
        return PageResponse.of(refundRequestRepository.findByBranchId(branchId,
                PageRequest.of(page, size, Sort.by("createdAt").descending()))
                .map(this::toResponseDto));
    }

    @Override
    @Transactional(readOnly = true)
    public RefundRequestResponseDto findById(UUID id) {
        return toResponseDto(refundRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de remboursement", id)));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        RefundRequest refund = refundRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de remboursement", id));
        refundRequestRepository.delete(refund);
    }

    @Override
    @Transactional(readOnly = true)
    public long countPending(UUID branchId) {
        return refundRequestRepository.countByBranchIdAndStatus(branchId, "En attente");
    }

    private void createLog(RefundRequest refund, UUID userId, String operation) {
        RefundRequestLog log = new RefundRequestLog();
        log.setBranchId(refund.getBranchId());
        log.setRefundRequest(refund);
        log.setUserId(userId);
        log.setOperation(operation);
        refundRequestLogRepository.save(log);
    }

    private RefundRequestResponseDto toResponseDto(RefundRequest refund) {
        List<RefundRequestLogDto> logs = refundRequestLogRepository.findByRefundRequestId(refund.getId())
                .stream()
                .map(l -> new RefundRequestLogDto(
                        l.getId(),
                        l.getUserId(),
                        userFullName(l.getUserId()),
                        l.getOperation(),
                        l.getCreatedAt()))
                .toList();
        String reasonLabel = refund.getRefundReasonId() == null ? null
                : refundReasonRepository.findById(refund.getRefundReasonId())
                        .map(RefundReason::getLabel).orElse(null);
        return new RefundRequestResponseDto(
                refund.getId(),
                refund.getInvoice() != null ? refund.getInvoice().getId() : null,
                refund.getInvoice() != null ? refund.getInvoice().getCode() : null,
                refund.getRefundReasonId(),
                reasonLabel,
                refund.getMontant(),
                refund.getNote(),
                refund.getAttachment(),
                refund.getCode(),
                refund.getStatus(),
                logs,
                refund.getBranchId(),
                refund.getCreatedAt(),
                refund.getUpdatedAt()
        );
    }

    /** « Prénom Nom » de l'auteur d'un log, vide si l'utilisateur n'existe plus. */
    private String userFullName(UUID userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(u -> NomComplet.de(u.getLastname(), u.getFirstname()))
                .orElse(null);
    }

    /**
     * Code d'une demande de remboursement : "DER" + année sur 2 chiffres +
     * séquence 4 chiffres — ex. DER260001. La séquence se calcule sur les
     * demandes de l'année, comme le helper Laravel {@code generateCodeFactureAvoir}.
     */
    private String generateCodeDemandeRemboursement(UUID branchId) {
        int year = LocalDate.now().getYear();
        List<RefundRequest> last = refundRequestRepository.findLastCodeOfYear(
                branchId, year, PageRequest.of(0, 1));
        String seq = "0001";
        if (!last.isEmpty()) {
            String lastCode = last.get(0).getCode();
            if (lastCode != null && lastCode.length() >= 4) {
                try {
                    int lastSeq = Integer.parseInt(lastCode.substring(lastCode.length() - 4));
                    seq = String.format("%04d", lastSeq + 1);
                } catch (NumberFormatException ignored) {}
            }
        }
        return "DER" + (year % 100) + seq;
    }

    // Format "FA" + (year % 100) + séquence 4 chiffres — ex: FA260001
    private String generateCodeFacture(UUID branchId) {
        int year = LocalDate.now().getYear();
        List<Invoice> invoices = invoiceRepository.findByBranchIdAndCodeNotNullAndYear(
                branchId, year, PageRequest.of(0, 1));
        String seq = "0001";
        if (!invoices.isEmpty()) {
            String lastCode = invoices.get(0).getCode();
            if (lastCode != null && lastCode.length() >= 4) {
                try {
                    int lastSeq = Integer.parseInt(lastCode.substring(lastCode.length() - 4));
                    seq = String.format("%04d", lastSeq + 1);
                } catch (NumberFormatException ignored) {}
            }
        }
        return "FA" + (year % 100) + seq;
    }
}
