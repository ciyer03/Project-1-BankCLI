package org.bankofcli.repository;

import java.math.BigDecimal;
import java.util.List;

import org.bankofcli.exceptions.InsufficientBalanceException;
import org.bankofcli.model.Transaction;

public interface TransactionRepository {
    /**
     * Deposits the specified amount into the specified account ID.
     * 
     * @param accountId The account ID into which to deposit the money to.
     * @param amount The amount of money to deposit into the account.
     */    
    void deposit(String accountId, BigDecimal amount);

    /**
     * Withdraws the specified amount from the specified account ID.
     * 
     * @param accountId The account ID to withdraw money from.
     * @param amount The amount of money to withdraw from the account.
     * @throws InsufficientBalanceException If there is insufficient balance to withdraw 
     * the requested money.
     */
    void withdraw(String accountId, BigDecimal amount) throws InsufficientBalanceException;

    /**
     * Transfer the specified amount from sourceAccountId to destinationAccountId.
     * 
     * @param sourceAccountId The account from which to transfer the money from.
     * @param destinationAccountId The account to which to transfer the money to.
     * @param amount The amoount of money to transfer.
     * @throws InsufficientBalanceException If there is insufficient balance in the 
     * sourceAccountId account to transfer.
     */
    void transfer(String sourceAccountId,
            String destinationAccountId, BigDecimal amount) throws InsufficientBalanceException;


    /**
     * Returns the most recent "limit" number of transactions done by the account ID.
     * 
     * @param accountId The account ID of the account to fetch transactions of.
     * @param limit The amount of transactions of fetch.
     * @return A list of "limit" number of {@link Transaction} objects.
     */
    List<Transaction> getRecentTransactions(String accountId, int limit);
}
