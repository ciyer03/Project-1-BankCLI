package org.bankofcli;

import java.math.BigDecimal;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.bankofcli.exceptions.BankingException;
import org.bankofcli.repository.*;
import org.bankofcli.repository.memory.InMemoryBankRepository;
import org.bankofcli.service.impl.*;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ServiceBoundaryTest {
    @Test
    void validationDoesNotCallTransactionRepository() {
        var accounts = mock(AccountRepository.class);
        var repository = mock(TransactionRepository.class);
        when(accounts.existsById("Alice1-")).thenReturn(true);
        when(accounts.existsById("Bobby2#")).thenReturn(true);
        when(accounts.getBalance("Alice1-")).thenReturn(BigDecimal.TEN);
        var service = new TransactionServiceImpl(accounts, repository);
        assertThrows(BankingException.class, () -> service.deposit("Alice1-", BigDecimal.ZERO));
        // Withdraw is not yet implemented at the service layer, so it always throws
        // UnsupportedOperationException without reaching the repository.
        assertThrows(UnsupportedOperationException.class, () -> service.withdraw("Alice1-", new BigDecimal("11")));
        assertThrows(BankingException.class, () -> service.transfer("Alice1-", "Bobby2#", new BigDecimal("11")));
        assertThrows(BankingException.class, () -> service.transfer("Alice1-", "Alice1-", BigDecimal.ONE));
        verifyNoInteractions(repository);
    }

    @Test
    void validTransferDelegatesOneAtomicOperation() {
        var accounts = mock(AccountRepository.class);
        var repository = mock(TransactionRepository.class);
        when(accounts.existsById("Alice1-")).thenReturn(true);
        when(accounts.existsById("Bobby2#")).thenReturn(true);
        when(accounts.getBalance("Alice1-")).thenReturn(BigDecimal.TEN);
        var service = new TransactionServiceImpl(accounts, repository);
        assertDoesNotThrow(() -> service.transfer("Alice1-", "Bobby2#", BigDecimal.TEN));
        assertDoesNotThrow(() -> verify(repository).transfer("Alice1-", "Bobby2#", new BigDecimal("10.00")));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void persistenceFailureIsPropagatedWithoutSuccessLog() {
        var accounts = mock(AccountRepository.class);
        var repository = mock(TransactionRepository.class);
        when(accounts.existsById("Alice1-")).thenReturn(true);
        doThrow(new IllegalStateException("Storage unavailable")).when(repository)
                .deposit("Alice1-", new BigDecimal("1.00"));
        Logger logger = (Logger) LoggerFactory.getLogger(TransactionServiceImpl.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertThrows(IllegalStateException.class,
                    () -> new TransactionServiceImpl(accounts, repository).deposit("Alice1-", BigDecimal.ONE));
            assertTrue(appender.list.stream().noneMatch(e -> e.getFormattedMessage().contains("succeeded")));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void authenticationLogsOutcomesWithoutCredentials() {
        Logger logger = (Logger) LoggerFactory.getLogger(AuthServiceImpl.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            var auth = new AuthServiceImpl(new InMemoryBankRepository());
            auth.register("Private_user1", 6789);
            auth.login("Private_user1", 6789);
            assertThrows(BankingException.class, () -> auth.login("Private_user1", 9876));
            var messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
            assertTrue(messages.contains("Registration succeeded!"));
            assertTrue(messages.contains("Login succeeded!"));
            assertTrue(messages.contains("Login failed"));
            assertTrue(messages.stream().noneMatch(m ->
                    m.contains("6789") || m.contains("9876") || m.contains("Private_user1")));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void simultaneousWithdrawalsCannotOverdraw() throws Exception {
        var repository = new InMemoryBankRepository();
        new AuthServiceImpl(repository).register("Alice1-", 1234);
        repository.deposit("Alice1-", BigDecimal.TEN);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Boolean> withdraw = () -> {
                start.await();
                try {
                    repository.withdraw("Alice1-", BigDecimal.TEN);
                    return true;
                } catch (BankingException e) {
                    return false;
                }
            };
            var first = executor.submit(withdraw);
            var second = executor.submit(withdraw);
            start.countDown();
            assertNotEquals(first.get(5, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(5, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(new BigDecimal("0.00"), repository.getBalance("Alice1-"));
        assertEquals(2, repository.getRecentTransactions("Alice1-", 10).size());
    }
}
