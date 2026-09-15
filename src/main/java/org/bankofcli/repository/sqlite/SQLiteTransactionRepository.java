package org.bankofcli.repository.sqlite;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.bankofcli.exceptions.BankingException;
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
        String transferAmountQuery = "UPDATE accounts SET balance = CASE WHEN accountId = ? THEN balance - ? WHEN accountId = ? THEN balance + ? END WHERE accountId IN (?, ?)";

        try (
                Connection conn = SQLiteConnectionFactory.getConnection();
                PreparedStatement psmtTransferAmount = conn.prepareStatement(transferAmountQuery);
            ) {
                
            conn.setAutoCommit(false);

            psmtTransferAmount.setString(1, sourceAccountId);
            psmtTransferAmount.setBigDecimal(2, amount.setScale(2));
            psmtTransferAmount.setString(3, destinationAccountId);
            psmtTransferAmount.setBigDecimal(4, amount.setScale(2));
            psmtTransferAmount.setString(5, sourceAccountId);
            psmtTransferAmount.setString(6, destinationAccountId);

            logger.debug("Attempting a transfer of ${} from source account ID \"{}\" to destination account ID \"{}\".",
                amount.setScale(2), sourceAccountId, destinationAccountId);
            if (psmtTransferAmount.executeUpdate() == 0) {
                conn.rollback();
                logger.debug("Failed to transfer ${} from source account ID \"{}\" to destination account ID \"{}\".",
                    amount.setScale(2), sourceAccountId, destinationAccountId);
                throw new BankingException("Failed to transfer $" + amount.setScale(2) + " from source account ID \"" + sourceAccountId +
                 "\" to destination account ID \"" + destinationAccountId + "\".");
            }

            conn.commit();
            logger.debug("Successfully transferred ${} from source account ID \"{}\" to destination account ID \"{}\".",
                amount.setScale(2), sourceAccountId, destinationAccountId);
               
        } catch (SQLException e) {
            logger.error("Database access error during database initialization.", e);
            throw new IllegalStateException("Database access error during database initialization.", e);
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
