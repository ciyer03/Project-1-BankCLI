package org.bankofcli;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import org.bankofcli.repository.memory.InMemoryBankRepository;
import org.bankofcli.service.impl.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BankApplicationTest {
    private String run(String input) {
        var repository = new InMemoryBankRepository();
        repository.create(new org.bankofcli.model.Account("", "", "Alice1-", 1234));
        repository.create(new org.bankofcli.model.Account("", "", "Bobby2#", 4321));
        var output = new ByteArrayOutputStream();
        new BankApplication(new AuthServiceImpl(repository), new AccountServiceImpl(repository),
                new TransactionServiceImpl(repository, repository), new Scanner(input),
                new PrintStream(output, true, StandardCharsets.UTF_8)).run();
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    void registrationRejectsBlankNames() {
        for (String names : new String[] {"\nSmith\n", "Alice\n   \n"}) {
            String output = run("1\n" + names + "1234\n8\n");
            assertTrue(output.contains("First name: "));
            assertTrue(output.contains("Last name: "));
            assertTrue(output.contains("First and last name are required."));
            assertFalse(output.contains("Registration successful."));
        }
    }

    @Test
    void registrationDisplaysGeneratedUuidAndAcceptsLeadingZeroPins() {
        for (String pin : new String[] {"0000", "0001", "0123", "0999"}) {
            String output = run("1\nAlice\nSmith\n" + pin + "\n8\n");
            assertTrue(output.contains("Registration successful."), pin);
            var match = java.util.regex.Pattern.compile("Your Account ID: ([A-Za-z0-9_-]{22})").matcher(output);
            assertTrue(match.find());
            assertEquals(22, match.group(1).length());
            assertFalse(output.contains("Choose Account ID"));
        }
    }

    @Test
    void pinInputMustStillBeExactlyFourDigits() {
        for (String pin : new String[] {"0", "000", "00000", "-001", "00a0"}) {
            String output = run("1\nAlice\nSmith\n" + pin + "\n8\n");
            assertTrue(output.contains("PIN must be four digits"), pin);
            assertFalse(output.contains("Registration successful."), pin);
        }
        String output = run("2\nAlice1-\n0\n3\n8\n");
        assertFalse(output.contains("Login successful."));
        assertTrue(output.contains("Please register or log in first."));
    }

    @Test
    void registrationRequiresLoginAndWrongPinDoesNotUnlockMenu() {
        String output = run("1\nAlice\nSmith\n1234\n3\n2\nAlice1-\n9999\n3\n8\n");
        assertTrue(output.contains("Registration successful. Please log in."));
        assertTrue(output.contains("Invalid account ID or PIN."));
        assertFalse(output.contains("Your Balance is:"));
        assertFalse(output.contains("Login successful."));
    }

    @Test
    void successfulLoginEnablesBankingAndLogoutClearsSession() {
        String output = run("2\nAlice1-\n1234\n4\n20.50\n5\n1234\n0.50\n3\n7\n10\n9\n3\n8\n");
        assertTrue(output.contains("Login successful."));
        assertTrue(output.contains("Your Balance is: $20.00"));
        assertTrue(output.contains("Transaction Type: WITHDRAW"));
        assertTrue(output.contains("Logged out."));
        assertTrue(output.contains("Please register or log in first."));
    }

    @Test
    void allBankingOptionsAreBlockedBeforeLogin() {
        String output = run("3\n4\n5\n6\n7\n9\n8\n");
        assertEquals(6, output.split("Please register or log in first.", -1).length - 1);
        assertFalse(output.contains("Amount:"));
    }

    @Test
    void badInputIsRecoverable() {
        String output = run("wrong\n1\nAlice\nSmith\nabc\n2\nAlice1-\n1234\n4\nnope\n4\n1.001\n4\n0\n3\n8\n");
        assertTrue(output.contains("Invalid option."));
        assertTrue(output.contains("PIN must be four digits"));
        assertTrue(output.contains("Enter a positive amount"));
        assertTrue(output.contains("Amount must be greater than zero."));
        assertTrue(output.contains("Your Balance is: $0.00"));
    }

    @Test
    void logoutAndLoginAsAnotherAccountDoesNotReusePreviousBalance() {
        String output = run("2\nAlice1-\n1234\n4\n10\n9\n2\nBobby2#\n4321\n3\n8\n");
        assertTrue(output.contains("Your Balance is: $0.00"));
    }

    @Test
    void depositThroughMenuUpdatesBalance() {
        String output = run("2\nAlice1-\n1234\n4\n20.50\n4\n0.50\n3\n8\n");
        assertTrue(output.contains("Login successful."));
        assertEquals(2, output.split("Deposit successful.", -1).length - 1);
        assertTrue(output.contains("Your Balance is: $21.00"));
    }

    @Test
    void transferThroughMenuCreditsRecipient() {
        String output = run("2\nAlice1-\n1234\n4\n10\n6\nBobby2#\n4\n9\n2\nBobby2#\n4321\n3\n8\n");
        assertTrue(output.contains("Transfer successful."));
        assertTrue(output.contains("Your Balance is: $4.00"));
    }

    @Test
    void endOfInputAtPromptExitsCleanly() {
        assertDoesNotThrow(() -> run(""));
        assertTrue(run("2\nAlice1-\n").contains("Input closed."));
    }

    @Test
    void withdrawThroughMenuRejectsInsufficientBalanceAndKeepsBalanceUnchanged() {
        String output = run("2\nAlice1-\n1234\n4\n10\n5\n1234\n20\n3\n8\n");
        assertTrue(output.contains("Insufficient balance"));
        assertTrue(output.contains("Your Balance is: $10.00"));
    }

    @Test
    void transferThroughMenuRejectsMissingDestinationAccountAndKeepsBalanceUnchanged() {
        String output = run("2\nAlice1-\n1234\n4\n10\n6\nMissing1-\n5\n3\n8\n");
        assertTrue(output.contains("does not exist"));
        assertTrue(output.contains("Your Balance is: $10.00"));
    }

    @Test
    void transactionHistoryThroughMenuRejectsNonPositiveCount() {
        String output = run("2\nAlice1-\n1234\n4\n1\n7\n0\n7\n-1\n8\n");
        assertEquals(2, output.split("Enter a positive whole number for the number of transactions to show.", -1).length - 1);
    }
}
