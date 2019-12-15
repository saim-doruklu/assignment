package com.revolut.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicBoolean;

public class Transaction {

    @JsonIgnore
    private AtomicBoolean isBeingUpdated = new AtomicBoolean(false);

    private TransactionType transactionType;
    private String sender;
    private String receiver;
    private String id;
    private BigDecimal amount;

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public AtomicBoolean getIsBeingUpdated() {
        return isBeingUpdated;
    }

    public Transaction copy(){
        Transaction copy = new Transaction();
        copy.setId(id);
        copy.setReceiver(receiver);
        copy.setSender(sender);
        copy.setAmount(amount);
        copy.setTransactionType(transactionType);
        return copy;
    }

    public void copyFrom(Transaction transaction){
        this.transactionType = transaction.getTransactionType();
        this.amount = transaction.getAmount();
        this.sender = transaction.getSender();
        this.receiver = transaction.getReceiver();
    }
}
