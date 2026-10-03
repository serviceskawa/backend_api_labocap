package com.labo.anapath.finance;

import com.labo.anapath.common.exception.BusinessException;
import com.labo.anapath.common.exception.ResourceNotFoundException;
import com.labo.anapath.inventory.Article;
import com.labo.anapath.inventory.ArticleRepository;
import com.labo.anapath.inventory.Movement;
import com.labo.anapath.inventory.MovementRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceImplTest {

    @Mock private ExpenseRepository expenseRepository;
    @Mock private com.labo.anapath.common.storage.FichierStockeRepository fichiers;
    @Mock private ExpenceDetailRepository expenceDetailRepository;
    @Mock private CashboxRepository cashboxRepository;
    @Mock private CashboxOperationRepository cashboxOperationRepository;
    @Mock private ArticleRepository articleRepository;
    @Mock private MovementRepository movementRepository;

    @InjectMocks private ExpenseServiceImpl service;

    private static final UUID BRANCH_ID  = UUID.randomUUID();
    private static final UUID USER_ID    = UUID.randomUUID();
    private static final UUID EXPENSE_ID = UUID.randomUUID();

    private Expense buildExpense(int paid, BigDecimal amount) {
        Expense e = new Expense();
        e.setAmount(amount);
        e.setPaid(paid);
        e.setDescription("Dépense test");
        return e;
    }

    private Cashbox buildCaisse(BigDecimal balance) {
        Cashbox c = new Cashbox();
        c.setName("Caisse dépense");
        c.setType("depense");
        c.setBalance(balance);
        return c;
    }

    // ── payExpense ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("payExpense - dépense non payée → débite la caisse et passe paid=1")
    void payExpense_unpaid_debitsCaisseAndSetsPaid1() {
        Expense expense = buildExpense(0, new BigDecimal("300.00"));
        Cashbox caisse = buildCaisse(new BigDecimal("1000.00"));

        when(expenseRepository.findById(EXPENSE_ID)).thenReturn(Optional.of(expense));
        when(cashboxRepository.findFirstByBranchIdAndType(BRANCH_ID, "depense"))
                .thenReturn(Optional.of(caisse));
        when(cashboxOperationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(expenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(expenceDetailRepository.findByExpenseId(any())).thenReturn(List.of());

        service.payExpense(EXPENSE_ID, BRANCH_ID, USER_ID);

        // Solde caisse débité
        assertThat(caisse.getBalance()).isEqualByComparingTo(new BigDecimal("700.00"));

        // Opération de caisse enregistrée
        ArgumentCaptor<CashboxOperation> opCaptor = ArgumentCaptor.forClass(CashboxOperation.class);
        verify(cashboxOperationRepository).save(opCaptor.capture());
        assertThat(opCaptor.getValue().getType()).isEqualTo("DEBIT");
        assertThat(opCaptor.getValue().getAmount()).isEqualByComparingTo(new BigDecimal("300.00"));

        // Dépense marquée paid=1
        ArgumentCaptor<Expense> expCaptor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(expCaptor.capture());
        assertThat(expCaptor.getValue().getPaid()).isEqualTo(1);
    }

    @Test
    @DisplayName("payExpense - dépense déjà payée → BusinessException")
    void payExpense_alreadyPaid_throwsBusinessException() {
        Expense expense = buildExpense(1, new BigDecimal("300.00"));
        when(expenseRepository.findById(EXPENSE_ID)).thenReturn(Optional.of(expense));

        assertThatThrownBy(() -> service.payExpense(EXPENSE_ID, BRANCH_ID, USER_ID))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("déjà payée");

        verify(cashboxRepository, never()).findFirstByBranchIdAndType(any(), any());
    }

    @Test
    @DisplayName("payExpense - caisse dépense introuvable → ResourceNotFoundException")
    void payExpense_noCaisse_throwsResourceNotFoundException() {
        Expense expense = buildExpense(0, new BigDecimal("200.00"));
        when(expenseRepository.findById(EXPENSE_ID)).thenReturn(Optional.of(expense));
        when(cashboxRepository.findFirstByBranchIdAndType(BRANCH_ID, "depense"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.payExpense(EXPENSE_ID, BRANCH_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── updateStock (AC16) ────────────────────────────────────────────────────

    @Test
    @DisplayName("updateStock - dépense non payée → débite caisse, met à jour stock et passe paid=2")
    void updateStock_unpaid_debitsThenUpdatesStockAndSetsPaid2() {
        Expense expense = buildExpense(0, new BigDecimal("500.00"));
        Cashbox caisse = buildCaisse(new BigDecimal("2000.00"));

        ExpenceDetail detail = new ExpenceDetail();
        detail.setArticleName("Gants");
        detail.setQuantity(new BigDecimal("10"));

        Article article = new Article();
        article.setQuantity(new BigDecimal("5"));

        when(expenseRepository.findById(EXPENSE_ID)).thenReturn(Optional.of(expense));
        when(cashboxRepository.findFirstByBranchIdAndType(BRANCH_ID, "depense"))
                .thenReturn(Optional.of(caisse));
        when(cashboxOperationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(expenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(expenceDetailRepository.findByExpenseId(EXPENSE_ID)).thenReturn(List.of(detail));
        when(articleRepository.findFirstByBranchIdAndName(BRANCH_ID, "Gants"))
                .thenReturn(Optional.of(article));
        when(articleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateStock(EXPENSE_ID, BRANCH_ID, USER_ID);

        // Caisse débitée
        assertThat(caisse.getBalance()).isEqualByComparingTo(new BigDecimal("1500.00"));

        // Stock article augmenté
        assertThat(article.getQuantity()).isEqualByComparingTo(new BigDecimal("15"));

        // Mouvement d'entrée enregistré
        ArgumentCaptor<Movement> movCaptor = ArgumentCaptor.forClass(Movement.class);
        verify(movementRepository).save(movCaptor.capture());
        assertThat(movCaptor.getValue().getQuantity()).isEqualByComparingTo(new BigDecimal("10"));

        // Dépense paid=2
        ArgumentCaptor<Expense> expCaptor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(expCaptor.capture());
        assertThat(expCaptor.getValue().getPaid()).isEqualTo(2);
    }

    @Test
    @DisplayName("updateStock - dépense déjà payée (paid=1) → ne débite pas à nouveau la caisse")
    void updateStock_alreadyPaid_skipsDebitAndSetsStockOnly() {
        Expense expense = buildExpense(1, new BigDecimal("400.00"));

        ExpenceDetail detail = new ExpenceDetail();
        detail.setArticleName("Seringues");
        detail.setQuantity(new BigDecimal("20"));

        Article article = new Article();
        article.setQuantity(new BigDecimal("0"));

        when(expenseRepository.findById(EXPENSE_ID)).thenReturn(Optional.of(expense));
        when(expenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(expenceDetailRepository.findByExpenseId(EXPENSE_ID)).thenReturn(List.of(detail));
        when(articleRepository.findFirstByBranchIdAndName(BRANCH_ID, "Seringues"))
                .thenReturn(Optional.of(article));
        when(articleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(movementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateStock(EXPENSE_ID, BRANCH_ID, USER_ID);

        // La caisse ne doit pas être consultée (pas de débit)
        verify(cashboxRepository, never()).findFirstByBranchIdAndType(any(), any());

        // Stock mis à jour
        assertThat(article.getQuantity()).isEqualByComparingTo(new BigDecimal("20"));

        // paid=2
        ArgumentCaptor<Expense> expCaptor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseRepository).save(expCaptor.capture());
        assertThat(expCaptor.getValue().getPaid()).isEqualTo(2);
    }

    @Test
    @DisplayName("updateStock - article non trouvé par nom → pas de mouvement créé")
    void updateStock_articleNotFound_noMovementCreated() {
        Expense expense = buildExpense(1, new BigDecimal("100.00"));

        ExpenceDetail detail = new ExpenceDetail();
        detail.setArticleName("Article inconnu");
        detail.setQuantity(new BigDecimal("5"));

        when(expenseRepository.findById(EXPENSE_ID)).thenReturn(Optional.of(expense));
        when(expenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(expenceDetailRepository.findByExpenseId(EXPENSE_ID)).thenReturn(List.of(detail));
        when(articleRepository.findFirstByBranchIdAndName(BRANCH_ID, "Article inconnu"))
                .thenReturn(Optional.empty());

        service.updateStock(EXPENSE_ID, BRANCH_ID, USER_ID);

        verify(movementRepository, never()).save(any());
        verify(articleRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateStock - ligne sans articleName → ignorée (pas de mouvement)")
    void updateStock_detailWithNullArticleName_skipped() {
        Expense expense = buildExpense(1, new BigDecimal("50.00"));

        ExpenceDetail detail = new ExpenceDetail();
        detail.setArticleName(null);
        detail.setQuantity(new BigDecimal("2"));

        when(expenseRepository.findById(EXPENSE_ID)).thenReturn(Optional.of(expense));
        when(expenseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(expenceDetailRepository.findByExpenseId(EXPENSE_ID)).thenReturn(List.of(detail));

        service.updateStock(EXPENSE_ID, BRANCH_ID, USER_ID);

        verify(articleRepository, never()).findFirstByBranchIdAndName(any(), any());
        verify(movementRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateStock - dépense introuvable → ResourceNotFoundException")
    void updateStock_expenseNotFound_throwsResourceNotFoundException() {
        when(expenseRepository.findById(EXPENSE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStock(EXPENSE_ID, BRANCH_ID, USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
