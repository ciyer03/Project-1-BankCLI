package org.bankofcli.repository.sqlite;

import java.math.BigDecimal;
import java.util.Optional;

import org.bankofcli.model.Account;
import org.bankofcli.repository.AccountRepository;

public class SQLiteAccountRepository implements AccountRepository {

    /**
     * Creates a new account with the details contained in the account object.
     * 
     * @param account The account object containing the details to be saved.
     * @return Returns the newly created account.
     * @see AccountRepository#create(Account)
     */
    @Override
    public Account create(Account account) {
        // TODO: Make sure to create a UUID for the Account ID. Otherwise, it won't be accepted into the database.
        throw new UnsupportedOperationException("Unimplemented method 'create'");
    }

    /**
     * Finds and returns, if exists, an account with the specified account ID.
     * 
     * @param accountId The account ID of the account to be fetched.
     * @return The Account object if it exists. An empty object otherwise.
     * @see AccountRepository#findById(String)
     */
    @Override
    public Optional<Account> findById(String accountId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'findById'");
    }

    /**
     * Returns a boolean indicating if an account with the specified account ID exists.
     * 
     * @param accountId The account ID of the account to be searched.
     * @return True if the account exists. False otherwise.
     * @see AccountRepository#existsById(String)
     */
    @Override
    public boolean existsById(String accountId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'existsById'");
    }

    /**
     * Returns the current balance of the account specified by the account ID.
     * 
     * @param accountId The account ID of the account for which the balance needs to be fetched.
     * @return The current balance of the account.
     * @see AccountRepository#getBalance(String)
     */
    @Override
    public BigDecimal getBalance(String accountId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getBalance'");
    }
}
