package org.bankofcli.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

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
    private String aliceId;
    private String bobbyId;

    @BeforeEach
    void setup() {
        database = SqliteTestDatabase.create();
        AccountRepository accountRepository = new SQLiteAccountRepository(database.connections);
        TransactionRepository transactionRepository = new SQLiteTransactionRepository(database.connections);
        accounts = new AccountServiceImpl(accountRepository);
        transactions = new TransactionServiceImpl(accountRepository, transactionRepository);
        aliceId = UUID.randomUUID().toString();
        bobbyId = UUID.randomUUID().toString();
        accountRepository.create(new Account("Alice", "Smith", aliceId, 1234));
        accountRepository.create(new Account("Bobby", "Jones", bobbyId, 4321));
    }

    @AfterEach
    void teardown() {
        database.close();
    }

    @Test
    void depositIncreasesBalanceAndRecordsTransaction() {
        transactions.deposit(aliceId, new BigDecimal("12.34"));
        assertEquals(new BigDecimal("12.34"), accounts.getBalance(aliceId));
        Transaction recorded = transactions.getRecentTransactions(aliceId, 1).getFirst();
        assertEquals(TransactionType.DEPOSIT, recorded.getType());
        assertEquals(new BigDecimal("12.34"), recorded.getAmount());
    }

    @Test
    void depositRejectsMissingAccount() {
        assertThrows(AccountDoesNotExistException.class,
                () -> transactions.deposit(UUID.randomUUID().toString(), BigDecimal.ONE));
    }

    @Test
    void withdrawReducesBalanceAndRecordsTransaction() {
        transactions.deposit(aliceId, new BigDecimal("20.00"));
        assertDoesNotThrow(() -> transactions.withdraw(aliceId, new BigDecimal("5.00")));
        assertEquals(new BigDecimal("15.00"), accounts.getBalance(aliceId));
        Transaction recorded = transactions.getRecentTransactions(aliceId, 1).getFirst();
        assertEquals(TransactionType.WITHDRAW, recorded.getType());
        assertEquals(new BigDecimal("5.00"), recorded.getAmount());
    }

    @Test
    void withdrawRejectsInsufficientBalance() {
        transactions.deposit(aliceId, BigDecimal.TEN);
        assertThrows(InsufficientBalanceException.class, () -> transactions.withdraw(aliceId, new BigDecimal("10.01")));
        assertEquals(new BigDecimal("10.00"), accounts.getBalance(aliceId));
    }

    @Test
    void transferMovesFundsBetweenAccounts() {
        transactions.deposit(aliceId, BigDecimal.TEN);
        assertDoesNotThrow(() -> transactions.transfer(aliceId, bobbyId, new BigDecimal("4.00")));
        assertEquals(new BigDecimal("6.00"), accounts.getBalance(aliceId));
        assertEquals(new BigDecimal("4.00"), accounts.getBalance(bobbyId));
    }

    @Test
    void transferRejectsInsufficientBalance() {
        transactions.deposit(aliceId, BigDecimal.TEN);
        assertThrows(InsufficientBalanceException.class,
                () -> transactions.transfer(aliceId, bobbyId, new BigDecimal("10.01")));
        assertEquals(new BigDecimal("10.00"), accounts.getBalance(aliceId));
        assertEquals(new BigDecimal("0.00"), accounts.getBalance(bobbyId));
    }

    @Test
    void getRecentTransactionsReturnsLatestFirst() {
        transactions.deposit(aliceId, BigDecimal.ONE);
        transactions.deposit(aliceId, new BigDecimal("2.00"));
        List<Transaction> history = transactions.getRecentTransactions(aliceId, 10);
        assertEquals(2, history.size());
        assertEquals(new BigDecimal("2.00"), history.getFirst().getAmount());
        assertEquals(new BigDecimal("1.00"), history.getLast().getAmount());
    }

    @Test
    void getRecentTransactionsRejectsMissingAccount() {
        assertThrows(AccountDoesNotExistException.class,
                () -> transactions.getRecentTransactions(UUID.randomUUID().toString(), 10));
    }
}
