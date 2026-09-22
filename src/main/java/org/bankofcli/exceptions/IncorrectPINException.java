package org.bankofcli.exceptions;

/**
 * An exception that is thrown when the PIN for an account is wrong.
 */
public class IncorrectPINException extends RuntimeException {
    /**
     * An exception that is thrown when the PIN for an account is wrong.
     * 
     * The message is constructed as such:
     * {@code The entered PIN for the account `accountId` is incorrect.}
     * 
     * @param accountId The accountId for which the PIN is wrong.
     */
    public IncorrectPINException(String accountId) {
        super("The entered PIN for the account \"" + accountId + "\" is incorrect.");
    }
}
