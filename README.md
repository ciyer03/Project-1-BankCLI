# Bank of CLI

Run org.bankofcli.BankApplication with Java 21. Build and run the JUnit suite
with mvn test (or the IntelliJ Maven test lifecycle).

Choose 1 to register and enter your first name, last name, and a four-digit PIN.
Both names are required and saved with your account ID and PIN. Registration generates an account
ID with java.util.UUID, encodes it as 22 URL-safe Base64 characters (case-sensitive),
and displays it. The encoding preserves all UUID bits. Existing 36-character IDs
remain valid. Save that ID: choose 2 to log in using
the ID and PIN. Registration does not automatically log you in.
Option 9 logs out, and option 8 exits. Invalid input returns to the menu.

Accounts are saved in data/bank.db using SQLiteConnectionFactory and
SQLiteAccountRepository. Startup creates missing tables without deleting existing
accounts. Registration starts with a zero balance. PIN input must contain exactly
four digits, including leading zeros (0000-9999); the database preserves that format.
The model still uses an integer PIN. PINs are stored as plain text and input is
visible in the console.

Account registration, login, and balance lookup use SQLite. SQLite deposits,
withdrawals, transfers, and transaction history are not implemented yet; their
menu options currently report that the operation could not be completed.
The in-memory transaction implementation remains available for service tests.

SLF4J and Logback write application lifecycle, authentication outcomes, and
transaction outcomes to logs/bank.log. Logs do not include PINs, entered account IDs,
balances, or raw exception messages.

JUnit covers UUID generation, persistent login across fresh SQLite connections,
leading-zero PIN storage, invalid credentials, CLI registration/login/logout and
input validation, plus the existing in-memory transaction rules.
SQLite tests use temporary databases and do not modify data/bank.db.
