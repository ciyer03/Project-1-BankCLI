package org.bankofcli.service.impl;

import java.math.BigDecimal;
import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.util.Optional;

import org.bankofcli.exceptions.AccountDoesNotExistException;
import org.bankofcli.exceptions.BankingException;
import org.bankofcli.exceptions.IncorrectPINException;
import org.bankofcli.exceptions.InsufficientBalanceException;
import org.bankofcli.model.Account;
import org.bankofcli.model.Transaction;
import org.bankofcli.model.TransactionType;
import org.bankofcli.repository.AccountRepository;
import org.bankofcli.repository.TransactionRepository;
import org.bankofcli.repository.memory.InMemoryBankRepository;
import org.bankofcli.service.AccountService;
import org.bankofcli.service.TransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransactionServiceImplTest {
    private InMemoryBankRepository repository;
    private AccountService accounts;
    private TransactionService transactions;

    @BeforeEach
    void setup() {
        repository = new InMemoryBankRepository();
        accounts = new AccountServiceImpl(repository);
        transactions = new TransactionServiceImpl(repository, repository);
        repository.create(new Account("", "", "Alice1-", 1234));
        repository.create(new Account("", "", "Bobby2#", 4321));
    }

    @Test
    void transferUpdatesBothBalancesAndBothHistories() {
        transactions.deposit("Alice1-", new BigDecimal("100"));
        assertDoesNotThrow(() -> transactions.transfer("Alice1-", "Bobby2#", new BigDecimal("25.25")));
        assertEquals(new BigDecimal("74.75"), accounts.getBalance("Alice1-"));
        assertEquals(new BigDecimal("25.25"), accounts.getBalance("Bobby2#"));
        Transaction outgoing = transactions.getRecentTransactions("Alice1-", 10).getFirst();
        Transaction incoming = transactions.getRecentTransactions("Bobby2#", 10).getFirst();
        assertEquals(TransactionType.TRANSFER_OUT, outgoing.getType());
        assertEquals(TransactionType.TRANSFER_IN, incoming.getType());
        assertEquals(outgoing.getAmount(), incoming.getAmount());
        assertNotEquals(outgoing.getId(), incoming.getId());
    }

    @Test
    void historyIsLatestTenAndOnlyForRequestedAccount() {
        for (int i = 1; i <= 12; i++) {
            transactions.deposit("Alice1-", BigDecimal.valueOf(i));
        }

        transactions.deposit("Bobby2#", BigDecimal.ONE);
        List<Transaction> history = transactions.getRecentTransactions("Alice1-", 10);
        assertEquals(10, history.size());
        assertEquals(new BigDecimal("12.00"), history.getFirst().getAmount());
        assertEquals(new BigDecimal("3.00"), history.getLast().getAmount());
        assertTrue(history.stream().allMatch(t -> t.getAccountId().equals("Alice1-")));
    }

    @Test
    void validationDoesNotCallTransactionRepository() {
        AccountRepository mockedAccounts = mock(AccountRepository.class);
        TransactionRepository mockedRepository = mock(TransactionRepository.class);
        when(mockedAccounts.existsById("Alice1-")).thenReturn(true);
        when(mockedAccounts.existsById("Bobby2#")).thenReturn(true);
        when(mockedAccounts.findById("Alice1-")).thenReturn(Optional.of(new Account("", "", "Alice1-", 1234)));
        when(mockedAccounts.getBalance("Alice1-")).thenReturn(BigDecimal.TEN);
        TransactionService service = new TransactionServiceImpl(mockedAccounts, mockedRepository);
        assertThrows(BankingException.class, () -> service.deposit("Alice1-", BigDecimal.ZERO));
        assertThrows(InsufficientBalanceException.class, () -> service.withdraw("Alice1-", 1234, new BigDecimal("11")));
        assertThrows(InsufficientBalanceException.class, () -> service.transfer("Alice1-", "Bobby2#", new BigDecimal("11")));
        assertThrows(IllegalArgumentException.class, () -> service.transfer("Alice1-", "Alice1-", BigDecimal.ONE));
        verifyNoInteractions(mockedRepository);
    }

    @Test
    void validTransferDelegatesOneAtomicOperation() {
        AccountRepository mockedAccounts = mock(AccountRepository.class);
        TransactionRepository mockedRepository = mock(TransactionRepository.class);
        when(mockedAccounts.existsById("Alice1-")).thenReturn(true);
        when(mockedAccounts.existsById("Bobby2#")).thenReturn(true);
        when(mockedAccounts.getBalance("Alice1-")).thenReturn(BigDecimal.TEN);
        TransactionService service = new TransactionServiceImpl(mockedAccounts, mockedRepository);
        assertDoesNotThrow(() -> service.transfer("Alice1-", "Bobby2#", BigDecimal.TEN));
        assertDoesNotThrow(() -> verify(mockedRepository).transfer("Alice1-", "Bobby2#", new BigDecimal("10.00")));
        verifyNoMoreInteractions(mockedRepository);
    }

    @Test
    void persistenceFailureIsPropagatedWithoutSuccessLog() {
        AccountRepository mockedAccounts = mock(AccountRepository.class);
        TransactionRepository mockedRepository = mock(TransactionRepository.class);
        when(mockedAccounts.existsById("Alice1-")).thenReturn(true);
        doThrow(new IllegalStateException("Storage unavailable")).when(mockedRepository)
                .deposit("Alice1-", new BigDecimal("1.00"));
        Logger logger = (Logger) LoggerFactory.getLogger(TransactionServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThrows(IllegalStateException.class,
                    () -> new TransactionServiceImpl(mockedAccounts, mockedRepository).deposit("Alice1-", BigDecimal.ONE));
            assertTrue(appender.list.stream().noneMatch(e -> e.getFormattedMessage().contains("succeeded")));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void depositIncreasesBalanceAndRecordsTransaction() {
        transactions.deposit("Alice1-", new BigDecimal("12.34"));
        assertEquals(new BigDecimal("12.34"), accounts.getBalance("Alice1-"));
        Transaction recorded = transactions.getRecentTransactions("Alice1-", 1).getFirst();
        assertEquals(TransactionType.DEPOSIT, recorded.getType());
        assertEquals(new BigDecimal("12.34"), recorded.getAmount());
    }


    @Test
    void withdrawReducesBalanceAndRecordsTransaction() {
        transactions.deposit("Alice1-", new BigDecimal("20.00"));
        assertDoesNotThrow(() -> transactions.withdraw("Alice1-", 1234, new BigDecimal("5.00")));
        assertEquals(new BigDecimal("15.00"), accounts.getBalance("Alice1-"));
        Transaction recorded = transactions.getRecentTransactions("Alice1-", 1).getFirst();
        assertEquals(TransactionType.WITHDRAW, recorded.getType());
        assertEquals(new BigDecimal("5.00"), recorded.getAmount());
    }

    @Test
    void withdrawOfExactBalanceLeavesZero() {
        transactions.deposit("Alice1-", new BigDecimal("20.00"));
        assertDoesNotThrow(() -> transactions.withdraw("Alice1-", 1234, new BigDecimal("20.00")));
        assertEquals(new BigDecimal("0.00"), accounts.getBalance("Alice1-"));
    }

    @Test
    void withdrawRejectsInsufficientBalance() {
        transactions.deposit("Alice1-", BigDecimal.TEN);
        assertThrows(InsufficientBalanceException.class, () -> transactions.withdraw("Alice1-", 1234, new BigDecimal("10.01")));
        assertEquals(new BigDecimal("10.00"), accounts.getBalance("Alice1-"));
        assertTrue(transactions.getRecentTransactions("Alice1-", 10).stream()
                .noneMatch(t -> t.getType() == TransactionType.WITHDRAW));
    }

    @Test
    void withdrawRejectsMissingAccount() {
        assertThrows(AccountDoesNotExistException.class, () -> transactions.withdraw("Missing1-", 0, BigDecimal.ONE));
    }

    @Test
    void withdrawRejectsIncorrectPin() {
        transactions.deposit("Alice1-", BigDecimal.TEN);
        assertThrows(IncorrectPINException.class, () -> transactions.withdraw("Alice1-", 9999, BigDecimal.ONE));
        assertEquals(new BigDecimal("10.00"), accounts.getBalance("Alice1-"));
    }

    @Test
    void withdrawRejectsNullAndNonPositiveAmounts() {
        transactions.deposit("Alice1-", BigDecimal.TEN);
        for (BigDecimal amount : new BigDecimal[] {null, BigDecimal.ZERO, new BigDecimal("-1")}) {
            assertThrows(BankingException.class, () -> transactions.withdraw("Alice1-", 1234, amount));
        }
        assertEquals(new BigDecimal("10.00"), accounts.getBalance("Alice1-"));
    }

    @Test
    void transferRejectsBlankOrNullAccountIds() {
        transactions.deposit("Alice1-", BigDecimal.TEN);
        assertThrows(IllegalArgumentException.class, () -> transactions.transfer(null, "Bobby2#", BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> transactions.transfer("Alice1-", null, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> transactions.transfer(" ", "Bobby2#", BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> transactions.transfer("Alice1-", " ", BigDecimal.ONE));
    }

    @Test
    void transferRejectsInvalidAmounts() {
        transactions.deposit("Alice1-", BigDecimal.TEN);
        for (BigDecimal amount : new BigDecimal[] {null, BigDecimal.ZERO, new BigDecimal("-1"), new BigDecimal("1.001")}) {
            assertThrows(IllegalArgumentException.class, () -> transactions.transfer("Alice1-", "Bobby2#", amount));
        }
        assertEquals(new BigDecimal("10.00"), accounts.getBalance("Alice1-"));
        assertEquals(new BigDecimal("0.00"), accounts.getBalance("Bobby2#"));
    }

    @Test
    void getRecentTransactionsRejectsNonPositiveLimit() {
        transactions.deposit("Alice1-", BigDecimal.ONE);
        assertThrows(BankingException.class, () -> transactions.getRecentTransactions("Alice1-", 0));
        assertThrows(BankingException.class, () -> transactions.getRecentTransactions("Alice1-", -1));
    }

    @Test
    void getRecentTransactionsRejectsMissingAccount() {
        assertThrows(AccountDoesNotExistException.class, () -> transactions.getRecentTransactions("Missing1-", 10));
    }
}
