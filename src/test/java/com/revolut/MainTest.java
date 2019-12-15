package com.revolut;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.revolut.model.Account;
import com.revolut.model.Transaction;
import com.revolut.model.TransactionType;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;

public class MainTest {

    private static final String GET_ACCOUNTS_URL = "http://localhost:4567/account/all";
    private static final String CREATE_ACCOUNTS_URL = "http://localhost:4567/account/create";
    private static final String CREATE_TRANSACTION_URL = "http://localhost:4567/transaction/new";
    private static final TypeReference<List<Account>> accountListType = new TypeReference<List<Account>>() {};
    private static final TypeReference<Transaction> transactionType = new TypeReference<Transaction>() {};
    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeClass
    public static void init(){
        Main.main(null);
    }

    @Test
    public void testCreateNewAccount() throws IOException {

        List<Account> initialAccounts = sendRequestAndGetResponse(GET_ACCOUNTS_URL, null, this::createGet, accountListType);
        int initialAccountsSize = initialAccounts.size();

        Object raw = readFile("create-accounts.json", true);
        int numAccountsToCreate = convert(raw, accountListType).size();


        sendRequestAndGetResponse(CREATE_ACCOUNTS_URL,raw,this::createPost,null);

        List<Account> createdAccounts = sendRequestAndGetResponse(GET_ACCOUNTS_URL, null, this::createGet, accountListType);
        int createdAccountsSize = createdAccounts.size();
        Assert.assertEquals(0, initialAccountsSize);
        Assert.assertEquals(numAccountsToCreate, createdAccountsSize);
    }

    @Test
    public void testCreateNewTransaction() throws IOException, InterruptedException {
        Object fileJson = readFile("create-transactions.json",false);

        List<Account> initialAccounts = createAccounts((Map<String, Object>) fileJson);
        List<Transaction> transactions = createTransactions((Map<String, Object>) fileJson, initialAccounts);
        BigDecimal balanceSumInFile = allAccountsTotalBalance(transactions);

        Thread.sleep(2000);

        List<Account> accounts = sendRequestAndGetResponse(GET_ACCOUNTS_URL, null, this::createGet, accountListType);
        BigDecimal initialTotalBalance = totalBalance(initialAccounts);
        BigDecimal balanceSumAfterTransactions = totalBalance(accounts);

        Assert.assertEquals(BigDecimal.ZERO,initialTotalBalance);
        Assert.assertEquals(balanceSumInFile,balanceSumAfterTransactions);
    }

    @Test
    public void testRandomTransactions() throws IOException {
        Random random = new Random();
        List<Account> accountsToCreate = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Account account = new Account();
            account.setEmail("email"+i+"@.com");
            account.setName("name "+i);
            accountsToCreate.add(account);
        }
        List<Account> accounts = sendRequestAndGetResponse(CREATE_ACCOUNTS_URL, accountsToCreate, this::createPost, accountListType);

        for (int i = 0; i < 1000; i++) {
            int randomTransactionType = random.nextInt(3);
            TransactionType type;
            if(randomTransactionType == 0){
                type = TransactionType.DEPOSIT;
            }else if(randomTransactionType == 1){
                type = TransactionType.WITHDRAWAL;
            }else{
                type = TransactionType.TRANSFER;
            }

        }
    }

    private BigDecimal totalBalance(List<Account> initialAccounts) {
        return initialAccounts.stream().map(Account::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal allAccountsTotalBalance(List<Transaction> transactions) {
        BigDecimal balanceSumInFile = BigDecimal.ZERO;
        for(Transaction transaction : transactions){
            if(transaction.getTransactionType() == TransactionType.DEPOSIT){
                balanceSumInFile = balanceSumInFile.add(transaction.getAmount());
            }else if(transaction.getTransactionType() == TransactionType.WITHDRAWAL){
                balanceSumInFile = balanceSumInFile.subtract(transaction.getAmount());
            }
        }
        return balanceSumInFile;
    }

    private List<Transaction> createTransactions(Map<String, Object> json, List<Account> accounts) throws IOException {
        List<Map<String, Object>> transactionsInfoList = (List<Map<String, Object>>) (json.get("transactions"));
        List<Transaction> transactions = new ArrayList<>();
        for(Map<String, Object> transactionInfo : transactionsInfoList){
            String sender = (String)transactionInfo.get("senderMailAddress");
            String receiver = (String)transactionInfo.get("receiverMailAddress");
            String senderAccountNo = accounts.stream().filter(acc -> acc.getEmail().equals(sender)).map(Account::getAccountNumber).findFirst().orElse(null);
            String receiverAccountNo = accounts.stream().filter(acc -> acc.getEmail().equals(receiver)).map(Account::getAccountNumber).findFirst().orElse(null);
            Transaction transaction = convert(transactionInfo.get("transaction"),transactionType);
            transaction.setSender(senderAccountNo);
            transaction.setReceiver(receiverAccountNo);
            transactions.add(transaction);
        }
        sendRequestAndGetResponse(CREATE_TRANSACTION_URL,transactions,this::createPost,null);
        return transactions;
    }

    private List<Account> createAccounts(Map<String, Object> json) throws IOException {
        List<Map<String, Object>> accountsList = (List<Map<String, Object>>) (json.get("accounts"));
        return sendRequestAndGetResponse(CREATE_ACCOUNTS_URL, accountsList, this::createPost, accountListType);
    }

    private <T> T sendRequestAndGetResponse(String url, Object body, BiFunction<String,Object,HttpUriRequest> requestCreator, TypeReference<T> returnType) throws IOException {
        HttpResponse response = sendRequest(requestCreator.apply(url,body));
        return getHttpResponseAs(response, returnType);
    }

    private <T> T getHttpResponseAs(HttpResponse response, TypeReference<T> type) throws IOException {
        if(response.getEntity() != null && type != null) {
            return objectMapper.reader(type).readValue(response.getEntity().getContent());
        }else{
            return null;
        }
    }

    private CloseableHttpResponse sendRequest(HttpUriRequest request) throws IOException {
        return HttpClientBuilder.create().build().execute(request);
    }

    private HttpPost createPost(String url, Object body) {
        try {
            return (HttpPost) createRequest(url, body, HttpPost.METHOD_NAME);
        } catch (Exception exception) {
            System.out.printf("Request unsuccessful. Error is %s", exception.getMessage());
            exception.printStackTrace();
            return null;
        }
    }

    private HttpGet createGet(String url, Object body) {
        try {
            return (HttpGet) createRequest(url, null, HttpGet.METHOD_NAME);
        }catch (Exception exception){
            System.out.printf("Request unsuccessful. Error is %s", exception.getMessage());
            exception.printStackTrace();
            return null;
        }
    }

    private HttpRequest createRequest(String url, Object body, String methodName) throws UnsupportedEncodingException, JsonProcessingException {
        switch (methodName){
            case HttpPost.METHOD_NAME:{
                HttpPost request = new HttpPost(url);
                request.setEntity(getStringEntity(body));
                return request;
            }
            case HttpGet.METHOD_NAME:{
                return new HttpGet(url);
            }
            default:{
                return null;
            }
        }
    }

    private StringEntity getStringEntity(Object object) throws UnsupportedEncodingException, JsonProcessingException {
        return new StringEntity(objectMapper.writeValueAsString(object));
    }

    private <T> T convert(Object object,TypeReference<T> reference) {
        return objectMapper.convertValue(object,reference);
    }

    private Object readFile(String fileName, boolean isList) throws IOException {
        InputStream stream = getResourceFile(fileName);
        return readFromStream(isList, stream);
    }

    private Object readFromStream(boolean isList, InputStream stream) throws IOException {
        if(isList) {
            return objectMapper.reader(new TypeReference<List<Map<String,Object>>>() {
            }).readValue(stream);
        }else{
            return objectMapper.reader(new TypeReference<Map<String,Object>>() {
            }).readValue(stream);
        }
    }

    private InputStream getResourceFile(String fileName) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName);
    }

}