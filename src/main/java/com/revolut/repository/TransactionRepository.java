package com.revolut.repository;

import com.revolut.model.Transaction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class TransactionRepository {
    private AtomicInteger waitingTransactionsSize = new AtomicInteger(0);
    private LinkedTransferQueue<Transaction> waitingTransactions = new LinkedTransferQueue<>();
    private Map<String,Transaction> waitingTransactionsById = new ConcurrentHashMap<>();
    private Map<String,Transaction> finishedTransactionsById = new ConcurrentHashMap<>();
    private Map<String,Transaction> rejectedTransactions = new ConcurrentHashMap<>();

    public List<String> addTransactions(List<Transaction> transactions) {
        List<String> transactionIds = new ArrayList<>();
        for(Transaction transaction : transactions){
            String transactionId = generateTransactionId();
            transaction.setId(transactionId);
            transactionIds.add(transactionId);
            addWaitingTransaction(transaction);
        }
        return transactionIds;
    }


    public Transaction getNextTransaction(){
        Transaction transaction = getWaitingTransaction();
        if(transaction != null && lockTransaction(transaction)) {
            return transaction.copy();
        }else{
            return null;
        }
    }

    public Transaction getWaitingTransaction(String transactionId){
        return waitingTransactionsById.get(transactionId).copy();
    }

    public Transaction getFinishedTransaction(String transactionId){
        return finishedTransactionsById.get(transactionId).copy();
    }

    public boolean finishTransaction(Transaction transaction, boolean isFinished, boolean isRejected){
        String threadName = Thread.currentThread().getName();
        Transaction actualTransaction = waitingTransactionsById.get(transaction.getId());
        if(isLocked(actualTransaction)){
            if(isFinished || isRejected) {
                System.out.printf("Finishing transaction %s rejection status = %s by thread %s\n", transaction.getId(), isRejected,threadName);
                actualTransaction.copyFrom(transaction);
                updateMapsForFinishedTransaction(actualTransaction, isRejected);
            }else{
                System.out.printf("Queueing back transaction to process later %s by thread %s\n", transaction.getId(), threadName);
                addWaitingTransaction(actualTransaction);
            }
            unlockTransaction(actualTransaction);
            return true;
        }else{
            return false;
        }
    }

    private void updateMapsForFinishedTransaction(Transaction transaction,boolean isRejected) {
        waitingTransactionsById.remove(transaction.getId());
        if(isRejected) {
            rejectedTransactions.put(transaction.getId(),transaction);
        }else{
            finishedTransactionsById.put(transaction.getId(), transaction);
        }
    }

    private void addWaitingTransaction(Transaction transaction){
        waitingTransactionsById.put(transaction.getId(),transaction);
        waitingTransactions.add(transaction);
        waitingTransactionsSize.incrementAndGet();
    }


    private Transaction getWaitingTransaction(){
        Transaction transaction = waitingTransactions.poll();
        if(transaction != null) {
            waitingTransactionsSize.decrementAndGet();
        }
        return transaction;
    }

    public int getWaitingTransactionsSize(){
        return waitingTransactionsSize.get();
    }

    private boolean lockTransaction(Transaction transaction){
        return transaction.getIsBeingUpdated().compareAndSet(false,true);
    }

    private boolean unlockTransaction(Transaction transaction){
        return transaction.getIsBeingUpdated().compareAndSet(true,false);
    }

    private boolean isLocked(Transaction transaction){
        return transaction.getIsBeingUpdated().get();
    }

    private String generateTransactionId(){
        return UUID.randomUUID().toString();
    }
}
