package org.bankofcli.service.impl;

import java.math.BigDecimal;
import java.util.UUID;

import org.bankofcli.exceptions.BankingException;
import org.bankofcli.model.Account;
import org.bankofcli.repository.AccountRepository;
import org.bankofcli.repository.sqlite.SQLiteAccountRepository;
import org.bankofcli.repository.sqlite.SqliteTestDatabase;
import org.bankofcli.service.AccountService;
import org.bankofcli.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthServiceImplSqliteTest {
    private SqliteTestDatabase database;
    private AuthService auth;
    private AccountService accounts;

    @BeforeEach
    void setup() {
        database = SqliteTestDatabase.create();
        AccountRepository repository = new SQLiteAccountRepository(database.connections);
        auth = new AuthServiceImpl(repository);
        accounts = new AccountServiceImpl(repository);
    }

    @AfterEach
    void teardown() {
        database.close();
    }

    @Test
    void registrationPersistsAccountWithCompactUuid() {
        Account registered = auth.register("Alice", "Smith", 1234);
        assertTrue(registered.getAccountId().matches("[A-Za-z0-9_-]{22}"));
        assertEquals(new BigDecimal("0.00"), accounts.getBalance(registered.getAccountId()));
    }

    @Test
    void registrationRejectsBlankNamesAndInvalidPins() {
        assertThrows(BankingException.class, () -> auth.register(null, "Smith", 1234));
        assertThrows(BankingException.class, () -> auth.register("Alice", " ", 1234));
        assertThrows(BankingException.class, () -> auth.register("Alice", "Smith", -1));
        assertThrows(BankingException.class, () -> auth.register("Alice", "Smith", 10000));
    }

    @Test
    void loginSucceedsAndReturnsMatchingAccountId() {
        Account registered = auth.register("Alice", "Smith", 1234);
        Account loggedIn = auth.login(registered.getAccountId(), 1234);
        assertEquals(registered.getAccountId(), loggedIn.getAccountId());
    }

    @Test
    void loginRejectsUnknownAccountAndWrongPinWithSameMessage() {
        Account registered = auth.register("Alice", "Smith", 1234);
        BankingException wrong = assertThrows(BankingException.class,
                () -> auth.login(registered.getAccountId(), 4321));
        BankingException unknown = assertThrows(BankingException.class,
                () -> auth.login(UUID.randomUUID().toString(), 1234));
        assertEquals(wrong.getMessage(), unknown.getMessage());
    }
}
