package org.bankofcli.utils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.bankofcli.BankApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.SQLiteConfig;

public class SQLiteConnectionFactory {
    private static final Logger logger = LoggerFactory.getLogger(SQLiteConnectionFactory.class);
    private static final String DB_RELATIVE_PATH = "data/bank.db";
    private static final String DB_PATH;

    static {
        try {
            // Gets the location of the compiled byteclass (would be in target/classes)
            URI currentLocation = SQLiteConnectionFactory.class.getProtectionDomain().getCodeSource().
            getLocation().toURI();
            logger.debug("SQLiteConnectionFactory compiled class location: {}", currentLocation.toString());

            // Go up twice from the target/classes folder to land at the root.
            Path root = Paths.get(currentLocation).getParent().getParent();
            logger.debug("Deduced Project Root: {}", root.toString());

            DB_PATH = root.resolve(DB_RELATIVE_PATH).toString();
            logger.info("Database Path: {}", DB_PATH);
        } catch (URISyntaxException e) {
            logger.error("Failed to resolve project root for database path.", e);
            throw new IllegalStateException("Failed to resolve project root for database path.", e);
        }
    }

    /**
     * Reads and initializes the database to the data/ folder. The database would be named bank.db.
     */
    public static void initializeDatabase() {
        try (InputStream in = BankApplication.class.getResourceAsStream("/bank_schema.sql")) {
            if (in == null) {
                logger.error("Unable to load the database schema file.");
                throw new IllegalStateException("Unable to load the database schema file.");
            }

            String script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            logger.info("Read in the database initialization script.");

            try (
                    Connection conn = SQLiteConnectionFactory.getConnection();
                    Statement st = conn.createStatement();
                ) {
                migrateAccountIds(conn);
                for (String sql : script.split(";")) {
                    if (!(sql.trim().isEmpty())) {
                        st.execute(sql);
                        logger.trace("Ran the SQL statement: {}", sql);
                    }
                }
            }            
        } catch (SQLException e) {
            logger.error("Database access error during database initialization.", e);
            throw new IllegalStateException("Database access error during database initialization.", e);
        } catch (IOException e) {
            logger.error("Unable to either close the database script input stream " + 
            "or read from the database initialization script.", e);
            
            throw new IllegalStateException("Unable to either close the database script input stream " + 
            "or read from the database initialization script.", e);
        }
    }

    /** Updates the original account ID constraint without changing saved IDs or transactions. */
    public static void migrateAccountIds(Connection conn) throws SQLException {
        String tableSql;
        try (Statement statement = conn.createStatement();
             var result = statement.executeQuery(
                     "SELECT sql FROM sqlite_master WHERE type = 'table' AND name = 'accounts'")) {
            if (!result.next()) return;
            tableSql = result.getString(1);
        }
        String updatedSql = tableSql.replaceAll("(?i)LENGTH\\s*\\(accountId\\)\\s*=\\s*36",
                "LENGTH(accountId) IN (22, 36)");
        if (updatedSql.equals(tableSql)) return;
        if (!conn.getAutoCommit()) throw new SQLException("Account migration requires auto-commit mode.");
        try (Statement statement = conn.createStatement()) {
            boolean foreignKeys;
            try (var result = statement.executeQuery("PRAGMA foreign_keys")) {
                foreignKeys = result.getInt(1) != 0;
            }
            statement.execute("PRAGMA foreign_keys = OFF");
            try {
                conn.setAutoCommit(false);
                statement.execute(updatedSql.replaceFirst("(?i)CREATE TABLE\\s+\"?accounts\"?",
                        "CREATE TABLE accounts_id_migration"));
                statement.execute("INSERT INTO accounts_id_migration (accountId, firstName, lastName, PIN, balance) "
                        + "SELECT accountId, firstName, lastName, PIN, balance FROM accounts");
                statement.execute("DROP TABLE accounts");
                statement.execute("ALTER TABLE accounts_id_migration RENAME TO accounts");
                try (var result = statement.executeQuery("PRAGMA foreign_key_check")) {
                    if (result.next()) throw new SQLException("Account migration failed foreign key validation.");
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
                statement.execute("PRAGMA foreign_keys = " + (foreignKeys ? "ON" : "OFF"));
            }
        }
    }

    /**
     * Returns a SQLite JDBC connection.
     * 
     * @return A connection to the JDBC database.
     */
    public static Connection getConnection() {
        try {
            SQLiteConfig config = new SQLiteConfig();
            config.enforceForeignKeys(true);
            return DriverManager.getConnection("jdbc:sqlite:" + DB_PATH, config.toProperties());
        } catch (SQLException e) {
            logger.error("Failed to establish a connection to the SQLite database.", e);
            throw new IllegalStateException("Failed to establish a connection to the SQLite database.", e);
        }
    }
}
