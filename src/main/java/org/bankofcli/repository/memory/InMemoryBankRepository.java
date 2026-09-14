package org.bankofcli.repository.memory;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import org.bankofcli.exceptions.BankingException;
import org.bankofcli.model.*;
import org.bankofcli.repository.*;
import org.bankofcli.service.impl.BankingRules;

/** Session-only storage. Synchronization makes balance and history changes atomic. */
public class InMemoryBankRepository implements AccountRepository, TransactionRepository {
    private final Map<String, Account> accounts = new HashMap<>();
    private final Map<String, BigDecimal> balances = new HashMap<>();
    private final List<Transaction> history = new ArrayList<>();
    private long nextId = 1;

    @Override
    public synchronized Account create(Account account) {
        BankingRules.accountId(account.getAccountId());
        if (accounts.containsKey(account.getAccountId())) {
            throw new BankingException("Account ID is already registered.");
        }
        accounts.put(account.getAccountId(), account);
        balances.put(account.getAccountId(), new BigDecimal("0.00"));
        return snapshot(account);
    }

    private Account snapshot(Account account) {
        return new Account(account.getFirstName(), account.getLastName(), account.getAccountId(),
                account.getPIN(), balances.get(account.getAccountId()));
    }

    @Override
    public synchronized Optional<Account> findById(String accountId) {
        return Optional.ofNullable(accounts.get(accountId)).map(this::snapshot);
    }

    @Override
    public synchronized boolean existsById(String accountId) {
        return accounts.containsKey(accountId);
    }

    @Override
    public synchronized BigDecimal getBalance(String accountId) {
        BankingRules.existingAccount(this, accountId);
        return balances.get(accountId);
    }

    @Override
    public synchronized void deposit(String accountId, BigDecimal amount) {
        amount = BankingRules.amount(amount);
        BigDecimal balance = getBalance(accountId);
        balances.put(accountId, balance.add(amount));
        record(accountId, TransactionType.DEPOSIT, amount);
    }

    @Override
    public synchronized void withdraw(String accountId, BigDecimal amount) {
        amount = BankingRules.amount(amount);
        BigDecimal balance = getBalance(accountId);
        requireFunds(balance, amount);
        balances.put(accountId, balance.subtract(amount));
        record(accountId, TransactionType.WITHDRAW, amount);
    }

    @Override
    public synchronized void transfer(String source, String destination, BigDecimal amount) {
        amount = BankingRules.amount(amount);
        BigDecimal sourceBalance = getBalance(source);
        BigDecimal destinationBalance = getBalance(destination);
        if (source.equals(destination)) {
            throw new BankingException("Cannot transfer to the same account.");
        }
        requireFunds(sourceBalance, amount);
        balances.put(source, sourceBalance.subtract(amount));
        balances.put(destination, destinationBalance.add(amount));
        record(source, TransactionType.TRANSFER_OUT, amount);
        record(destination, TransactionType.TRANSFER_IN, amount);
    }

    private void requireFunds(BigDecimal balance, BigDecimal amount) {
        if (balance.compareTo(amount) < 0) {
            throw new BankingException("Insufficient funds in the account.");
        }
    }

    private void record(String accountId, TransactionType type, BigDecimal amount) {
        history.add(new Transaction(nextId++, accountId, type, amount, LocalDateTime.now()));
    }

    @Override
    public synchronized List<Transaction> getRecentTransactions(String accountId, int limit) {
        BankingRules.existingAccount(this, accountId);
        if (limit < 0) throw new IllegalArgumentException("Limit must not be negative.");
        return history.reversed().stream().filter(t -> t.getAccountId().equals(accountId))
                .limit(limit).toList();
    }
}
