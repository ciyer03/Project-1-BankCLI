package org.bankofcli.repository.sqlite;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.bankofcli.exceptions.InsufficientBalanceException;
import org.bankofcli.model.Transaction;
import org.bankofcli.repository.TransactionRepository;
import org.bankofcli.utils.SQLiteConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SQLiteTransactionRepository implements TransactionRepository {
    private static final Logger logger = LoggerFactory.getLogger(SQLiteTransactionRepository.class);

    /**
     * Deposits the specified amount into the specified account ID.
     * 
     * @param accountId The account ID into which to deposit the money to.
     * @param amount The amount of money to deposit into the account.
     */
    @Override
    public void deposit(String accountId, BigDecimal amount) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'deposit'");
    }

    /**
     * Withdraws the specified amount from the specified account ID.
     * 
     * @param accountId The account ID to withdraw money from.
     * @param amount The amount of money to withdraw from the account.
     * @throws InsufficientBalanceException If there is insufficient balance to withdraw the requested money.
     * @see TransactionRepository#withdraw(String, BigDecimal)
     */
    @Override
    public void withdraw(String accountId, BigDecimal amount) throws InsufficientBalanceException {
        String withdrawQuery = "UPDATE accounts SET balance = balance - ? WHERE account_id = ? AND balance >= ?";
        try (
                Connection conn = SQLiteConnectionFactory.getConnection();
                PreparedStatement psmt = conn.prepareStatement(withdrawQuery);
            ) {

            conn.setAutoCommit(false);

            psmt.setBigDecimal(1, amount);
            psmt.setString(2, accountId);
            psmt.setBigDecimal(3, amount);
            
            logger.debug("Trying to withdraw ${} from account ID {}.", amount, accountId);
            
            if (psmt.executeUpdate() == 0) {
                conn.rollback();
                logger.error("Insufficient balance to withdraw requested amount ${}.", amount);
                throw new InsufficientBalanceException("Insufficient balance to withdraw requested amount $" + amount);
            }
            conn.commit();
            logger.debug("Successfully withdrew ${} from account ID {}.", amount, accountId);
            
        } catch (SQLException e) {
            logger.error("Database access error during database initialization.", e);
            throw new IllegalStateException("Database access error during database initialization.", e);
        }
    }

    /**
     * Transfer the specified amount from sourceAccountId to destinationAccountId.
     * 
     * @param sourceAccountId The account from which to transfer the money from.
     * @param destinationAccountId The account to which to transfer the money to.
     * @param amount The amoount of money to transfer.
     * @throws InsufficientBalanceException If there is insufficient balance in the 
     * sourceAccountId account to transfer.
     * @see TransactionRepository#transfer(String, String, BigDecimal)
     */
    @Override
    public void transfer(String sourceAccountId, String destinationAccountId, BigDecimal amount)
            throws InsufficientBalanceException {

        String withdrawQuery =
                "UPDATE accounts SET balance = balance - ? " +
                        "WHERE accountId = ? AND balance >= ?";

        String depositQuery =
                "UPDATE accounts SET balance = balance + ? " +
                        "WHERE accountId = ?";

        String transactionQuery =
                "INSERT INTO transactions (accountId, type, amount, timestamp) " +
                        "VALUES (?, ?, ?, ?)";

        try (Connection conn = SQLiteConnectionFactory.getConnection()) {

            conn.setAutoCommit(false);

            try {
                // Make sure destination exists BEFORE changing source balance
                try (PreparedStatement checkDestination =
                             conn.prepareStatement("SELECT 1 FROM accounts WHERE accountId = ?")) {

                    checkDestination.setString(1, destinationAccountId);

                    try (var result = checkDestination.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("Destination account does not exist.");
                        }
                    }
                }

                // Withdraw from source account
                try (PreparedStatement withdraw =
                             conn.prepareStatement(withdrawQuery)) {

                    withdraw.setBigDecimal(1, amount);
                    withdraw.setString(2, sourceAccountId);
                    withdraw.setBigDecimal(3, amount);

                    if (withdraw.executeUpdate() == 0) {
                        throw new InsufficientBalanceException(
                                "Insufficient balance or source account does not exist.");
                    }
                }

                // Deposit into destination account
                try (PreparedStatement deposit =
                             conn.prepareStatement(depositQuery)) {

                    deposit.setBigDecimal(1, amount);
                    deposit.setString(2, destinationAccountId);

                    if (deposit.executeUpdate() == 0) {
                        throw new IllegalArgumentException("Destination account does not exist.");
                    }
                }

                String timestamp = java.time.LocalDateTime.now().toString();

                // Record transfer out
                try (PreparedStatement transaction =
                             conn.prepareStatement(transactionQuery)) {

                    transaction.setString(1, sourceAccountId);
                    transaction.setString(2, "TRANSFER_OUT");
                    transaction.setBigDecimal(3, amount);
                    transaction.setString(4, timestamp);
                    transaction.executeUpdate();
                }

                // Record transfer in
                try (PreparedStatement transaction =
                             conn.prepareStatement(transactionQuery)) {

                    transaction.setString(1, destinationAccountId);
                    transaction.setString(2, "TRANSFER_IN");
                    transaction.setBigDecimal(3, amount);
                    transaction.setString(4, timestamp);
                    transaction.executeUpdate();
                }

                conn.commit();

            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (InsufficientBalanceException | IllegalArgumentException e) {
            throw e;
        } catch (SQLException e) {
            logger.error("Database error during transfer.");
            throw new IllegalStateException("Database error during transfer.", e);
        }
    }

    /**
     * Returns the most recent "limit" number of transactions done by the account ID.
     * 
     * @param accountId The account ID of the account to fetch transactions of.
     * @param limit The amount of transactions of fetch.
     * @return A list of "limit" number of {@link Transaction} objects.
     */
    @Override
    public List<Transaction> getRecentTransactions(String accountId, int limit) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getRecentTransactions'");
    }
    
}
