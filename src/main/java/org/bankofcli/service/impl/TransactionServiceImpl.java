package org.bankofcli.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

import org.bankofcli.exceptions.InsufficientBalanceException;
import org.bankofcli.model.Transaction;
import org.bankofcli.repository.AccountRepository;
import org.bankofcli.repository.TransactionRepository;
import org.bankofcli.service.TransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TransactionServiceImpl implements TransactionService {
    private static final Logger log = LoggerFactory.getLogger(TransactionServiceImpl.class);
    private final AccountRepository accounts;
    private final TransactionRepository transactions;

    public TransactionServiceImpl(AccountRepository accounts, TransactionRepository transactions) {
        this.accounts = Objects.requireNonNull(accounts);
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public void deposit(String accountId, BigDecimal amount) {
        amount = BankingRules.amount(amount);
        BankingRules.existingAccount(accounts, accountId);
        transactions.deposit(accountId, amount);
        log.info("Deposit succeeded");
    }

    @Override
    public void withdraw(String accountId, BigDecimal amount) {
        throw new UnsupportedOperationException("Withdraw is not implemented yet");
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

        transactions.transfer(
                sourceAccountId,
                destinationAccountId,
                amount
        );
    }

    @Override
    public List<Transaction> getRecentTransactions(String accountId, int limit) {
        BankingRules.existingAccount(accounts, accountId);
        return List.copyOf(transactions.getRecentTransactions(accountId, limit));
    }
}
