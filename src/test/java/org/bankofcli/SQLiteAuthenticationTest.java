package org.bankofcli;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;
import org.bankofcli.exceptions.BankingException;
import org.bankofcli.repository.sqlite.SQLiteAccountRepository;
import org.bankofcli.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SQLiteAuthenticationTest {
    @TempDir Path directory;

    private Supplier<Connection> database() throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("bank.db");
        Supplier<Connection> connections = () -> {
            try {
                return DriverManager.getConnection(url);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        };
        try (var input = BankApplication.class.getResourceAsStream("/bank_schema.sql");
             var connection = connections.get(); var statement = connection.createStatement()) {
            assertNotNull(input);
            for (String sql : new String(input.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                if (!sql.isBlank()) statement.execute(sql);
            }
        }
        return connections;
    }

    @Test
    void registrationSurvivesFreshConnectionsAndPreservesLeadingZeroPins() throws Exception {
        var connections = database();
        for (int pin : new int[] {0, 1, 123, 999, 9999}) {
            var account = new AuthServiceImpl(new SQLiteAccountRepository(connections)).register("Alice", "Smith", pin);
            String id = account.getAccountId();
            assertEquals(4, UUID.fromString(id).version());
            // Reinitialize as on application startup, then use a new repository and connection.
            database();
            var reopened = new SQLiteAccountRepository(connections);
            assertEquals(id, new AuthServiceImpl(reopened).login(id, pin).getAccountId());
            assertTrue(reopened.existsById(id));
            assertEquals(new java.math.BigDecimal("0.00"), reopened.getBalance(id));
            try (var connection = connections.get();
                 var statement = connection.prepareStatement("SELECT PIN FROM accounts WHERE accountId = ?")) {
                statement.setString(1, id);
                try (var result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(String.format(Locale.ROOT, "%04d", pin), result.getString(1));
                }
            }
        }
    }

    @Test
    void cliCanLoginToAccountCreatedInAnEarlierRun() throws Exception {
        var connections = database();
        var output = new java.io.ByteArrayOutputStream();
        var repository = new SQLiteAccountRepository(connections);
        new BankApplication(new AuthServiceImpl(repository),
                new org.bankofcli.service.impl.AccountServiceImpl(repository), null,
                new java.util.Scanner("1\nAlice\nSmith\n0001\n8\n"), new java.io.PrintStream(output)).run();
        var match = java.util.regex.Pattern.compile("Your Account ID: ([0-9a-f-]{36})")
                .matcher(output.toString(StandardCharsets.UTF_8));
        assertTrue(match.find());
        String id = match.group(1);
        output.reset();
        var reopened = new SQLiteAccountRepository(database());
        var saved = reopened.findById(id).orElseThrow();
        assertEquals("Alice", saved.getFirstName());
        assertEquals("Smith", saved.getLastName());
        assertEquals(1, saved.getPIN());
        new BankApplication(new AuthServiceImpl(reopened),
                new org.bankofcli.service.impl.AccountServiceImpl(reopened), null,
                new java.util.Scanner("2\n" + id + "\n0001\n3\n9\n3\n8\n"),
                new java.io.PrintStream(output)).run();
        String text = output.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains("Login successful."));
        assertTrue(text.contains("Your Balance is: $0.00"));
        assertTrue(text.contains("Logged out."));
        assertTrue(text.contains("Please register or log in first."));
    }

    @Test
    void invalidCredentialsFailAndDuplicateInsertCannotOverwriteAccount() throws Exception {
        var repository = new SQLiteAccountRepository(database());
        var auth = new AuthServiceImpl(repository);
        var first = auth.register("Alice", "Smith", 1234);
        var second = auth.register("Alice", "Smith", 1234);
        assertNotEquals(first.getAccountId(), second.getAccountId());
        assertThrows(IllegalStateException.class, () -> repository.create(first));
        assertEquals(first.getAccountId(), auth.login(first.getAccountId(), 1234).getAccountId());
        var wrong = assertThrows(BankingException.class, () -> auth.login(first.getAccountId(), 4321));
        var missing = assertThrows(BankingException.class, () -> auth.login(UUID.randomUUID().toString(), 1234));
        assertEquals(wrong.getMessage(), missing.getMessage());
        assertThrows(BankingException.class, () -> auth.login("' OR 1=1 --", 1234));
        assertThrows(BankingException.class, () -> auth.register("Alice", "Smith", -1));
        assertThrows(BankingException.class, () -> auth.register("Alice", "Smith", 10000));
    }
}
