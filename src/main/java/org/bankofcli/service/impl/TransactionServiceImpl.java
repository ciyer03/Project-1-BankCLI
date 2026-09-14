package org.bankofcli.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.bankofcli.exceptions.AccountDoesNotExistException;
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

    @Override
    public void deposit(String accountId, BigDecimal amount) {
        amount = BankingRules.amount(amount);
        BankingRules.existingAccount(accountRepository, accountId);
        transactionRepository.deposit(accountId, amount);
        logger.info("Deposit succeeded");
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
        logger.trace("Checking whether accountId \"{}\" exists ...", accountId);
        if (!(this.accountRepository.existsById(accountId))) {
            logger.error("The specified account ID \"{}\" doesn't exist.", accountId);
            throw new AccountDoesNotExistException("The specified account ID \"" + accountId + "\"" + " doesn't exist.");
        }
        logger.trace("accountId \"{}\" exists. Proceeding ...", accountId);

        logger.trace("Calling repository withdraw() method now with account ID \"{}\" and amount ${}.", accountId, amount);
        this.transactionRepository.withdraw(accountId, amount);
    }

    @Override
    public void transfer(String sourceAccountId,
                         String destinationAccountId,
                         BigDecimal amount) throws InsufficientBalanceException {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    "Transfer amount must be greater than zero.");
        }

        if (sourceAccountId == null || destinationAccountId == null ||
                sourceAccountId.isBlank() || destinationAccountId.isBlank()) {
            throw new IllegalArgumentException(
                    "Account IDs cannot be empty.");
        }

        if (sourceAccountId.equals(destinationAccountId)) {
            throw new IllegalArgumentException(
                    "Source and destination accounts must be different.");
        }

        transactionRepository.transfer(
                sourceAccountId,
                destinationAccountId,
                amount
        );
    }

    @Override
    public List<Transaction> getRecentTransactions(String accountId, int limit) {
        BankingRules.existingAccount(accountRepository, accountId);
        return List.copyOf(transactionRepository.getRecentTransactions(accountId, limit));
    }
}
