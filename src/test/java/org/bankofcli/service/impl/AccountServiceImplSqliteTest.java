package org.bankofcli.service.impl;

import java.math.BigDecimal;

import org.bankofcli.exceptions.BankingException;
import org.bankofcli.model.Account;
import org.bankofcli.repository.AccountRepository;
import org.bankofcli.repository.TransactionRepository;
import org.bankofcli.repository.sqlite.SQLiteAccountRepository;
import org.bankofcli.repository.sqlite.SQLiteTransactionRepository;
import org.bankofcli.repository.sqlite.SqliteTestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccountServiceImplSqliteTest {
    private SqliteTestDatabase database;
    private AccountServiceImpl accounts;
    private TransactionServiceImpl transactions;

    @BeforeEach
    void setup() {
        database = SqliteTestDatabase.create();
        AccountRepository accountRepository = new SQLiteAccountRepository(database.connections);
        TransactionRepository transactionRepository = new SQLiteTransactionRepository(database.connections);
        accounts = new AccountServiceImpl(accountRepository);
        transactions = new TransactionServiceImpl(accountRepository, transactionRepository);
        accountRepository.create(new Account("Alice", "Smith", "Alice1-", 1234));
    }

    @AfterEach
    void teardown() {
        database.close();
    }

    @Test
    void getBalanceReturnsZeroForNewAccountAndReflectsDeposit() {
        assertEquals(new BigDecimal("0.00"), accounts.getBalance("Alice1-"));
        transactions.deposit("Alice1-", new BigDecimal("15.50"));
        assertEquals(new BigDecimal("15.50"), accounts.getBalance("Alice1-"));
    }

    @Test
    void getBalanceRejectsMissingOrBlankAccount() {
        assertThrows(BankingException.class, () -> accounts.getBalance("Missing1-"));
        assertThrows(BankingException.class, () -> accounts.getBalance(""));
        assertThrows(BankingException.class, () -> accounts.getBalance(null));
    }
}
