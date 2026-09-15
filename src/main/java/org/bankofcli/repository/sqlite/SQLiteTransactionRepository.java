package org.bankofcli.repository.sqlite;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import org.bankofcli.exceptions.BankingException;
import org.bankofcli.exceptions.InsufficientBalanceException;
import org.bankofcli.model.Transaction;
import org.bankofcli.model.TransactionType;
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
        String transactionRecordQuery = "INSERT INTO transactions (accountId, type, amount, timestamp) VALUES (?, ?, ?, ?)";

        try (
                Connection conn = SQLiteConnectionFactory.getConnection();
                PreparedStatement psmt = conn.prepareStatement(withdrawQuery);
                PreparedStatement psmtTransactionRecord = conn.prepareStatement(transactionRecordQuery);
            ) {

            conn.setAutoCommit(false);

            psmt.setBigDecimal(1, amount);
            psmt.setString(2, accountId);
            psmt.setBigDecimal(3, amount);
            
            logger.debug("Trying to withdraw ${} from account ID {}.", amount, accountId);
            int balanceUpdateResult = psmt.executeUpdate();
            
            if (balanceUpdateResult == 0) {
                conn.rollback();
                logger.error("Insufficient balance to withdraw requested amount ${}.", amount);
                throw new InsufficientBalanceException("Insufficient balance to withdraw requested amount $" + amount);
            }

            psmtTransactionRecord.setString(1, accountId);
            psmtTransactionRecord.setString(2, TransactionType.WITHDRAW.toString());
            psmtTransactionRecord.setBigDecimal(3, amount);
            String currentDateTime = LocalDateTime.now().toString();
            psmtTransactionRecord.setString(4, currentDateTime);

            logger.debug("Trying to insert a withdraw transaction record for account ID {} for amount ${}.", accountId, amount);
            int transactionRecordResult = psmtTransactionRecord.executeUpdate();
            
            if (transactionRecordResult == 0) {
                conn.rollback();
                logger.error("Unable to add a transaction record for account ID {} for amount ${}.", accountId, amount);
                throw new BankingException("Unable to add a transaction record for account ID "+ accountId + " for amount $" + amount);
            }

            conn.commit();
            logger.debug("Successfully withdrew ${} from account ID {}.", amount, accountId);
            logger.debug("Successfully added a transaction record for account ID {} for amount ${} at {}.", accountId, amount, currentDateTime);
            
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
