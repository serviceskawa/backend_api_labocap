package com.labo.anapath.finance;

import com.labo.anapath.common.dto.PageResponse;
import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CashboxDailyServiceImpl implements CashboxDailyService {

    private final CashboxDailyRepository cashboxDailyRepository;
    private final CashboxRepository cashboxRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CashboxDailyResponseDto openOrUpdate(CashboxDailyOpenDto dto, UUID branchId, UUID userId) {
        Cashbox cashbox = resolveCashbox(dto.getCashboxId(), branchId);

        LocalDate today = LocalDate.now();
        CashboxDaily daily = cashboxDailyRepository
                .findByBranchIdAndCashboxIdAndDate(branchId, cashbox.getId(), today)
                .orElse(null);

        if (daily != null) {
            daily.setOpeningBalance(dto.getSoldeOuverture());
            daily.setStatus(1);
        } else {
            daily = new CashboxDaily();
            daily.setBranchId(branchId);
            daily.setCashbox(cashbox);
            daily.setOpeningBalance(dto.getSoldeOuverture());
            daily.setClosingBalance(BigDecimal.ZERO);
            daily.setStatus(1);
            daily.setDate(today);
        }

        CashboxDaily savedDaily = cashboxDailyRepository.save(daily);
        if (savedDaily.getCode() == null) {
            savedDaily.setCode(generateCode(savedDaily.getId()));
            cashboxDailyRepository.save(savedDaily);
        }

        // Mettre à jour la caisse
        BigDecimal newBalance = cashbox.getBalance().subtract(dto.getSoldeOuverture());
        cashbox.setOpeningBalance(dto.getSoldeOuverture());
        cashbox.setBalance(newBalance.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newBalance);
        cashbox.setStatut(1);
        cashboxRepository.save(cashbox);

        return toDto(savedDaily);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CashboxDailyResponseDto> findAll(int page, int size, UUID branchId) {
        return PageResponse.of(cashboxDailyRepository.findByBranchId(branchId,
                PageRequest.of(page, size, Sort.by("date").descending()))
                .map(this::toDto));
    }

    @Override
    @Transactional(readOnly = true)
    public CashboxDailyResponseDto findById(UUID id, UUID branchId) {
        CashboxDaily daily = findDaily(id);
        if (!daily.getBranchId().equals(branchId)) {
            throw new ResourceNotFoundException("Session caisse", id);
        }
        return toDto(daily);
    }

    @Override
    @Transactional
    public CashboxDailyResponseDto closeCashbox(UUID id, CashboxDailyCloseDto dto, UUID userId) {
        CashboxDaily daily = findDaily(id);

        if (!userId.equals(daily.getCreatedBy())) {
            throw new BusinessException("Vous n'êtes pas autorisé à fermer cette caisse");
        }

        daily.setStatus(0);
        daily.setClosingBalance(dto.getClosingBalance() != null ? dto.getClosingBalance() : BigDecimal.ZERO);
        daily.setCashCalculated(dto.getCashCalculated());
        daily.setCashConfirmation(dto.getCashConfirmation());
        daily.setCashEcart(dto.getCashEcart());
        daily.setMobileMoneyCalculated(dto.getMobileMoneyCalculated());
        daily.setMoneyMoneyConfirmation(dto.getMoneyMoneyConfirmation());
        daily.setMobileMoneyEcart(dto.getMobileMoneyEcart());
        daily.setChequeCalculated(dto.getChequeCalculated());
        daily.setChequeConfirmation(dto.getChequeConfirmation());
        daily.setChequeEcart(dto.getChequeEcart());
        daily.setVirementCalculated(dto.getVirementCalculated());
        daily.setVirementConfirmation(dto.getVirementConfirmation());
        daily.setVirementEcart(dto.getVirementEcart());
        daily.setTotalCalculated(dto.getTotalCalculated());
        daily.setTotalConfirmation(dto.getTotalConfirmation());
        daily.setTotalEcart(dto.getTotalEcart());

        // Recalculer le solde de la caisse
        Cashbox cashbox = daily.getCashbox();
        BigDecimal ecart = daily.getTotalEcart() != null ? daily.getTotalEcart() : BigDecimal.ZERO;
        BigDecimal result = cashbox.getBalance()
                .add(cashbox.getOpeningBalance())
                .add(ecart);

        cashbox.setBalance(result);
        cashbox.setOpeningBalance(BigDecimal.ZERO);
        cashbox.setStatut(0);
        cashboxRepository.save(cashbox);

        return toDto(cashboxDailyRepository.save(daily));
    }

    @Override
    @Transactional(readOnly = true)
    public CashboxDailySummaryDto getDailySummary(UUID branchId) {
        LocalDateTime sinceDate = cashboxDailyRepository
                .findFirstByBranchIdAndStatusOrderByUpdatedAtDesc(branchId, 1)
                .map(d -> d.getUpdatedAt() != null ? d.getUpdatedAt() : d.getCreatedAt())
                .orElse(LocalDate.now().atStartOfDay());

        BigDecimal especes = orZero(cashboxDailyRepository.sumCreditByPaymentMethod(branchId, "ESPECES", sinceDate));
        BigDecimal mobileMoney = orZero(cashboxDailyRepository.sumCreditByPaymentMethod(branchId, "MOBILEMONEY", sinceDate));
        BigDecimal cheques = orZero(cashboxDailyRepository.sumCreditByPaymentMethod(branchId, "CHEQUES", sinceDate));
        BigDecimal virement = orZero(cashboxDailyRepository.sumCreditByPaymentMethod(branchId, "VIREMENT", sinceDate));
        BigDecimal total = especes.add(mobileMoney).add(cheques).add(virement);

        return new CashboxDailySummaryDto(especes, mobileMoney, cheques, virement, total);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        CashboxDaily daily = findDaily(id);
        cashboxDailyRepository.delete(daily);
    }

    private Cashbox resolveCashbox(UUID cashboxId, UUID branchId) {
        if (cashboxId != null) {
            return cashboxRepository.findById(cashboxId)
                    .orElseThrow(() -> new ResourceNotFoundException("Cashbox", cashboxId));
        }
        return cashboxRepository.findFirstByBranchIdAndType(branchId, "vente")
                .orElseThrow(() -> new ResourceNotFoundException("Caisse vente introuvable pour cette branche"));
    }

    private CashboxDaily findDaily(UUID id) {
        return cashboxDailyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CashboxDaily", id));
    }

    private String generateCode(UUID id) {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        return String.format("OUV-%s-%s", datePart, id.toString().substring(0, 8).toUpperCase());
    }

    private BigDecimal orZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private CashboxDailyResponseDto toDto(CashboxDaily d) {
        return new CashboxDailyResponseDto(
                d.getId(),
                d.getCashbox() != null ? d.getCashbox().getId() : null,
                d.getOpeningBalance(),
                d.getClosingBalance(),
                d.getDate(),
                d.getStatus(),
                d.getCode(),
                d.getCashCalculated(),
                d.getCashConfirmation(),
                d.getCashEcart(),
                d.getMobileMoneyCalculated(),
                d.getMoneyMoneyConfirmation(),
                d.getMobileMoneyEcart(),
                d.getChequeCalculated(),
                d.getChequeConfirmation(),
                d.getChequeEcart(),
                d.getVirementCalculated(),
                d.getVirementConfirmation(),
                d.getVirementEcart(),
                d.getTotalCalculated(),
                d.getTotalConfirmation(),
                d.getTotalEcart(),
                d.getBranchId(),
                d.getCreatedAt(),
                // updatedAt fait office de date de fermeture (statut 0 = clôturée) ;
                // userName = agent ayant ouvert/fermé la session (colonnes vue Laravel).
                d.getUpdatedAt(),
                resolveUserName(d.getCreatedBy())
        );
    }

    private String resolveUserName(UUID userId) {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .map(u -> (u.getFirstname() + " " + u.getLastname()).trim())
                .orElse(null);
    }
}
