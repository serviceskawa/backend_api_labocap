package com.labo.anapath.finance;

import com.labo.anapath.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankServiceImplTest {

    @Mock private BankRepository bankRepository;
    @Mock private BankDepositRepository bankDepositRepository;
    @Mock private CashboxRepository cashboxRepository;
    @Mock private CashboxOperationRepository cashboxOperationRepository;

    @InjectMocks private BankServiceImpl service;

    private static final UUID BRANCH_ID = UUID.randomUUID();
    private static final UUID USER_ID   = UUID.randomUUID();
    private static final UUID BANK_ID   = UUID.randomUUID();

    private Cashbox buildCashbox(BigDecimal balance) {
        Cashbox c = new Cashbox();
        c.setBranchId(BRANCH_ID);
        c.setType("vente");
        c.setBalance(balance);
        c.setOpeningBalance(BigDecimal.ZERO);
        return c;
    }

    private Bank buildBank() {
        Bank b = new Bank();
        b.setBranchId(BRANCH_ID);
        b.setName("BNI Bénin");
        b.setAccountNumber("BJ12345");
        return b;
    }

    @Test
    @DisplayName("createDeposit - solde suffisant → débit caisse + CashboxOperation DEBIT")
    void createDeposit_sufficientBalance_debitsCashbox() {
        Cashbox cashbox = buildCashbox(new BigDecimal("2000.00"));
        Bank bank = buildBank();

        when(bankRepository.findById(BANK_ID)).thenReturn(Optional.of(bank));
        when(cashboxRepository.findFirstByBranchIdAndType(BRANCH_ID, "vente")).thenReturn(Optional.of(cashbox));
        when(bankDepositRepository.save(any())).thenAnswer(inv -> {
            // JPA attribue l'identifiant à la persistance ; la référence de
            // l'opération de caisse (« DEP-… ») en dérive.
            BankDeposit d = inv.getArgument(0);
            d.setId(UUID.randomUUID());
            return d;
        });
        when(cashboxOperationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cashboxRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BankDepositRequestDto dto = new BankDepositRequestDto();
        dto.setBankId(BANK_ID);
        dto.setAmount(new BigDecimal("500.00"));
        dto.setDate(LocalDate.now());

        service.createDeposit(dto, BRANCH_ID, USER_ID);

        assertThat(cashbox.getBalance()).isEqualByComparingTo(new BigDecimal("1500.00"));

        ArgumentCaptor<CashboxOperation> opCaptor = ArgumentCaptor.forClass(CashboxOperation.class);
        verify(cashboxOperationRepository).save(opCaptor.capture());
        assertThat(opCaptor.getValue().getType()).isEqualTo("DEBIT");
        assertThat(opCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("createDeposit - solde insuffisant → BusinessException 422")
    void createDeposit_insufficientBalance_throwsBusinessException() {
        Cashbox cashbox = buildCashbox(new BigDecimal("100.00"));
        Bank bank = buildBank();

        when(bankRepository.findById(BANK_ID)).thenReturn(Optional.of(bank));
        when(cashboxRepository.findFirstByBranchIdAndType(BRANCH_ID, "vente")).thenReturn(Optional.of(cashbox));

        BankDepositRequestDto dto = new BankDepositRequestDto();
        dto.setBankId(BANK_ID);
        dto.setAmount(new BigDecimal("500.00"));
        dto.setDate(LocalDate.now());

        assertThatThrownBy(() -> service.createDeposit(dto, BRANCH_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Le montant du dépôt ne peut pas être supérieur au montant de la caisse");
    }
}
