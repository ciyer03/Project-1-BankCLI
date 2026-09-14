package org.bankofcli.service;

import org.bankofcli.model.Account;

public interface AuthService {
    Account register(int PIN);
    
    Account login(String accountId, int PIN);
}
