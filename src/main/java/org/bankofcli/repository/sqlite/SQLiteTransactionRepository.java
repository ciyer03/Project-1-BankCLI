package org.bankofcli.repository.sqlite;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.math.RoundingMode;

import org.bankofcli.exceptions.BankingException;
import org.bankofcli.exceptions.AccountDoesNotExistException;
import org.bankofcli.exceptions.InsufficientBalanceException;
import org.bankofcli.model.Transaction;
import org.bankofcli.model.TransactionType;
import org.bankofcli.repository.TransactionRepository;
import org.bankofcli.utils.SQLiteConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bankofcli.service.impl.BankingRules;

public class SQLiteTransactionRepository implements TransactionRepository {
    private static final Logger logger = LoggerFactory.getLogger(SQLiteTransactionRepository.class);

    /**
     * Deposits the specified amount into the specified account ID.
     * 
     * @param accountId The account ID into which to deposit the money to.
     * @param amount The amount of money to deposit into the account.
     * @throws BankingException If the amount is not a positive whole number of cents.
     * @throws AccountDoesNotExistException If the account does not exist.
     */
    @Override
    public void deposit(String accountId, BigDecimal amount) {
        BigDecimal deposit = BankingRules.amount(amount);

        String selectBalance = "SELECT balance FROM accounts WHERE accountId = ?";
        String updateBalance = "UPDATE accounts SET balance = ? WHERE accountId = ?";
        String insertTransaction = "INSERT INTO transactions (accountId, type, amount, timestamp) VALUES (?, ?, ?, ?)";

        try (Connection conn = SQLiteConnectionFactory.getConnection()) {
            conn.setAutoCommit(false);
            try {
                BigDecimal balance;
                try (PreparedStatement select = conn.prepareStatement(selectBalance)) {
                    select.setString(1, accountId);
                    try (ResultSet result = select.executeQuery()) {
                        if (!result.next()) {
                            logger.warn("Deposit rejected: the account does not exist.");
                            throw new AccountDoesNotExistException("Account does not exist.");
                        }
                        balance = result.getBigDecimal("balance").setScale(2, RoundingMode.UNNECESSARY);
                    }
                }

                try (PreparedStatement update = conn.prepareStatement(updateBalance)) {
                    update.setBigDecimal(1, balance.add(deposit));
                    update.setString(2, accountId);
                    if (update.executeUpdate() != 1) {
                        logger.warn("Deposit rejected: the account could not be updated.");
                        throw new AccountDoesNotExistException("Account does not exist.");
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
     * @see TransactionRepository#withdraw(String, BigDecimal)
     */
    @Override
    public void withdraw(String accountId, BigDecimal amount) {
        String withdrawQuery = "UPDATE accounts SET balance = balance - ? WHERE account_id = ?";
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

            logger.debug("Attempting to withdraw ${} from account ID \"{}\"...",
                amount.setScale(2), accountId);
            int withdrawResult = psmt.executeUpdate();
            if (withdrawResult == 0) {
                conn.rollback();
                logger.error("Unable to withdraw ${} from account ID \"{}\".",
                    amount.setScale(2), accountId);
                throw new BankingException("Failed to withdraw $" + amount.setScale(2) +
                 " from account ID \"" + accountId + "\".");
            }

            psmtTransactionRecord.setString(1, accountId);
            psmtTransactionRecord.setString(2, TransactionType.WITHDRAW.toString());
            psmtTransactionRecord.setBigDecimal(3, amount);
            String currentDateTime = LocalDateTime.now().toString();
            psmtTransactionRecord.setString(4, currentDateTime);

            logger.debug("Trying to insert a withdraw transaction record for account ID \"{}\" for amount ${}.", 
                accountId, amount.setScale(2));
            int transactionRecordResult = psmtTransactionRecord.executeUpdate();
            
            if (transactionRecordResult == 0) {
                conn.rollback();
                logger.error("Unable to add a transaction record for account ID {} for amount ${}.", 
                    accountId, amount);
                throw new BankingException("Unable to add a transaction record for account ID "+ 
                    accountId + " for amount $" + amount);
            }

            conn.commit();
            logger.debug("Successfully withdrew ${} from account ID {}.",
                amount.setScale(2), accountId);
            logger.debug("Successfully added a transaction record for account ID {} for amount ${} at {}.",
                accountId, amount.setScale(2), currentDateTime);
            
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
