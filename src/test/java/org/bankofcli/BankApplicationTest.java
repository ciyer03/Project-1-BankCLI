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
        var output = new ByteArrayOutputStream();
        new BankApplication(new AuthServiceImpl(repository), new AccountServiceImpl(repository),
                new TransactionServiceImpl(repository, repository), new Scanner(input),
                new PrintStream(output, true, StandardCharsets.UTF_8)).run();
        return output.toString(StandardCharsets.UTF_8);
    }

    @Test
    void registrationExplainsAccountIdRequirementsAndAllowsRetry() {
        String output = run("1\nalice\n0000\n1\nAlice1$\n0000\n2\nAlice1$\n0000\n3\n8\n");
        assertTrue(output.contains("at least one uppercase letter, one lowercase letter, one number"));
        assertTrue(output.contains("one special character"));
        assertTrue(output.contains("Login successful."));
        assertTrue(output.contains("Your Balance is: $0.00"));
    }

    @Test
    void leadingZeroPinsCanRegisterAndLogin() {
        for (String pin : new String[] {"0000", "0001", "0123", "0999"}) {
            String output = run("1\nAlice1-\n" + pin + "\n2\nAlice1-\n" + pin + "\n3\n8\n");
            assertTrue(output.contains("Registration successful."), pin);
            assertTrue(output.contains("Login successful."), pin);
            assertTrue(output.contains("Your Balance is: $0.00"), pin);
        }
    }

    @Test
    void pinInputMustStillBeExactlyFourDigits() {
        for (String pin : new String[] {"0", "000", "00000", "-001", "00a0"}) {
            String output = run("1\nAlice1-\n" + pin + "\n8\n");
            assertTrue(output.contains("PIN must be four digits"), pin);
            assertFalse(output.contains("Registration successful."), pin);
        }
        String output = run("1\nAlice1-\n0000\n2\nAlice1-\n0\n3\n8\n");
        assertFalse(output.contains("Login successful."));
        assertTrue(output.contains("Please register or log in first."));
    }

    @Test
    void registrationRequiresLoginAndWrongPinDoesNotUnlockMenu() {
        String output = run("1\nAlice1-\n1234\n3\n2\nAlice1-\n9999\n3\n8\n");
        assertTrue(output.contains("Registration successful. Please log in."));
        assertTrue(output.contains("Invalid account ID or PIN."));
        assertFalse(output.contains("Your Balance is:"));
        assertFalse(output.contains("Login successful."));
    }

    @Test
    void successfulLoginEnablesBankingAndLogoutClearsSession() {
        String output = run("1\nAlice1-\n1234\n2\nAlice1-\n1234\n4\n20.50\n5\n0.50\n3\n7\n10\n9\n3\n8\n");
        assertTrue(output.contains("Login successful."));
        assertTrue(output.contains("Your Balance is: $20.50"));
        assertTrue(output.contains("Transaction Type: DEPOSIT"));
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
        String output = run("wrong\n1\nAlice1-\nabc\n1\nAlice1-\n1234\n2\nAlice1-\n1234\n4\nnope\n4\n1.001\n4\n0\n3\n8\n");
        assertTrue(output.contains("Invalid option."));
        assertTrue(output.contains("PIN must be four digits"));
        assertTrue(output.contains("Enter a positive amount"));
        assertTrue(output.contains("Amount must be greater than zero."));
        assertTrue(output.contains("Your Balance is: $0.00"));
    }

    @Test
    void logoutAndLoginAsAnotherAccountDoesNotReusePreviousBalance() {
        String output = run("1\nAlice1-\n1234\n2\nAlice1-\n1234\n4\n10\n9\n1\nBobby2#\n4321\n2\nBobby2#\n4321\n3\n8\n");
        assertTrue(output.contains("Your Balance is: $0.00"));
    }

    @Test
    void transferThroughMenuCreditsRecipient() {
        String output = run("1\nAlice1-\n1234\n1\nBobby2#\n4321\n2\nAlice1-\n1234\n4\n10\n6\nBobby2#\n4\n9\n2\nBobby2#\n4321\n3\n8\n");
        assertTrue(output.contains("Transfer successful."));
        assertTrue(output.contains("Your Balance is: $4.00"));
    }

    @Test
    void endOfInputAtPromptExitsCleanly() {
        assertDoesNotThrow(() -> run(""));
        assertTrue(run("2\nAlice1-\n").contains("Input closed."));
    }
}
