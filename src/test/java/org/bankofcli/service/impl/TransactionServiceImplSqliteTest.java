package org.bankofcli.service.impl;

import java.math.BigDecimal;
import java.util.List;

import org.bankofcli.exceptions.AccountDoesNotExistException;
import org.bankofcli.exceptions.InsufficientBalanceException;
import org.bankofcli.model.Account;
import org.bankofcli.model.Transaction;
import org.bankofcli.model.TransactionType;
import org.bankofcli.repository.AccountRepository;
import org.bankofcli.repository.TransactionRepository;
import org.bankofcli.repository.sqlite.SQLiteAccountRepository;
import org.bankofcli.repository.sqlite.SQLiteTransactionRepository;
import org.bankofcli.repository.sqlite.SqliteTestDatabase;
import org.bankofcli.service.AccountService;
import org.bankofcli.service.TransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TransactionServiceImplSqliteTest {
    private SqliteTestDatabase database;
    private AccountService accounts;
    private TransactionService transactions;

    @BeforeEach
    void setup() {
        database = SqliteTestDatabase.create();
        AccountRepository accountRepository = new SQLiteAccountRepository(database.connections);
        TransactionRepository transactionRepository = new SQLiteTransactionRepository(database.connections);
        accounts = new AccountServiceImpl(accountRepository);
        transactions = new TransactionServiceImpl(accountRepository, transactionRepository);
        accountRepository.create(new Account("Alice", "Smith", "Alice1-", 1234));
        accountRepository.create(new Account("Bobby", "Jones", "Bobby2#", 4321));
    }

    @AfterEach
    void teardown() {
        database.close();
    }

    @Test
    void depositIncreasesBalanceAndRecordsTransaction() {
        transactions.deposit("Alice1-", new BigDecimal("12.34"));
        assertEquals(new BigDecimal("12.34"), accounts.getBalance("Alice1-"));
        Transaction recorded = transactions.getRecentTransactions("Alice1-", 1).getFirst();
        assertEquals(TransactionType.DEPOSIT, recorded.getType());
        assertEquals(new BigDecimal("12.34"), recorded.getAmount());
    }

    @Test
    void depositRejectsMissingAccount() {
        assertThrows(AccountDoesNotExistException.class, () -> transactions.deposit("Missing1-", BigDecimal.ONE));
    }

    @Test
    void withdrawReducesBalanceAndRecordsTransaction() {
        transactions.deposit("Alice1-", new BigDecimal("20.00"));
        assertDoesNotThrow(() -> transactions.withdraw("Alice1-", new BigDecimal("5.00")));
        assertEquals(new BigDecimal("15.00"), accounts.getBalance("Alice1-"));
        Transaction recorded = transactions.getRecentTransactions("Alice1-", 1).getFirst();
        assertEquals(TransactionType.WITHDRAW, recorded.getType());
        assertEquals(new BigDecimal("5.00"), recorded.getAmount());
    }

    @Test
    void withdrawRejectsInsufficientBalance() {
        transactions.deposit("Alice1-", BigDecimal.TEN);
        assertThrows(InsufficientBalanceException.class, () -> transactions.withdraw("Alice1-", new BigDecimal("10.01")));
        assertEquals(new BigDecimal("10.00"), accounts.getBalance("Alice1-"));
    }

    @Test
    void transferMovesFundsBetweenAccounts() {
        transactions.deposit("Alice1-", BigDecimal.TEN);
        assertDoesNotThrow(() -> transactions.transfer("Alice1-", "Bobby2#", new BigDecimal("4.00")));
        assertEquals(new BigDecimal("6.00"), accounts.getBalance("Alice1-"));
        assertEquals(new BigDecimal("4.00"), accounts.getBalance("Bobby2#"));
    }

    @Test
    void transferRejectsInsufficientBalance() {
        transactions.deposit("Alice1-", BigDecimal.TEN);
        assertThrows(InsufficientBalanceException.class,
                () -> transactions.transfer("Alice1-", "Bobby2#", new BigDecimal("10.01")));
        assertEquals(new BigDecimal("10.00"), accounts.getBalance("Alice1-"));
        assertEquals(new BigDecimal("0.00"), accounts.getBalance("Bobby2#"));
    }

    @Test
    void getRecentTransactionsReturnsLatestFirst() {
        transactions.deposit("Alice1-", BigDecimal.ONE);
        transactions.deposit("Alice1-", new BigDecimal("2.00"));
        List<Transaction> history = transactions.getRecentTransactions("Alice1-", 10);
        assertEquals(2, history.size());
        assertEquals(new BigDecimal("2.00"), history.getFirst().getAmount());
        assertEquals(new BigDecimal("1.00"), history.getLast().getAmount());
    }

    @Test
    void getRecentTransactionsRejectsMissingAccount() {
        assertThrows(AccountDoesNotExistException.class, () -> transactions.getRecentTransactions("Missing1-", 10));
    }
}
