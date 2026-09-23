package org.bankofcli.service.impl;

import java.math.BigDecimal;
import java.util.UUID;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.bankofcli.exceptions.BankingException;
import org.bankofcli.model.Account;
import org.bankofcli.repository.memory.InMemoryBankRepository;
import org.bankofcli.service.AccountService;
import org.bankofcli.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.*;

class AuthServiceImplTest {
    private InMemoryBankRepository repository;
    private AuthService auth;
    private AccountService accounts;

    @BeforeEach
    void setup() {
        repository = new InMemoryBankRepository();
        auth = new AuthServiceImpl(repository);
        accounts = new AccountServiceImpl(repository);
        repository.create(new Account("", "", "Alice1-", 1234));
        repository.create(new Account("", "", "Bobby2#", 4321));
    }

    @Test
    void registrationRequiresBothNamesAndTrimsWhitespace() {
        for (String name : new String[] {null, "", " \t "}) {
            assertThrows(BankingException.class, () -> auth.register(name, "Smith", 1234));
            assertThrows(BankingException.class, () -> auth.register("Alice", name, 1234));
        }
        Account account = auth.register("  Anne Marie  ", "  O'Neill-Smith  ", 1234);
        Account saved = repository.findById(account.getAccountId()).orElseThrow();
        assertEquals("Anne Marie", saved.getFirstName());
        assertEquals("O'Neill-Smith", saved.getLastName());
    }

    @Test
    void registrationGeneratesUniqueCompactUuidIds() {
        Account first = auth.register("Alice", "Smith", 1234);
        Account second = auth.register("Alice", "Smith", 1234);
        assertTrue(first.getAccountId().matches("[A-Za-z0-9_-]{22}"));
        var bytes = java.nio.ByteBuffer.wrap(java.util.Base64.getUrlDecoder().decode(first.getAccountId()));
        UUID uuid = new UUID(bytes.getLong(), bytes.getLong());
        assertEquals(4, uuid.version());
        assertEquals(2, uuid.variant());
        assertNotEquals(first.getAccountId(), second.getAccountId());
        assertEquals(first.getAccountId(), auth.login(first.getAccountId(), 1234).getAccountId());
        assertEquals(new BigDecimal("0.00"), accounts.getBalance(first.getAccountId()));
    }

    @Test
    void acceptsNumericValuesOfLeadingZeroPins() {
        for (int pin : new int[] {0, 1, 123, 999}) {
            String id = auth.register("Alice", "Smith", pin).getAccountId();
            assertEquals(id, auth.login(id, pin).getAccountId());
            assertThrows(BankingException.class, () -> auth.login(id, 9999));
        }
    }

    @Test
    void registrationStartsAtZeroAndAuthenticates() {
        assertEquals("Alice1-", auth.login("Alice1-", 1234).getAccountId());
        assertEquals(new BigDecimal("0.00"), accounts.getBalance("Alice1-"));
    }

    @Test
    void rejectsInvalidPins() {
        for (int pin : new int[] {-1, 10000}) {
            assertThrows(BankingException.class, () -> auth.register("Alice", "Smith", pin));
        }
    }

    @Test
    void unknownAccountAndWrongPinHaveSameError() {
        BankingException wrong = assertThrows(BankingException.class, () -> auth.login("Alice1-", 1111));
        BankingException unknown = assertThrows(BankingException.class, () -> auth.login("Unknown1-", 1234));
        assertEquals(wrong.getMessage(), unknown.getMessage());
        assertThrows(BankingException.class, () -> auth.login(null, 1234));
    }

    @Test
    void authenticationLogsOutcomesWithoutCredentials() {
        Logger logger = (Logger) LoggerFactory.getLogger(AuthServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            AuthService isolatedAuth = new AuthServiceImpl(new InMemoryBankRepository());
            String id = isolatedAuth.register("Alice", "Smith", 6789).getAccountId();
            isolatedAuth.login(id, 6789);
            assertThrows(BankingException.class, () -> isolatedAuth.login(id, 9876));
            java.util.List<String> messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertTrue(messages.contains("Registration succeeded!"));
            assertTrue(messages.contains("Login succeeded!"));
            assertTrue(messages.contains("Login failed"));
            assertTrue(messages.stream().noneMatch(m ->
                    m.contains("6789") || m.contains("9876") || m.contains("Private_user1")));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
