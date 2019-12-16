package com.revolut;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.revolut.model.Account;
import com.revolut.model.Transaction;
import com.revolut.repository.AccountRepository;
import com.revolut.repository.TransactionRepository;
import spark.Request;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;

import static spark.Spark.get;
import static spark.Spark.post;

public class Main {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final AccountRepository accountRepository = new AccountRepository();
    private static final TransactionRepository transactionRepository = new TransactionRepository();
    private static final TransactionProcessor processor = new TransactionProcessor(accountRepository,transactionRepository);

    public static void main(String[] args) {
        TypeReference<List<Account>> accountListType = new TypeReference<List<Account>>(){};
        TypeReference<List<String>> stringListType = new TypeReference<List<String>>(){};
        TypeReference<List<Transaction>> transactionListType = new TypeReference<List<Transaction>>(){};

        post("/account/create", (req, res) -> {
            List<Account> accounts = convertPayload(req,accountListType);
            List<Account> created = accountRepository.create(accounts);
            return objectMapper.writeValueAsString(created);
        });
        get("/account/all",(req,res) -> {
           List<Account> allAccounts = accountRepository.all();
           return objectMapper.writeValueAsString(allAccounts);
        });
        post("/account/get",(req,res) -> {
            List<String> accountsNumbers = convertPayload(req,stringListType);
            Map<String,Account> accounts = accountRepository.getAccounts(accountsNumbers,false);
            return objectMapper.writeValueAsString(accounts);
        });
        post("/transaction/new", (req,res) -> {
            List<Transaction> transactions = convertPayload(req,transactionListType);
            List<String> transactionNumbers = transactionRepository.addTransactions(transactions);
            return objectMapper.writeValueAsString(transactionNumbers);
        });
        processor.start();
    }

    private static <T> T convertPayload(Request req, TypeReference<T> type) throws java.io.IOException {
        return objectMapper.readValue(new ByteArrayInputStream(req.bodyAsBytes()),type);
    }
}