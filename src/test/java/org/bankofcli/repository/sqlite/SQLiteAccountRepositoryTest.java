package org.bankofcli.repository.sqlite;

import java.math.BigDecimal;
import java.util.UUID;

import org.bankofcli.exceptions.AccountDoesNotExistException;
import org.bankofcli.model.Account;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SQLiteAccountRepositoryTest {
    private SqliteTestDatabase database;
    private SQLiteAccountRepository repository;
    private String aliceId;

    @BeforeEach
    void setup() {
        database = SqliteTestDatabase.create();
        repository = new SQLiteAccountRepository(database.connections);
        aliceId = UUID.randomUUID().toString();
        repository.create(new Account("Alice", "Smith", aliceId, 1234));
    }

    @AfterEach
    void teardown() {
        database.close();
    }

    @Test
    void createPersistsAccountWithZeroStartingBalance() {
        assertEquals(new BigDecimal("0.00"), repository.getBalance(aliceId));
        assertTrue(repository.findById(aliceId).isPresent());
    }

    @Test
    void createRejectsDuplicateIdAndInvalidPin() {
        assertThrows(IllegalStateException.class,
                () -> repository.create(new Account("Someone", "Else", aliceId, 4321)));
        assertThrows(IllegalStateException.class,
                () -> repository.create(new Account("Bob", "Jones", UUID.randomUUID().toString(), -1)));
        assertThrows(IllegalStateException.class,
                () -> repository.create(new Account("Bob", "Jones", UUID.randomUUID().toString(), 10000)));
    }

    @Test
    void findByIdReturnsPersistedAccountDetails() {
        Account found = repository.findById(aliceId).orElseThrow();
        assertEquals("Alice", found.getFirstName());
        assertEquals("Smith", found.getLastName());
        assertEquals(1234, found.getPIN());
    }

    @Test
    void findByIdReturnsEmptyForMissingAccount() {
        assertTrue(repository.findById(UUID.randomUUID().toString()).isEmpty());
    }

    @Test
    void existsByIdDistinguishesCreatedFromMissingAccounts() {
        assertTrue(repository.existsById(aliceId));
        assertFalse(repository.existsById(UUID.randomUUID().toString()));
    }

    @Test
    void getBalanceRejectsMissingAccountDirectlyWithAccountDoesNotExistException() {
        assertThrows(AccountDoesNotExistException.class,
                () -> repository.getBalance(UUID.randomUUID().toString()));
    }
}
