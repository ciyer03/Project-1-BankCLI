package org.bankofcli.service.impl;

import java.util.Objects;
import java.util.UUID;

import org.bankofcli.exceptions.BankingException;
import org.bankofcli.model.Account;
import org.bankofcli.repository.AccountRepository;
import org.bankofcli.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthServiceImpl implements AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private final AccountRepository accounts;

    public AuthServiceImpl(AccountRepository accounts) {
        this.accounts = Objects.requireNonNull(accounts);
    }

    @Override
    public Account register(int pin) {
        if (pin < 0 || pin > 9999) {
            throw new BankingException("PIN must be four digits, from 0000 to 9999.");
        }
        String accountId = UUID.randomUUID().toString();
        Account account = accounts.create(new Account("", "", accountId, pin));
        log.info("Registration succeeded!");
        return account;
    }

    @Override
    public Account login(String accountId, int pin) {
        // Use the same response for unknown accounts and incorrect PINs.
        Account account = accountId == null ? null : accounts.findById(accountId).orElse(null);
        if (account == null || pin < 0 || pin > 9999 || account.getPIN() != pin) {
            log.warn("Login failed");
            throw new BankingException("Invalid account ID or PIN.");
        }
        log.info("Login succeeded!");
        return account;
    }
}
