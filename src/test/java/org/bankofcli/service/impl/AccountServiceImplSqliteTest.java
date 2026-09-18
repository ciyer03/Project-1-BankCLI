package org.bankofcli.service.impl;

import java.math.BigDecimal;
import java.util.UUID;

import org.bankofcli.exceptions.BankingException;
import org.bankofcli.model.Account;
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

class AccountServiceImplSqliteTest {
    private SqliteTestDatabase database;
    private AccountService accounts;
    private TransactionService transactions;
    private String aliceId;

    @BeforeEach
    void setup() {
        database = SqliteTestDatabase.create();
        AccountRepository accountRepository = new SQLiteAccountRepository(database.connections);
        TransactionRepository transactionRepository = new SQLiteTransactionRepository(database.connections);
        accounts = new AccountServiceImpl(accountRepository);
        transactions = new TransactionServiceImpl(accountRepository, transactionRepository);
        aliceId = UUID.randomUUID().toString();
        accountRepository.create(new Account("Alice", "Smith", aliceId, 1234));
    }

    @AfterEach
    void teardown() {
        database.close();
    }

    @Test
    void getBalanceReturnsZeroForNewAccountAndReflectsDeposit() {
        assertEquals(new BigDecimal("0.00"), accounts.getBalance(aliceId));
        transactions.deposit(aliceId, new BigDecimal("15.50"));
        assertEquals(new BigDecimal("15.50"), accounts.getBalance(aliceId));
    }

    @Test
    void getBalanceRejectsMissingOrBlankAccount() {
        assertThrows(BankingException.class, () -> accounts.getBalance(UUID.randomUUID().toString()));
        assertThrows(BankingException.class, () -> accounts.getBalance(""));
        assertThrows(BankingException.class, () -> accounts.getBalance(null));
    }
}
