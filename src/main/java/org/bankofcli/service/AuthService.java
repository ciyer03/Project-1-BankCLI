package org.bankofcli.service;

import org.bankofcli.model.Account;

public interface AuthService {
    Account register(String firstName, String lastName, int PIN);
    
    Account login(String accountId, int PIN);
}
