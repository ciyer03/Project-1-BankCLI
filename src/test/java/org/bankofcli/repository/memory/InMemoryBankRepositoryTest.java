package org.bankofcli.repository.memory;

import java.math.BigDecimal;
import java.util.List;

import org.bankofcli.exceptions.BankingException;
import org.bankofcli.model.Account;
import org.bankofcli.model.Transaction;
import org.bankofcli.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InMemoryBankRepositoryTest {
    private InMemoryBankRepository repository;

    @BeforeEach
    void setup() {
        repository = new InMemoryBankRepository();
        repository.create(new Account("", "", "Alice1-", 1234));
        repository.create(new Account("", "", "Bobby2#", 4321));
    }

    @Test
    void createPersistsAccountWithZeroStartingBalance() {
        repository.create(new Account("Carol", "Diaz", "Carol3$", 5678));
        assertEquals(new BigDecimal("0.00"), repository.getBalance("Carol3$"));
        assertTrue(repository.findById("Carol3$").isPresent());
    }

    @Test
    void createRejectsBlankOrDuplicateAccountId() {
        assertThrows(BankingException.class, () -> repository.create(new Account("Carol", "Diaz", "", 5678)));
        assertThrows(BankingException.class, () -> repository.create(new Account("Carol", "Diaz", null, 5678)));
        assertThrows(BankingException.class, () -> repository.create(new Account("Someone", "Else", "Alice1-", 9999)));
    }

    @Test
    void findByIdReturnsPersistedAccountDetails() {
        Account found = repository.findById("Alice1-").orElseThrow();
        assertEquals("Alice1-", found.getAccountId());
        assertEquals(1234, found.getPIN());
    }

    @Test
    void findByIdReturnsEmptyForMissingAccount() {
        assertTrue(repository.findById("Missing1-").isEmpty());
    }

    @Test
    void existsByIdDistinguishesCreatedFromMissingAccountsWithoutThrowing() {
        assertTrue(repository.existsById("Alice1-"));
        assertFalse(repository.existsById("Missing1-"));
        assertFalse(repository.existsById(null));
    }

    @Test
    void withdrawReducesBalanceAndRecordsTransaction() {
        repository.deposit("Alice1-", new BigDecimal("20.00"));
        repository.withdraw("Alice1-", new BigDecimal("5.00"));
        assertEquals(new BigDecimal("15.00"), repository.getBalance("Alice1-"));
        Transaction recorded = repository.getRecentTransactions("Alice1-", 1).getFirst();
        assertEquals(TransactionType.WITHDRAW, recorded.getType());
    }

    @Test
    void withdrawRejectsInsufficientBalanceAndBadAmounts() {
        repository.deposit("Alice1-", BigDecimal.TEN);
        assertThrows(BankingException.class, () -> repository.withdraw("Alice1-", new BigDecimal("10.01")));
        for (BigDecimal amount : new BigDecimal[] {null, BigDecimal.ZERO, new BigDecimal("-1"), new BigDecimal("1.001")}) {
            assertThrows(BankingException.class, () -> repository.withdraw("Alice1-", amount));
        }
        assertEquals(new BigDecimal("10.00"), repository.getBalance("Alice1-"));
    }

    @Test
    void withdrawRejectsMissingAccount() {
        assertThrows(BankingException.class, () -> repository.withdraw("Missing1-", BigDecimal.ONE));
        assertThrows(BankingException.class, () -> repository.withdraw(null, BigDecimal.ONE));
    }

    @Test
    void transferMovesFundsAndRecordsBothSides() {
        repository.deposit("Alice1-", BigDecimal.TEN);
        repository.transfer("Alice1-", "Bobby2#", new BigDecimal("4.00"));
        assertEquals(new BigDecimal("6.00"), repository.getBalance("Alice1-"));
        assertEquals(new BigDecimal("4.00"), repository.getBalance("Bobby2#"));
        assertEquals(TransactionType.TRANSFER_OUT, repository.getRecentTransactions("Alice1-", 1).getFirst().getType());
        assertEquals(TransactionType.TRANSFER_IN, repository.getRecentTransactions("Bobby2#", 1).getFirst().getType());
    }

    @Test
    void transferRejectsSameAccountMissingAccountsAndBadAmounts() {
        repository.deposit("Alice1-", BigDecimal.TEN);
        assertThrows(BankingException.class, () -> repository.transfer("Alice1-", "Alice1-", BigDecimal.ONE));
        assertThrows(BankingException.class, () -> repository.transfer("Missing1-", "Alice1-", BigDecimal.ONE));
        assertThrows(BankingException.class, () -> repository.transfer("Alice1-", "Missing1-", BigDecimal.ONE));
        assertThrows(BankingException.class, () -> repository.transfer("Alice1-", "Bobby2#", BigDecimal.ZERO));
        assertEquals(new BigDecimal("10.00"), repository.getBalance("Alice1-"));
    }

    @Test
    void getRecentTransactionsReturnsLatestFirstAndIsImmutable() {
        repository.deposit("Alice1-", BigDecimal.ONE);
        repository.deposit("Alice1-", new BigDecimal("2.00"));
        List<Transaction> history = repository.getRecentTransactions("Alice1-", 50);
        assertEquals(2, history.size());
        assertEquals(new BigDecimal("2.00"), history.getFirst().getAmount());
        assertEquals(new BigDecimal("1.00"), history.getLast().getAmount());
        assertThrows(UnsupportedOperationException.class, history::clear);
    }

    @Test
    void getRecentTransactionsRejectsNegativeLimitButAllowsZero() {
        repository.deposit("Alice1-", BigDecimal.ONE);
        assertEquals(0, repository.getRecentTransactions("Alice1-", 0).size());
        assertThrows(IllegalArgumentException.class, () -> repository.getRecentTransactions("Alice1-", -1));
        assertThrows(BankingException.class, () -> repository.getRecentTransactions("Missing1-", 10));
    }

    @Test
    void repositoryRechecksFundsAndDestinationBeforeMutation() {
        repository.deposit("Alice1-", BigDecimal.TEN);
        assertThrows(BankingException.class, () -> repository.transfer("Alice1-", "Missing1-", BigDecimal.ONE));
        assertThrows(BankingException.class, () -> repository.transfer("Alice1-", "Bobby2#", new BigDecimal("11")));
        assertEquals(new BigDecimal("10.00"), repository.getBalance("Alice1-"));
        assertEquals(1, repository.getRecentTransactions("Alice1-", 10).size());
    }
}
