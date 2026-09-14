package org.bankofcli;

import java.math.BigDecimal;
import org.bankofcli.repository.memory.InMemoryBankRepository;
import org.bankofcli.service.impl.*;
import org.bankofcli.exceptions.BankingException;
import org.bankofcli.exceptions.InsufficientBalanceException;
import org.bankofcli.model.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BankingServiceTest {
    private InMemoryBankRepository repository;
    private AuthServiceImpl auth;
    private AccountServiceImpl accounts;
    private TransactionServiceImpl transactions;

    @BeforeEach
    void setup() {
        repository = new InMemoryBankRepository();
        auth = new AuthServiceImpl(repository);
        accounts = new AccountServiceImpl(repository);
        transactions = new TransactionServiceImpl(repository, repository);
        auth.register("Alice1-", 1234);
        auth.register("Bobby2#", 4321);
    }

    @Test
    void accountIdRequiresEveryCharacterType() {
        for (String id : new String[] {"alice1-", "ALICE1-", "Alice-", "Alice1",
                "Aa1 ", "Aa1- name", "Aa1-\n", "Aa1-" + "a".repeat(29)}) {
            assertThrows(BankingException.class, () -> auth.register(id, 1234), id);
            assertFalse(repository.existsById(id));
        }
    }

    @Test
    void accountIdsAcceptSpecialCharactersAndLengthBoundaries() {
        for (String id : new String[] {"Aa1-", "Aa1$", "Aa1#", "Aa1!", "Aa1_",
                "Aa1-" + "a".repeat(28)}) {
            auth.register(id, 0);
            assertEquals(id, auth.login(id, 0).getAccountId());
        }
    }

    @Test
    void acceptsNumericValuesOfLeadingZeroPins() {
        for (int pin : new int[] {0, 1, 123, 999}) {
            String id = "User-" + pin;
            auth.register(id, pin);
            assertEquals(id, auth.login(id, pin).getAccountId());
            assertThrows(BankingException.class, () -> auth.login(id, 9999));
        }
    }

    @Test
    void registrationStartsAtZeroAndAuthenticates() {
        assertEquals("Alice1-", auth.login("Alice1-", 1234).getAccountId());
        assertEquals(new BigDecimal("0.00"), accounts.getBalance("Alice1-"));
    }

    @Test
    void duplicateRegistrationDoesNotReplaceCredentialsOrBalance() {
        transactions.deposit("Alice1-", new BigDecimal("10"));
        assertThrows(BankingException.class, () -> auth.register("Alice1-", 9999));
        assertEquals("Alice1-", auth.login("Alice1-", 1234).getAccountId());
        assertEquals(new BigDecimal("10.00"), accounts.getBalance("Alice1-"));
    }

    @Test
    void rejectsInvalidAccountIdsAndPins() {
        for (String id : new String[] {null, "", "ab", "white space", "bad\naccount", "a".repeat(33)}) {
            assertThrows(BankingException.class, () -> auth.register(id, 1234));
        }
        for (int pin : new int[] {-1, 10000}) {
            assertThrows(BankingException.class, () -> auth.register("Valid1-", pin));
        }
        assertFalse(repository.existsById("Valid1-"));
    }

    @Test
    void unknownAccountAndWrongPinHaveSameError() {
        var wrong = assertThrows(BankingException.class, () -> auth.login("Alice1-", 1111));
        var unknown = assertThrows(BankingException.class, () -> auth.login("Unknown1-", 1234));
        assertEquals(wrong.getMessage(), unknown.getMessage());
        assertThrows(BankingException.class, () -> auth.login(null, 1234));
    }

    @Test
    void depositAndWithdrawalUseExactCents() {
        transactions.deposit("Alice1-", new BigDecimal("0.10"));
        transactions.deposit("Alice1-", new BigDecimal("0.20"));
        assertEquals(new BigDecimal("0.30"), accounts.getBalance("Alice1-"));
        repository.withdraw("Alice1-", new BigDecimal("0.30"));
        assertEquals(new BigDecimal("0.00"), accounts.getBalance("Alice1-"));
        assertEquals(TransactionType.WITHDRAW, transactions.getRecentTransactions("Alice1-", 10).getFirst().getType());
    }

    @Test
    void everyMutationRejectsInvalidAmountsWithoutChanges() {
        for (BigDecimal amount : new BigDecimal[] {null, BigDecimal.ZERO, new BigDecimal("-1"), new BigDecimal("1.001")}) {
            assertThrows(BankingException.class, () -> transactions.deposit("Alice1-", amount));
            assertThrows(BankingException.class, () -> repository.withdraw("Alice1-", amount));
            assertThrows(BankingException.class, () -> transactions.transfer("Alice1-", "Bobby2#", amount));
        }
        assertEquals(new BigDecimal("0.00"), accounts.getBalance("Alice1-"));
        assertTrue(transactions.getRecentTransactions("Alice1-", 10).isEmpty());
        assertTrue(transactions.getRecentTransactions("Bobby2#", 10).isEmpty());
    }

    @Test
    void rejectsMissingAccountsAcrossOperations() {
        assertThrows(BankingException.class, () -> accounts.getBalance("Missing1-"));
        assertThrows(BankingException.class, () -> transactions.deposit("Missing1-", BigDecimal.ONE));
        assertThrows(BankingException.class, () -> repository.withdraw("Missing1-", BigDecimal.ONE));
        assertThrows(BankingException.class, () -> transactions.transfer("Missing1-", "Alice1-", BigDecimal.ONE));
        assertThrows(BankingException.class, () -> transactions.getRecentTransactions("Missing1-", 10));
    }

    @Test
    void transferUpdatesBothBalancesAndBothHistories() {
        transactions.deposit("Alice1-", new BigDecimal("100"));
        assertDoesNotThrow(() -> transactions.transfer("Alice1-", "Bobby2#", new BigDecimal("25.25")));
        assertEquals(new BigDecimal("74.75"), accounts.getBalance("Alice1-"));
        assertEquals(new BigDecimal("25.25"), accounts.getBalance("Bobby2#"));
        var outgoing = transactions.getRecentTransactions("Alice1-", 10).getFirst();
        var incoming = transactions.getRecentTransactions("Bobby2#", 10).getFirst();
        assertEquals(TransactionType.TRANSFER_OUT, outgoing.getType());
        assertEquals(TransactionType.TRANSFER_IN, incoming.getType());
        assertEquals(outgoing.getAmount(), incoming.getAmount());
        assertNotEquals(outgoing.getId(), incoming.getId());
    }

    @Test
    void rejectedTransfersAndOverdraftsLeaveBalancesAndHistoryIntact() {
        transactions.deposit("Alice1-", BigDecimal.TEN);
        assertThrows(BankingException.class, () -> repository.withdraw("Alice1-", new BigDecimal("10.01")));
        assertThrows(BankingException.class, () -> transactions.transfer("Alice1-", "Bobby2#", new BigDecimal("11")));
        assertThrows(BankingException.class, () -> transactions.transfer("Alice1-", "Missing1-", BigDecimal.ONE));
        assertThrows(BankingException.class, () -> transactions.transfer("Alice1-", "Alice1-", BigDecimal.ONE));
        assertEquals(new BigDecimal("10.00"), accounts.getBalance("Alice1-"));
        assertEquals(new BigDecimal("0.00"), accounts.getBalance("Bobby2#"));
        assertEquals(1, transactions.getRecentTransactions("Alice1-", 10).size());
        assertTrue(transactions.getRecentTransactions("Bobby2#", 10).isEmpty());
    }

    @Test
    void historyIsLatestTenAndOnlyForRequestedAccount() {
        for (int i = 1; i <= 12; i++) transactions.deposit("Alice1-", BigDecimal.valueOf(i));
        transactions.deposit("Bobby2#", BigDecimal.ONE);
        var history = transactions.getRecentTransactions("Alice1-", 10);
        assertEquals(10, history.size());
        assertEquals(new BigDecimal("12.00"), history.getFirst().getAmount());
        assertEquals(new BigDecimal("3.00"), history.getLast().getAmount());
        assertTrue(history.stream().allMatch(t -> t.getAccountId().equals("Alice1-")));
        assertThrows(UnsupportedOperationException.class, history::clear);
    }

    @Test
    void repositoryRechecksFundsAndDestinationBeforeMutation() {
        repository.deposit("Alice1-", BigDecimal.TEN);
        assertThrows(BankingException.class, () -> repository.transfer("Alice1-", "Missing1-", BigDecimal.ONE));
        assertThrows(BankingException.class, () -> repository.transfer("Alice1-", "Bobby2#", new BigDecimal("11")));
        assertEquals(new BigDecimal("10.00"), repository.getBalance("Alice1-"));
        assertEquals(1, repository.getRecentTransactions("Alice1-", 10).size());
    }
}
