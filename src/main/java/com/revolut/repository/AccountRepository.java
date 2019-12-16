package com.revolut.repository;

import com.revolut.model.Account;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class AccountRepository {

    private class LockedAccount extends Account{

        private AtomicBoolean isBeingUpdated = new AtomicBoolean(false);

        private Account copy(){
            Account clone = new Account();
            clone.setBalance(getBalance());
            clone.setAccountNumber(getAccountNumber());
            clone.setName(getName());
            clone.setEmail(getEmail());
            return clone;
        }

        private void copyFrom(Account account) {
            this.setBalance(account.getBalance());
            this.setName(account.getName());
            this.setEmail(account.getEmail());
        }

        private AtomicBoolean getIsBeingUpdated() {
            return isBeingUpdated;
        }
    }

    Map<String,LockedAccount> accountNumberAccount = new ConcurrentHashMap<>();

    public List<Account> create(List<Account> accounts) {
        List<Account> created = new ArrayList<>();
        for(Account original : accounts){
            LockedAccount lockedAccount = new LockedAccount();
            lockedAccount.copyFrom(original);
            lockedAccount.setAccountNumber(generateId());
            lockedAccount.setBalance(BigDecimal.ZERO);
            accountNumberAccount.put(lockedAccount.getAccountNumber(),lockedAccount);
            created.add(lockedAccount.copy());
        }
        return created;
    }

    private String generateId(){
        return UUID.randomUUID().toString();
    }

    public List<Account> all() {
        return accountNumberAccount.values().stream().map(LockedAccount::copy).collect(Collectors.toList());
    }

    public Map<String,Account> getAccounts(List<String> accounts, boolean lock){
        String threadName = Thread.currentThread().getName();
        System.out.printf("Getting accounts %s lock = %s by thread %s\n", accounts, lock,threadName);

        if(!lock){
            return getAccountsWithoutLock(accounts);
        }
        return getAccountsWithLock(accounts);
    }

    private Map<String, Account> getAccountsWithLock(List<String> accounts) {
        String threadName = Thread.currentThread().getName();

        Map<String,Account> foundAccounts = new HashMap<>();
        List<LockedAccount> lockedAccounts = new ArrayList<>();

        Collections.sort(accounts);

        boolean allSuccessful = true;
        for(String accountNumber : accounts){
            LockedAccount actualAccount = accountNumberAccount.get(accountNumber);
            if(actualAccount != null && lockAccount(actualAccount)){
                System.out.printf("Getting account %s with lock by thread %s\n", accountNumber, threadName);
                foundAccounts.put(accountNumber,actualAccount.copy());
                lockedAccounts.add(actualAccount);
            }else{
                System.out.printf("Couldn't get account %s with lock by thread %s\n", accountNumber, threadName);
                allSuccessful = false;
                break;
            }
        }

        if (!allSuccessful) {
            for (LockedAccount lockedAccount : lockedAccounts) {
                unlockAccount(lockedAccount);
            }
            return new HashMap<>();
        }
        return foundAccounts;
    }

    private Map<String, Account> getAccountsWithoutLock(List<String> accounts) {
        String threadName = Thread.currentThread().getName();
        Map<String,Account> foundAccounts = new HashMap<>();
        for(String accountNumber : accounts) {
            LockedAccount actualAccount = accountNumberAccount.get(accountNumber);
            if(actualAccount != null) {
                System.out.printf("Getting account %s without lock by thread %s\n", accountNumber, threadName);
                foundAccounts.put(accountNumber, actualAccount.copy());
            }
        }
        return foundAccounts;
    }

    public boolean updateAccounts(List<Account> accounts){
        String threadName = Thread.currentThread().getName();

        Map<Account,LockedAccount> foundAccounts = new HashMap<>();
        for(Account account : accounts){
            LockedAccount actualAccount = accountNumberAccount.get(account.getAccountNumber());
            if(actualAccount != null && isLocked(actualAccount)){
                foundAccounts.put(account,actualAccount);
            } else{
                System.out.printf("Unable to update account %s by thread %s\n", account.getAccountNumber(), threadName);
                return false;
            }
        }
        for(Map.Entry<Account, LockedAccount> accountPair : foundAccounts.entrySet()){
            System.out.printf("Updating account %s by thread %s\n", accountPair.getKey().getAccountNumber(), threadName);
            Account account = accountPair.getKey();
            LockedAccount actualAccount = accountPair.getValue();
            actualAccount.copyFrom(account);
        }
        return true;
    }

    public void unlockAccounts(List<Account> accounts){
        for(Account account : accounts){
            LockedAccount actualAccount = accountNumberAccount.get(account.getAccountNumber());
            unlockAccount(actualAccount);
        }
    }

    private boolean lockAccount(LockedAccount account){
        return account.getIsBeingUpdated().compareAndSet(false,true);
    }

    private boolean unlockAccount(LockedAccount account){
        return account.getIsBeingUpdated().compareAndSet(true,false);
    }

    private boolean isLocked(LockedAccount account){
        return account.getIsBeingUpdated().get();
    }
}
