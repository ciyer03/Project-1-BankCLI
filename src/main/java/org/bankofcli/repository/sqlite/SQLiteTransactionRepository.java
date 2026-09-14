package org.bankofcli.repository.sqlite;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.time.LocalDateTime;

import org.bankofcli.exceptions.InsufficientBalanceException;
import org.bankofcli.model.Transaction;
import org.bankofcli.repository.TransactionRepository;
import org.bankofcli.utils.SQLiteConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bankofcli.exceptions.BankingException;
import org.bankofcli.model.TransactionType;
import org.bankofcli.service.impl.BankingRules;

public class SQLiteTransactionRepository implements TransactionRepository {
    private static final Logger logger = LoggerFactory.getLogger(SQLiteTransactionRepository.class);

    /**
     * Deposits the specified amount into the specified account ID.
     * 
     * @param accountId The account ID into which to deposit the money to.
     * @param amount The amount of money to deposit into the account.
     * @throws BankingException If the amount is not a positive whole number of cents
     * or the account does not exist.
     */
    @Override
    public void deposit(String accountId, BigDecimal amount) {
        // The service validates first, but the repository enforces the same rule so that it is
        // safe to call directly.
        BigDecimal deposit = BankingRules.amount(amount);

        String selectBalance = "SELECT balance FROM accounts WHERE accountId = ?";
        String updateBalance = "UPDATE accounts SET balance = ? WHERE accountId = ?";
        String insertTransaction =
                "INSERT INTO transactions (accountId, type, amount, timestamp) VALUES (?, ?, ?, ?)";

        try (Connection conn = SQLiteConnectionFactory.getConnection()) {
            conn.setAutoCommit(false);
            try {
                BigDecimal balance;
                try (PreparedStatement select = conn.prepareStatement(selectBalance)) {
                    select.setString(1, accountId);
                    try (ResultSet result = select.executeQuery()) {
                        if (!result.next()) {
                            logger.warn("Deposit rejected: the account does not exist.");
                            throw new BankingException("Account does not exist.");
                        }
                        balance = result.getBigDecimal("balance").setScale(2, RoundingMode.UNNECESSARY);
                    }
                }

                try (PreparedStatement update = conn.prepareStatement(updateBalance)) {
                    update.setBigDecimal(1, balance.add(deposit));
                    update.setString(2, accountId);
                    if (update.executeUpdate() != 1) {
                        logger.warn("Deposit rejected: the account could not be updated.");
                        throw new BankingException("Account does not exist.");
                    }
                }

                try (PreparedStatement insert = conn.prepareStatement(insertTransaction)) {
                    insert.setString(1, accountId);
                    insert.setString(2, TransactionType.DEPOSIT.name());
                    insert.setBigDecimal(3, deposit);
                    insert.setString(4, LocalDateTime.now().toString());
                    insert.executeUpdate();
                }

                conn.commit();
                logger.info("Deposit recorded.");
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            logger.error("Database access error while recording a deposit.");
            throw new IllegalStateException("Unable to record the deposit.", e);
        }
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
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'transfer'");
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
