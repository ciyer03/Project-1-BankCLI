PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS accounts (
    accountId TEXT PRIMARY KEY CHECK (
        LENGTH(accountId) IN (22, 36)
    ),
    firstName TEXT NOT NULL,
    lastName TEXT NOT NULL,
    PIN TEXT NOT NULL CHECK (
        LENGTH(PIN) = 4 AND PIN GLOB '[0-9][0-9][0-9][0-9]'
    ),
    balance NUMERIC NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    accountId TEXT NOT NULL REFERENCES accounts(accountId),
    type TEXT NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAW', 'TRANSFER_IN', 'TRANSFER_OUT')),
    amount NUMERIC NOT NULL,
    timestamp TEXT NOT NULL
);
