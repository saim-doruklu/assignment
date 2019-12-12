package com.revolut.repository;

import com.revolut.model.Account;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class AccountRepository {
    Map<String,Account> accountNumberAccount = new ConcurrentHashMap<>();

    public List<Account> create(List<Account> accounts) {
        List<Account> created = new ArrayList<>();
        for(Account original : accounts){
            original.setAccountNumber(generateId());
            original.setBalance(BigDecimal.ZERO);
            accountNumberAccount.put(original.getAccountNumber(),original);
            created.add(original.copy());
        }
        return created;
    }

    private String generateId(){
        return UUID.randomUUID().toString();
    }

    public List<Account> all() {
        return accountNumberAccount.values().stream().map(Account::copy).collect(Collectors.toList());
    }
}
