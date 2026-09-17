package org.bankofcli.service.impl;

import java.math.BigDecimal;

import org.bankofcli.exceptions.BankingException;
import org.bankofcli.model.Account;
import org.bankofcli.repository.memory.InMemoryBankRepository;
import org.bankofcli.service.AccountService;
import org.bankofcli.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccountServiceImplTest {
    private InMemoryBankRepository repository;
    private AccountService accounts;
    private TransactionService transactions;

    @BeforeEach
    void setup() {
        repository = new InMemoryBankRepository();
        accounts = new AccountServiceImpl(repository);
        transactions = new TransactionServiceImpl(repository, repository);
        repository.create(new Account("", "", "Alice1-", 1234));
    }

    @Test
    void getBalanceReturnsZeroForNewAccountAndReflectsDeposit() {
        assertEquals(new BigDecimal("0.00"), accounts.getBalance("Alice1-"));
        transactions.deposit("Alice1-", new BigDecimal("15.50"));
        assertEquals(new BigDecimal("15.50"), accounts.getBalance("Alice1-"));
    }

    @Test
    void getBalanceRejectsMissingOrBlankAccount() {
        assertThrows(BankingException.class, () -> accounts.getBalance("Missing1-"));
        assertThrows(BankingException.class, () -> accounts.getBalance(""));
        assertThrows(BankingException.class, () -> accounts.getBalance(null));
    }
}
