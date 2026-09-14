package org.bankofcli.service;

import java.math.BigDecimal;

public interface AccountService {
    /**
     * Returns the current balance of the account specified by the account ID.
     * 
     * @param accountId The account ID of the account for which the balance needs to be fetched.
     * @return The current balance of the account.
     */
    BigDecimal getBalance(String accountId);
}
