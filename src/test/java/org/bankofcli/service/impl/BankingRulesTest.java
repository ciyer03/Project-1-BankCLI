package org.bankofcli.service.impl;

import java.math.BigDecimal;

import org.bankofcli.exceptions.BankingException;
import org.bankofcli.model.Account;
import org.bankofcli.repository.AccountRepository;
import org.bankofcli.repository.memory.InMemoryBankRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BankingRulesTest {
    private AccountRepository accounts;

    @BeforeEach
    void setup() {
        InMemoryBankRepository repository = new InMemoryBankRepository();
        repository.create(new Account("", "", "Alice1-", 1234));
        accounts = repository;
    }

    @Test
    void existingAccountAcceptsKnownAccountId() {
        assertDoesNotThrow(() -> BankingRules.existingAccount(accounts, "Alice1-"));
    }

    @Test
    void existingAccountRejectsNullBlankOrUnknownId() {
        assertThrows(BankingException.class, () -> BankingRules.existingAccount(accounts, null));
        assertThrows(BankingException.class, () -> BankingRules.existingAccount(accounts, ""));
        assertThrows(BankingException.class, () -> BankingRules.existingAccount(accounts, "Missing1-"));
    }

    @Test
    void amountAcceptsPositiveValueAndNormalizesScale() {
        assertEquals(new BigDecimal("10.00"), BankingRules.amount(BigDecimal.TEN));
        assertEquals(new BigDecimal("5.50"), BankingRules.amount(new BigDecimal("5.5")));
    }

    @Test
    void amountRejectsNullZeroNegativeAndFractionalCents() {
        for (BigDecimal amount : new BigDecimal[] {null, BigDecimal.ZERO, new BigDecimal("-1"), new BigDecimal("1.001")}) {
            assertThrows(BankingException.class, () -> BankingRules.amount(amount));
        }
    }
}
