package org.bankofcli.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.bankofcli.exceptions.BankingException;
import org.bankofcli.repository.AccountRepository;

public final class BankingRules {
    private BankingRules() {}

    public static void existingAccount(AccountRepository accounts, String id) {
        if (id == null || id.isBlank()) {
            throw new BankingException("Account does not exist.");
        }
        if (!accounts.existsById(id)) {
            throw new BankingException("Account does not exist.");
        }
    }

    public static BigDecimal amount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BankingException("Amount must be greater than zero.");
        }
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException e) {
            throw new BankingException("Amount must contain no fractions of a cent.");
        }
    }
}
