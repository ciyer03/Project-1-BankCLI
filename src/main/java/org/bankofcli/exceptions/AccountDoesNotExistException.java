package org.bankofcli.exceptions;

/**
 * An exception that is thrown when the referred account does not exist.
 */
public class AccountDoesNotExistException extends RuntimeException {
    /**
     * An exception that is thrown when the referred account does not exist.
     * 
     * @param message The message to be sent with the exception.
     */
    public AccountDoesNotExistException(String message) {
        super(message);
    }
}
