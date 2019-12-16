package com.revolut.repository;

import com.revolut.model.Transaction;
import com.revolut.model.TransactionStatus;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TransactionRepository {
    private AtomicInteger waitingTransactionsSize = new AtomicInteger(0);
    private LinkedTransferQueue<LockedTransaction> waitingTransactions = new LinkedTransferQueue<>();
    private Map<String,LockedTransaction> waitingTransactionsById = new ConcurrentHashMap<>();
    private Map<String,LockedTransaction> finishedTransactionsById = new ConcurrentHashMap<>();
    private Map<String,LockedTransaction> rejectedTransactions = new ConcurrentHashMap<>();

    public List<String> addTransactions(List<Transaction> transactions) {
        List<String> transactionIds = new ArrayList<>();
        for(Transaction transaction : transactions){
            LockedTransaction lockedTransaction = new LockedTransaction();
            lockedTransaction.copyFrom(transaction);
            transactionIds.add(lockedTransaction.getId());
            addWaitingTransaction(lockedTransaction);
        }
        return transactionIds;
    }

    public Transaction getNextTransaction(){
        LockedTransaction transaction = getWaitingTransaction();
        if(transaction != null && lockTransaction(transaction)) {
            return transaction.copy();
        }else{
            return null;
        }
    }

    public boolean finishTransaction(Transaction transaction, boolean isFinished, boolean isRejected){
        String threadName = Thread.currentThread().getName();
        LockedTransaction actualTransaction = waitingTransactionsById.get(transaction.getId());
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

    private void updateMapsForFinishedTransaction(LockedTransaction transaction,boolean isRejected) {
        waitingTransactionsById.remove(transaction.getId());
        if(isRejected) {
            rejectedTransactions.put(transaction.getId(),transaction);
        }else{
            finishedTransactionsById.put(transaction.getId(), transaction);
        }
    }

    private void addWaitingTransaction(LockedTransaction transaction){
        waitingTransactionsById.put(transaction.getId(),transaction);
        waitingTransactions.add(transaction);
        waitingTransactionsSize.incrementAndGet();
    }


    private LockedTransaction getWaitingTransaction(){
        LockedTransaction transaction = waitingTransactions.poll();
        if(transaction != null) {
            waitingTransactionsSize.decrementAndGet();
        }
        return transaction;
    }

    public int getWaitingTransactionsSize(){
        return waitingTransactionsSize.get();
    }

    private boolean lockTransaction(LockedTransaction transaction){
        return transaction.getIsBeingUpdated().compareAndSet(false,true);
    }

    private boolean unlockTransaction(LockedTransaction transaction){
        return transaction.getIsBeingUpdated().compareAndSet(true,false);
    }

    private boolean isLocked(LockedTransaction transaction){
        return transaction.getIsBeingUpdated().get();
    }

    public Map<String, TransactionStatus> getTransactionStatuses(List<String> transactionNumbers) {
        Map<String,TransactionStatus> statusMap = new HashMap<>();
        for(String transactionNumber : transactionNumbers){
            if(this.waitingTransactionsById.containsKey(transactionNumber)){
                statusMap.put(transactionNumber, TransactionStatus.POSTPONED);
            } else if(this.finishedTransactionsById.containsKey(transactionNumber)){
                statusMap.put(transactionNumber,TransactionStatus.FINISHED);
            } else if(this.rejectedTransactions.containsKey(transactionNumber)){
                statusMap.put(transactionNumber,TransactionStatus.REJECTED);
            }
        }
        return statusMap;
    }

    private class LockedTransaction extends Transaction{

        private AtomicBoolean isBeingUpdated = new AtomicBoolean(false);

        private LockedTransaction(){
            setId(UUID.randomUUID().toString());
        }

        private AtomicBoolean getIsBeingUpdated() {
            return isBeingUpdated;
        }

        private Transaction copy(){
            Transaction copy = new Transaction();
            copy.setId(getId());
            copy.setReceiver(getReceiver());
            copy.setSender(getSender());
            copy.setAmount(getAmount());
            copy.setTransactionType(getTransactionType());
            return copy;
        }

        private void copyFrom(Transaction transaction){
            this.setTransactionType(transaction.getTransactionType());
            this.setAmount(transaction.getAmount());
            this.setSender(transaction.getSender());
            this.setReceiver(transaction.getReceiver());
        }
    }
}
