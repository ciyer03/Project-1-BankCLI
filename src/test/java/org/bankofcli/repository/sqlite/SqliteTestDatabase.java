package org.bankofcli.repository.sqlite;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.function.Supplier;

import org.bankofcli.BankApplication;

/**
 * A throwaway SQLite database file under the project's data/ folder, initialized from
 * bank_schema.sql. Callers must call {@link #close()} (from an @AfterEach) to 
 * delete it.
 */
public final class SqliteTestDatabase implements AutoCloseable {
    public final Supplier<Connection> connections;
    private final Path file;

    private SqliteTestDatabase(Path file, Supplier<Connection> connections) {
        this.file = file;
        this.connections = connections;
    }

    public static SqliteTestDatabase create() {
        Path file = Path.of("data", "test-" + UUID.randomUUID() + ".db");
        String url = "jdbc:sqlite:" + file;
        Supplier<Connection> connections = () -> {
            try {
                return DriverManager.getConnection(url);
            } catch (SQLException e) {
                throw new IllegalStateException("Unable to open the test database.", e);
            }
        };
        try (InputStream input = BankApplication.class.getResourceAsStream("/bank_schema.sql");
             Connection connection = connections.get();
             Statement statement = connection.createStatement()) {
            for (String sql : new String(input.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                if (!sql.isBlank()) statement.execute(sql);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to read the schema script.", e);
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to initialize the test database.", e);
        }
        return new SqliteTestDatabase(file, connections);
    }

    @Override
    public void close() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to delete the test database.", e);
        }
    }
}
