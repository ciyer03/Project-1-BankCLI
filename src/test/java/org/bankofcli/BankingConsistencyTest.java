package org.bankofcli;

import java.math.BigDecimal;

import org.bankofcli.exceptions.AccountDoesNotExistException;
import org.bankofcli.exceptions.BankingException;
import org.bankofcli.exceptions.InsufficientBalanceException;
import org.bankofcli.model.Account;
import org.bankofcli.model.TransactionType;
import org.bankofcli.repository.memory.InMemoryBankRepository;
import org.bankofcli.service.AccountService;
import org.bankofcli.service.TransactionService;
import org.bankofcli.service.impl.AccountServiceImpl;
import org.bankofcli.service.impl.TransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BankingConsistencyTest {
    private InMemoryBankRepository repository;
    private AccountService accounts;
    private TransactionService transactions;

    @BeforeEach
    void setup() {
        repository = new InMemoryBankRepository();
        accounts = new AccountServiceImpl(repository);
        transactions = new TransactionServiceImpl(repository, repository);
        repository.create(new Account("", "", "Alice1-", 1234));
        repository.create(new Account("", "", "Bobby2#", 4321));
    }

    @Test
    void depositAndWithdrawalUseExactCents() {
        transactions.deposit("Alice1-", new BigDecimal("0.10"));
        transactions.deposit("Alice1-", new BigDecimal("0.20"));
        assertEquals(new BigDecimal("0.30"), accounts.getBalance("Alice1-"));
        assertEquals(TransactionType.DEPOSIT, transactions.getRecentTransactions("Alice1-", 10).getFirst().getType());
        repository.withdraw("Alice1-", new BigDecimal("0.30"));
        assertEquals(new BigDecimal("0.00"), accounts.getBalance("Alice1-"));
        assertEquals(TransactionType.WITHDRAW, transactions.getRecentTransactions("Alice1-", 10).getFirst().getType());
    }

    @Test
    void everyMutationRejectsInvalidAmountsWithoutChanges() {
        for (BigDecimal amount : new BigDecimal[] {
            null, BigDecimal.ZERO, new BigDecimal("-1"), new BigDecimal("1.001")
        }) {
            assertThrows(BankingException.class, () -> transactions.deposit("Alice1-", amount));
            assertThrows(BankingException.class, () -> repository.withdraw("Alice1-", amount));
            assertThrows(IllegalArgumentException.class, () -> transactions.transfer("Alice1-", "Bobby2#", amount));
        }
        assertEquals(new BigDecimal("0.00"), accounts.getBalance("Alice1-"));
        assertTrue(transactions.getRecentTransactions("Alice1-", 10).isEmpty());
        assertTrue(transactions.getRecentTransactions("Bobby2#", 10).isEmpty());
    }

    @Test
    void rejectsMissingAccountsAcrossOperations() {
        assertThrows(BankingException.class, () -> accounts.getBalance("Missing1-"));
        assertThrows(AccountDoesNotExistException.class, () -> transactions.deposit("Missing1-", BigDecimal.ONE));
        assertThrows(BankingException.class, () -> repository.withdraw("Missing1-", BigDecimal.ONE));
        assertThrows(AccountDoesNotExistException.class, () -> transactions.transfer("Missing1-", "Alice1-", BigDecimal.ONE));
        assertThrows(AccountDoesNotExistException.class, () -> transactions.getRecentTransactions("Missing1-", 10));
    }

    @Test
    void rejectedTransfersAndOverdraftsLeaveBalancesAndHistoryIntact() {
        transactions.deposit("Alice1-", BigDecimal.TEN);
        assertThrows(BankingException.class, () -> repository.withdraw("Alice1-", new BigDecimal("10.01")));
        assertThrows(InsufficientBalanceException.class, () -> transactions.transfer("Alice1-", "Bobby2#", new BigDecimal("11")));
        assertThrows(AccountDoesNotExistException.class, () -> transactions.transfer("Alice1-", "Missing1-", BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> transactions.transfer("Alice1-", "Alice1-", BigDecimal.ONE));
        assertEquals(new BigDecimal("10.00"), accounts.getBalance("Alice1-"));
        assertEquals(new BigDecimal("0.00"), accounts.getBalance("Bobby2#"));
        assertEquals(1, transactions.getRecentTransactions("Alice1-", 10).size());
        assertTrue(transactions.getRecentTransactions("Bobby2#", 10).isEmpty());
    }
}
