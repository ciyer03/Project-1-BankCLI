package org.bankofcli.repository.sqlite;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.bankofcli.model.Account;
import org.bankofcli.repository.AccountRepository;
import org.bankofcli.exceptions.BankingException;
import org.bankofcli.utils.SQLiteConnectionFactory;

public class SQLiteAccountRepository implements AccountRepository {
    private final Supplier<Connection> connections;

    public SQLiteAccountRepository() {
        this(SQLiteConnectionFactory::getConnection);
    }

    public SQLiteAccountRepository(Supplier<Connection> connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    @Override
    public Account create(Account account) {
        String sql = "INSERT INTO accounts (accountId, firstName, lastName, PIN) VALUES (?, ?, ?, ?)";
        try (var connection = connections.get(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, account.getAccountId());
            statement.setString(2, account.getFirstName());
            statement.setString(3, account.getLastName());
            statement.setString(4, String.format(Locale.ROOT, "%04d", account.getPIN()));
            statement.executeUpdate();
            return account;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to create account.", e);
        }
    }

    @Override
    public Optional<Account> findById(String accountId) {
        String sql = "SELECT accountId, firstName, lastName, PIN, balance FROM accounts WHERE accountId = ?";
        try (var connection = connections.get(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) return Optional.empty();
                return Optional.of(new Account(result.getString("firstName"), result.getString("lastName"),
                        result.getString("accountId"), Integer.parseInt(result.getString("PIN")),
                        result.getBigDecimal("balance")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load account.", e);
        }
    }

    @Override
    public boolean existsById(String accountId) {
        return findById(accountId).isPresent();
    }

    @Override
    public BigDecimal getBalance(String accountId) {
        try (var connection = connections.get();
             var statement = connection.prepareStatement("SELECT balance FROM accounts WHERE accountId = ?")) {
            statement.setString(1, accountId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) throw new BankingException("Account does not exist.");
                return result.getBigDecimal("balance").setScale(2);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to load balance.", e);
        }
    }
}
