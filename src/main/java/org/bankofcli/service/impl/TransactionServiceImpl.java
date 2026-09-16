package org.bankofcli.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

import org.bankofcli.exceptions.AccountDoesNotExistException;
import org.bankofcli.exceptions.BankingException;
import org.bankofcli.exceptions.InsufficientBalanceException;
import org.bankofcli.model.Transaction;
import org.bankofcli.repository.AccountRepository;
import org.bankofcli.repository.TransactionRepository;
import org.bankofcli.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionServiceImpl implements TransactionService {
    private static final Logger logger = LoggerFactory.getLogger(TransactionServiceImpl.class);
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransactionServiceImpl(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.transactionRepository = Objects.requireNonNull(transactionRepository);
    }

    /**
     * Deposits the specified amount into the specified account ID.
     *
     * @param accountId The account ID into which to deposit the money to.
     * @param amount The amount of money to deposit into the account.
     * @throws BankingException If the amount is not a positive whole number of cents.
     * @throws AccountDoesNotExistException If the specified accountId does not exist.
     */
    @Override
    public void deposit(String accountId, BigDecimal amount) {
        logger.trace("Checking whether accountId \"{}\" exists ...", accountId);
        if (!accountRepository.existsById(accountId)) {
            logger.error("The specified account ID \"{}\" doesn't exist.", accountId);
            throw new AccountDoesNotExistException("The specified account ID \"" + accountId + "\" doesn't exist.");
        }
        logger.trace("accountId \"{}\" exists. Proceeding ...", accountId);

        logger.trace("Validating deposit amount ...");
        if (amount == null || amount.signum() <= 0) {
            logger.error("Deposit amount is either null or not greater than zero.");
            throw new BankingException("Amount must be greater than zero.");
        }
        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            logger.error("Deposit amount contains a fraction of a cent.");
            throw new BankingException("Amount must contain no fractions of a cent.");
        }

        logger.trace("Calling repository deposit() method now with account ID \"{}\" and amount ${}.", accountId, amount);
        transactionRepository.deposit(accountId, amount);
        logger.info("Deposit succeeded.");
    }

    /**
     * Withdraws the specified amount from the specified account ID.
     * 
     * @param accountId The account ID to withdraw money from.
     * @param amount The amount of money to withdraw from the account.
     * @throws InsufficientBalanceException If there is insufficient balance to withdraw 
     * the requested money.
     * @throws AccountDoesNotExistException If there the specified accountId does not exist.
     */
    @Override
    public void withdraw(String accountId, BigDecimal amount) throws InsufficientBalanceException {
        logger.debug("Checking whether accountId \"{}\" exists ...", accountId);
        if (!(this.accountRepository.existsById(accountId))) {
            logger.error("The specified account ID \"{}\" doesn't exist.", accountId);
            throw new AccountDoesNotExistException("The specified account ID \"" + accountId + "\"" + " doesn't exist.");
        }
        logger.debug("accountId \"{}\" exists. Proceeding ...", accountId);

        logger.debug("Checking whether there's enough balance for a withdrawal...");
        if (this.accountRepository.getBalance(accountId).compareTo(amount) == -1) {
            logger.error("Insufficient balance to withdraw requested amount ${}.", 
                amount.setScale(2));
            throw new InsufficientBalanceException("Insufficient balance to withdraw requested amount $" + 
                amount.setScale(2));
        }
        logger.debug("There's enough balance. Proceeding...");

        logger.trace("Calling repository withdraw() method now with account ID \"{}\" and amount ${}.", 
            accountId, amount.setScale(2));
        this.transactionRepository.withdraw(accountId, amount);
    }

    /**
     * Transfer the specified amount from sourceAccountId to destinationAccountId.
     * 
     * @param sourceAccountId The account from which to transfer the money from.
     * @param destinationAccountId The account to which to transfer the money to.
     * @param amount The amoount of money to transfer.
     * @throws InsufficientBalanceException If there is insufficient balance in the 
     * sourceAccountId account to transfer.
     */
    @Override
    public void transfer(String sourceAccountId, String destinationAccountId, BigDecimal amount) 
    throws InsufficientBalanceException {

        logger.debug("Checking whether either account ID is null or blank...");
        if (sourceAccountId == null || destinationAccountId == null ||
                sourceAccountId.isBlank() || destinationAccountId.isBlank()) {
            logger.error("One or both of the account IDs are either blank or null.");
            throw new IllegalArgumentException(
                    "Account IDs cannot be empty.");
        }

        logger.debug("Checking whether both account IDs are the same...");
        if (sourceAccountId.equals(destinationAccountId)) {
            logger.error("Both the source and destination accounts are the same.");
            throw new IllegalArgumentException(
                    "Source and destination accounts must be different.");
        }

        logger.debug("Checking whether the amount is valid...");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            logger.debug("Amount is either null or less than or equal to 0");
            throw new IllegalArgumentException(
                    "Transfer amount must be greater than zero.");
        }

        logger.debug("Checking whether source account ID \"{}\" and destination account ID \"{}\" both exist...",
         sourceAccountId, destinationAccountId);
        if (!(this.accountRepository.existsById(sourceAccountId)) || !(this.accountRepository.existsById(destinationAccountId))) {
            logger.error("Either the source account ID \"{}\" or destination account ID \"{}\", or both, don't exist.",
             sourceAccountId, destinationAccountId);
            throw new AccountDoesNotExistException("Either the source account ID \"" + sourceAccountId + 
            "\" or destination account ID \"" + destinationAccountId + "\", or both, don't exist.");
        }

        logger.debug("Checking whether source account ID \"{}\" has enough balance for a transfer...", sourceAccountId);
        BigDecimal currentBalance = this.accountRepository.getBalance(sourceAccountId);
        logger.trace("Source account ID's \"{}\" balance: ${}.", sourceAccountId, currentBalance);
        if (currentBalance.compareTo(amount) == -1) {
            logger.error("Source account ID \"{}\" has insufficient balance for a transfer.", sourceAccountId);
            logger.trace("Current balance: ${}. Requested transfer amount: ${}.", currentBalance, amount);
            throw new InsufficientBalanceException("Source account ID \"" + sourceAccountId + "\" has insufficient balance for a transfer of $" +
                amount.setScale(2) + " to destination source ID \"" + destinationAccountId + "\".");
        }
        logger.debug("There is enough balance on source account ID \"{}\" for a transfer. Proceeding...", sourceAccountId);

        logger.trace("Calling repository transfer() method now with source account ID \"{}\" and destination account ID \"{}\" for amount ${}...",
            sourceAccountId, destinationAccountId, amount.setScale(2));
        
        this.transactionRepository.transfer(sourceAccountId, destinationAccountId, amount);
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
        logger.debug("Checking whether accountId \"{}\" exists ...", accountId);
        if (!(this.accountRepository.existsById(accountId))) {
            logger.error("The specified account ID \"{}\" doesn't exist.", accountId);
            throw new AccountDoesNotExistException("The specified account ID \"" + accountId + "\"" + " doesn't exist.");
        }
        logger.debug("accountId \"{}\" exists. Proceeding ...", accountId);

        logger.debug("Checking whether the limit is valid...");
        if (limit <= 0) {
            logger.error("Invalid limit: \"{}\". Limit must be greater than 0.", limit);
            throw new BankingException("Invalid limit: \"" + limit + "\". Limit must be greater than 0.");
        }
        logger.debug("The limit is valid. Proceeding...");

        return this.transactionRepository.getRecentTransactions(accountId, limit);
    }
}
