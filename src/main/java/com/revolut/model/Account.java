package com.revolut.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;

public class Account{

    @JsonIgnore
    private AtomicBoolean isBeingUpdated = new AtomicBoolean(false);

    private BigDecimal balance;
    private String accountNumber;
    private String name;
    private String email;

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public AtomicBoolean getIsBeingUpdated() {
        return isBeingUpdated;
    }

    public Account copy(){
        Account clone = new Account();
        clone.setBalance(this.balance);
        clone.setAccountNumber(this.accountNumber);
        clone.setName(this.name);
        clone.setEmail(this.email);
        return clone;
    }

    public void copyFrom(Account account) {
        this.balance = account.balance;
        this.name = account.name;
        this.email = account.email;
    }
}
