package org.bankofcli;

import java.io.PrintStream;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Scanner;

import org.bankofcli.exceptions.BankingException;
import org.bankofcli.exceptions.InsufficientBalanceException;
import org.bankofcli.repository.memory.InMemoryBankRepository;
import org.bankofcli.service.*;
import org.bankofcli.service.impl.*;
import org.bankofcli.utils.SQLiteConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BankApplication {
    private static final Logger log = LoggerFactory.getLogger(BankApplication.class);
    private final AuthService auth;
    private final AccountService accounts;
    private final TransactionService transactions;
    private final Scanner scanner;
    private final PrintStream out;

    public BankApplication(AuthService auth, AccountService accounts,
                           TransactionService transactions, Scanner scanner, PrintStream out) {
        this.auth = auth;
        this.accounts = accounts;
        this.transactions = transactions;
        this.scanner = scanner;
        this.out = out;
    }

    public void run() {
        String accountId = null;
        out.println("Welcome to the Bank Of CLI!");
        out.println("Accounts and transactions are stored for this run only.");
        log.info("Application started");
        try {
            while (true) {
                if (accountId == null) {
                    out.println("1. Register\n2. Login\n8. Exit");
                } else {
                    out.println("3. Check Balance\n4. Deposit\n5. Withdraw\n6. Transfer"
                            + "\n7. Transaction History\n8. Exit\n9. Logout");
                }
                out.print("Enter choice: ");
                if (!scanner.hasNextLine()) return;
                String choice = scanner.nextLine().trim();
                if (accountId == null && choice.matches("[3-79]")) {
                    out.println("Please register or log in first.");
                    continue;
                }
                try {
                    switch (choice) {
                        case "1":
                            if (accountId != null) {
                                out.println("Please log out before registering another account.");
                                break;
                            }
                            String newId = prompt("Choose Account ID: ");
                            auth.register(newId, readPin());
                            out.println("Registration successful. Please log in.");
                            break;
                        case "2":
                            if (accountId != null) {
                                out.println("You are already logged in.");
                                break;
                            }
                            String loginId = prompt("Account ID: ");
                            accountId = auth.login(loginId, readPin()).getAccountId();
                            out.println("Login successful.");
                            break;
                        case "3":
                            out.println("Your Balance is: $" + accounts.getBalance(accountId).toPlainString());
                            break;
                        case "4":
                            transactions.deposit(accountId, readAmount());
                            out.println("Deposit successful.");
                            break;
                        case "5":
                            transactions.withdraw(accountId, readAmount());
                            out.println("Withdrawal successful.");
                            break;
                        case "6":
                            String destination = prompt("Destination Account ID: ");
                            transactions.transfer(accountId, destination, readAmount());
                            out.println("Transfer successful.");
                            break;
                        case "7":
                            int limit = readLimit();
                            var history = transactions.getRecentTransactions(accountId, limit);
                            if (history.isEmpty()) out.println("No transactions yet.");
                            history.forEach(out::println);
                            break;
                        case "8":
                            out.println("Exiting Bank of CLI. Thank you!");
                            return;
                        case "9":
                            accountId = null;
                            log.info("Logout succeeded");
                            out.println("Logged out.");
                            break;
                        default:
                            out.println("Invalid option. Please choose an option shown in the menu.");
                    }
                } catch (BankingException e) {
                    log.warn("Banking request rejected.");
                    out.println(e.getMessage());
                } catch (InsufficientBalanceException e) {
                    log.warn("Insufficient balance for requested operation.");
                    out.println(e.getMessage());
                } catch (RuntimeException e) {
                    if (e instanceof NoSuchElementException) throw e;
                    // Do not log exception messages: repository/input errors may contain secrets.
                    log.error("Banking operation failed unexpectedly.");
                    out.println("Unable to complete the operation. Please try again.");
                }
            }
        } catch (NoSuchElementException e) {
            out.println("Input closed. Exiting Bank of CLI.");
        } finally {
            log.info("Application stopped");
        }
    }

    private String prompt(String message) {
        out.print(message);
        return scanner.nextLine().trim();
    }

    private int readPin() {
        String pin = prompt("PIN (0000-9999): ");
        if (!pin.matches("[0-9]{4}")) {
            throw new BankingException("PIN must be four digits, from 0000 to 9999.");
        }
        return Integer.parseInt(pin);
    }

    private int readLimit() {
        String limit = prompt("How many transactions to show: ");
        if (!limit.matches("[1-9][0-9]*")) {
            throw new BankingException("Enter a positive whole number for the number of transactions to show.");
        }
        return Integer.parseInt(limit);
    }

    private BigDecimal readAmount() {
        String amount = prompt("Amount: ");
        if (!amount.matches("[0-9]+(\\.[0-9]{1,2})?")) {
            throw new BankingException("Enter a positive amount with at most two decimal places.");
        }
        return new BigDecimal(amount);
    }

    public static boolean returnOrExit(Scanner scanner) {
        while (scanner.hasNextLine()) {
            System.out.println("1. Return to Main Menu\n2. Exit");
            switch (scanner.nextLine().trim()) {
                case "1": return true;
                case "2": return false;
                default: System.out.println("Invalid option. Please choose 1 or 2.");
            }
        }
        return false;
    }

    public static void main(String[] args) {
        SQLiteConnectionFactory.initializeDatabase();

        InMemoryBankRepository repository = new InMemoryBankRepository();
        try (Scanner scanner = new Scanner(System.in)) {
            new BankApplication(new AuthServiceImpl(repository), new AccountServiceImpl(repository),
                    new TransactionServiceImpl(repository, repository), scanner, System.out).run();
        }
    }
}
