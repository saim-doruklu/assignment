package com.revolut;

import com.revolut.model.Account;
import com.revolut.model.Transaction;
import com.revolut.model.TransactionType;
import com.revolut.repository.AccountRepository;
import com.revolut.repository.TransactionRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TransactionProcessor {

    private final int coreSize = 8;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private ScheduledExecutorService processor = Executors.newScheduledThreadPool(coreSize);

    private enum TransactionStatus{
        FINISHED,REJECTED,POSTPONED;
    }

    public TransactionProcessor(AccountRepository accountRepository, TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    public void start(){
        processor.scheduleAtFixedRate(processTransaction(),0,10, TimeUnit.MILLISECONDS);
    }

    private Runnable processTransaction() {
        return () -> {
            String threadName = Thread.currentThread().getName();
            int numberOfItemsToProcess = transactionRepository.getWaitingTransactionsSize();
            int itemsProcessed = 0;
            if(numberOfItemsToProcess > 0) {
                System.out.printf("Total items in queue = %d when thread %s is beginning\n", numberOfItemsToProcess, threadName);
            }
            while(transactionRepository.getWaitingTransactionsSize() > 0){
                Transaction nextTransaction = transactionRepository.getNextTransaction();
                if(nextTransaction != null){
                    System.out.printf("Processing transaction by id %s by thread %s\n",nextTransaction.getId(),threadName);

                    List<String> accountsForTransaction = accountsForTransaction(nextTransaction);
                    TransactionStatus status = processTransaction(nextTransaction, accountsForTransaction);
                    if(status == TransactionStatus.REJECTED){
                        transactionRepository.finishTransaction(nextTransaction,false, true);
                        itemsProcessed++;
                    } else if(status == TransactionStatus.POSTPONED){
                        transactionRepository.finishTransaction(nextTransaction,false, false);
                    } else{
                        transactionRepository.finishTransaction(nextTransaction,true, false);
                        itemsProcessed++;
                    }
                }
            }
            System.out.printf("Total items processed = %d by thread %s\n",itemsProcessed, threadName);
        };
    }

    private List<String> accountsForTransaction(Transaction transaction) {
        TransactionType transactionType = transaction.getTransactionType();
        List<String> accountsToGet = new ArrayList<>();
        accountsToGet.add(transaction.getSender());
        if(transactionType == TransactionType.TRANSFER) {
            accountsToGet.add(transaction.getReceiver());
        }
        return accountsToGet;
    }

    private TransactionStatus processTransaction(Transaction nextTransaction,List<String> accountsToGet) {
        if (!accountsExist(accountsToGet)){
            System.out.printf("Some accounts in list %s  don't exist, rejecting transaction %s\n",accountsToGet,nextTransaction.getId());
            return TransactionStatus.REJECTED;
        }

        return processWithExistingAccounts(nextTransaction, accountsToGet);
    }

    private TransactionStatus processWithExistingAccounts(Transaction nextTransaction, List<String> accountsToGet) {
        Map<String, Account> accounts = accountRepository.getAccounts(accountsToGet, true);
        if(accounts.isEmpty()){
            System.out.printf("Some accounts could not be locked from %s, postponing transaction %s\n",accountsToGet,nextTransaction.getId());
            return TransactionStatus.POSTPONED;
        }

        TransactionStatus status = processWithLockedAccounts(nextTransaction, accounts);
        accountRepository.unlockAccounts(new ArrayList<>(accounts.values()));
        return status;
    }

    private TransactionStatus processWithLockedAccounts(Transaction nextTransaction, Map<String, Account> accounts) {
        boolean isSuccess = tryTransaction(nextTransaction,accounts);
        if(!isSuccess){
            System.out.printf("Some constraints are not met, rejecting transaction %s\n",nextTransaction.getId());
            return TransactionStatus.REJECTED;
        }

        boolean updateSuccess = accountRepository.updateAccounts(new ArrayList<>(accounts.values()));
        return updateSuccess ? TransactionStatus.FINISHED : TransactionStatus.POSTPONED;
    }

    private boolean tryTransaction(Transaction nextTransaction, Map<String, Account> accounts) {
        String sender = nextTransaction.getSender();
        String receiver = nextTransaction.getReceiver();
        BigDecimal amount = nextTransaction.getAmount();
        TransactionType transactionType = nextTransaction.getTransactionType();
        boolean isSuccess = true;
        if (transactionType == TransactionType.TRANSFER) {
            isSuccess = handleTransfer(accounts, sender, receiver, amount);
        } else if (transactionType == TransactionType.DEPOSIT) {
            handleDeposit(accounts.get(sender), amount);
        } else {
            isSuccess = handleWithdrawal(accounts.get(sender), amount);
        }
        return isSuccess;
    }

    private boolean accountsExist(List<String> accountsToGet) {
        Map<String, Account> existingAccounts = accountRepository.getAccounts(accountsToGet,false);
        return existingAccounts.size() == accountsToGet.size();
    }

    private void handleDeposit(Account account, BigDecimal amount) {
        account.setBalance(account.getBalance().add(amount));
    }

    private boolean handleWithdrawal(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            return false;
        }

        account.setBalance(account.getBalance().subtract(amount));
        return true;
    }

    private boolean handleTransfer(Map<String, Account> accounts, String sender, String receiver, BigDecimal amount) {
        Account senderAccount = accounts.get(sender);
        Account receiverAccount = accounts.get(receiver);
        if (senderAccount.getBalance().compareTo(amount) < 0) {
            return false;
        }

        senderAccount.setBalance(senderAccount.getBalance().subtract(amount));
        receiverAccount.setBalance(receiverAccount.getBalance().add(amount));
        return true;
    }
}
