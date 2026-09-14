package org.bankofcli.exceptions;

/**
 * An exception that is thrown when there is insufficient balance 
 * for performing an account operation.
 */
public class InsufficientBalanceException extends Exception {
    /**
     * An exception that is thrown when there is insufficient balance 
     * for performing an account operation.
     * 
     * @param message The message to be sent with the exception.
     */
    public InsufficientBalanceException(String message) {
        super(message);
    }
}
