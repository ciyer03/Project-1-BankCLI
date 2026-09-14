package org.bankofcli.repository;

import java.math.BigDecimal;
import java.util.Optional;

import org.bankofcli.model.Account;

public interface AccountRepository {
    /**
     * Creates a new account with the details contained in the account object.
     * 
     * @param account The account object containing the details to be saved.
     * @return Returns the newly created account.
     */
    Account create(Account account);

    /**
     * Finds and returns, if exists, an account with the specified account ID.
     * 
     * @param accountId The account ID of the account to be fetched.
     * @return The Account object if it exists. An empty object otherwise.
     */
    Optional<Account> findById(String accountId);

    /**
     * Returns a boolean indicating if an account with the specified account ID exists.
     * 
     * @param accountId The account ID of the account to be searched.
     * @return True if the account exists. False otherwise.
     */
    boolean existsById(String accountId);

    /**
     * Returns the current balance of the account specified by the account ID.
     * 
     * @param accountId The account ID of the account for which the balance needs to be fetched.
     * @return The current balance of the account.
     */
    BigDecimal getBalance(String accountId);
}