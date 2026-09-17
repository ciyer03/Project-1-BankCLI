package org.bankofcli.repository.sqlite;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.bankofcli.exceptions.AccountDoesNotExistException;
import org.bankofcli.exceptions.BankingException;
import org.bankofcli.model.Account;
import org.bankofcli.model.Transaction;
import org.bankofcli.model.TransactionType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Calling this repository directly with an amount larger than the balance is 
 * not covered, since that pre-check belongs to the service layer.
 */
class SQLiteTransactionRepositoryTest {
    private SqliteTestDatabase database;
    private SQLiteAccountRepository accounts;
    private SQLiteTransactionRepository transactions;
    private String aliceId;
    private String bobbyId;

    @BeforeEach
    void setup() {
        database = SqliteTestDatabase.create();
        accounts = new SQLiteAccountRepository(database.connections);
        transactions = new SQLiteTransactionRepository(database.connections);
        aliceId = UUID.randomUUID().toString();
        bobbyId = UUID.randomUUID().toString();
        accounts.create(new Account("Alice", "Smith", aliceId, 1234));
        accounts.create(new Account("Bobby", "Jones", bobbyId, 4321));
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
    void depositRejectsMissingAccountAndBadAmounts() {
        assertThrows(AccountDoesNotExistException.class,
                () -> transactions.deposit(UUID.randomUUID().toString(), BigDecimal.ONE));
        for (BigDecimal amount : new BigDecimal[] {null, BigDecimal.ZERO, new BigDecimal("-1"), new BigDecimal("1.001")}) {
            assertThrows(BankingException.class, () -> transactions.deposit(aliceId, amount));
        }
        assertEquals(new BigDecimal("0.00"), accounts.getBalance(aliceId));
    }

    @Test
    void withdrawReducesBalanceAndRecordsTransaction() {
        transactions.deposit(aliceId, new BigDecimal("20.00"));
        transactions.withdraw(aliceId, new BigDecimal("5.00"));
        assertEquals(new BigDecimal("15.00"), accounts.getBalance(aliceId));
        Transaction recorded = transactions.getRecentTransactions(aliceId, 1).getFirst();
        assertEquals(TransactionType.WITHDRAW, recorded.getType());
        assertEquals(new BigDecimal("5.00"), recorded.getAmount());
    }

    @Test
    void withdrawRejectsMissingAccount() {
        assertThrows(BankingException.class,
                () -> transactions.withdraw(UUID.randomUUID().toString(), BigDecimal.ONE));
    }

    @Test
    void transferMovesFundsAndRecordsBothSides() throws Exception {
        transactions.deposit(aliceId, BigDecimal.TEN);
        transactions.transfer(aliceId, bobbyId, new BigDecimal("4.00"));
        assertEquals(new BigDecimal("6.00"), accounts.getBalance(aliceId));
        assertEquals(new BigDecimal("4.00"), accounts.getBalance(bobbyId));
        assertEquals(TransactionType.TRANSFER_IN, transactions.getRecentTransactions(bobbyId, 1).getFirst().getType());
    }

    @Test
    void transferRejectsWhenNeitherAccountExists() {
        assertThrows(BankingException.class,
                () -> transactions.transfer(UUID.randomUUID().toString(), UUID.randomUUID().toString(), BigDecimal.ONE));
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
    void getRecentTransactionsReturnsEmptyForAccountWithNoHistory() {
        assertTrue(transactions.getRecentTransactions(aliceId, 10).isEmpty());
    }

    @Test
    void getRecentTransactionsReturnsEmptyForMissingAccountInsteadOfThrowing() {
        assertTrue(transactions.getRecentTransactions(UUID.randomUUID().toString(), 10).isEmpty());
    }
}
