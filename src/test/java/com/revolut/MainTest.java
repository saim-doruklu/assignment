package com.revolut;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.revolut.model.Account;
import com.revolut.model.Transaction;
import com.revolut.model.TransactionStatus;
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
import java.util.*;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class MainTest {

    private static final String GET_ALL_ACCOUNTS_URL = "http://localhost:4567/account/all";
    private static final String GET_ACCOUNTS_URL = "http://localhost:4567/account/get";
    private static final String CREATE_ACCOUNTS_URL = "http://localhost:4567/account/create";
    private static final String CREATE_TRANSACTION_URL = "http://localhost:4567/transaction/new";
    private static final String GET_TRANSACTION_URL = "http://localhost:4567/transaction/get";

    private static final TypeReference<List<Account>> accountListType = new TypeReference<List<Account>>() {};
    private static final TypeReference<Map<String, TransactionStatus>> transactionStatusMapType = new TypeReference<Map<String, TransactionStatus>>() {};
    private static final TypeReference<Map<String,Account>> accountMapType = new TypeReference<Map<String,Account>>() {};
    private static final TypeReference<Transaction> transactionType = new TypeReference<Transaction>() {};
    private static final TypeReference<List<String>> stringListType = new TypeReference<List<String>>() {};
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeClass
    public static void init(){
        Main.main(null);
    }

    @Test
    public void testCreateNewAccount() throws IOException {

        List<Account> initialAccounts = getAllAccounts();
        int initialAccountsSize = initialAccounts.size();

        Object raw = readFile("create-accounts.json", true);
        int numAccountsToCreate = convert(raw, accountListType).size();

        createAccounts(raw);
        List<Account> createdAccounts = getAllAccounts();
        int createdAccountsSize = createdAccounts.size();

        Assert.assertEquals(initialAccountsSize+numAccountsToCreate, createdAccountsSize);
    }

    @Test
    public void testCreateNewTransaction() throws IOException, InterruptedException {
        Object fileJson = readFile("create-transactions.json",false);


        List<Map<String, Object>> initialAccounts = getAccountsFromJson((Map<String, Object>) fileJson);
        List<Account> accountsCreated = createAccounts(initialAccounts);
        List<String> accountNumbers = toAccountNumbers(accountsCreated);
        BigDecimal initialTotalBalance = accountsCreated.stream().map(Account::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Transaction> transactions = createTransactionsFromJson((Map<String, Object>) fileJson, accountsCreated);
        createTransactions(transactions);
        BigDecimal balanceSumInFile = allAccountsTotalBalance(transactions);

        Thread.sleep(2000);

        Map<String,Account> accounts = getAccounts(accountNumbers);
        BigDecimal balanceSumAfterTransactions = accounts.values().stream().map(Account::getBalance).reduce(BigDecimal.ZERO,BigDecimal::add);

        Assert.assertEquals(BigDecimal.ZERO,initialTotalBalance);
        Assert.assertEquals(balanceSumInFile,balanceSumAfterTransactions);
    }

    private List<String> toAccountNumbers(List<Account> accountsCreated) {
        return accountsCreated.stream().map(Account::getAccountNumber).collect(Collectors.toList());
    }

    @Test
    public void testRandomTransactions() throws IOException, InterruptedException {

        int numTransactions = 1000;
        int numAccounts = 100;
        long sleepTime = 5000;
        BigDecimal depositAmount = BigDecimal.valueOf(10000000);

        Random random = new Random();
        List<Account> accountsToCreate = new ArrayList<>();
        for (int i = 0; i < numAccounts; i++) {
            Account account = new Account();
            account.setEmail("email"+i+"@.com");
            account.setName("name "+i);
            accountsToCreate.add(account);
        }
        List<Account> accounts = createAccounts(accountsToCreate);
        List<String> accountNumbers = toAccountNumbers(accounts);
        List<Transaction> deposits = new ArrayList<>();
        for (int i = 0; i < numAccounts; i++) {
            Transaction deposit = new Transaction();
            deposit.setAmount(depositAmount);
            deposit.setSender(accounts.get(i).getAccountNumber());
            deposit.setTransactionType(TransactionType.DEPOSIT);
            deposits.add(deposit);
        }
        createTransactions(deposits);

        Thread.sleep(sleepTime);
        Map<String,Account> accountsAfterDeposit = getAccounts(accountNumbers);
        for (int i = 0; i < numAccounts; i++) {
            String accountNumber = accountNumbers.get(i);
            Account account = accountsAfterDeposit.get(accountNumber);
            System.out.printf("Account balance after deposit %s for account %s", account.getBalance(), account.getAccountNumber());
            Assert.assertEquals(depositAmount,account.getBalance());
        }

        List<Transaction> randomTransactions = new ArrayList<>();
        for (int i = 0; i < numTransactions; i++) {
            int randomTransactionType = random.nextInt(3);
            TransactionType type;
            String sender = accountNumbers.get(random.nextInt(numAccounts));
            String receiver =null;
            if(randomTransactionType == 0){
                type = TransactionType.DEPOSIT;
            }else if(randomTransactionType == 1){
                type = TransactionType.WITHDRAWAL;
            }else{
                type = TransactionType.TRANSFER;
                receiver = accountNumbers.get(random.nextInt(numAccounts));
                while(sender.equals(receiver)){
                    receiver = accountNumbers.get(random.nextInt(numAccounts));
                }
            }

            BigDecimal amount = BigDecimal.valueOf(random.nextInt(100)+1);
            Transaction transaction = new Transaction();
            transaction.setSender(sender);
            transaction.setReceiver(receiver);
            transaction.setTransactionType(type);
            transaction.setAmount(amount);
            randomTransactions.add(transaction);
        }

        for (int i = 0; i < numTransactions; i++) {
            Transaction transaction = randomTransactions.get(i);
            String sender = transaction.getSender();
            Account senderAccount = accountsAfterDeposit.get(sender);
            BigDecimal amount = transaction.getAmount();

            if(transaction.getTransactionType() == TransactionType.DEPOSIT){
                senderAccount.setBalance(senderAccount.getBalance().add(amount));
            } else if(transaction.getTransactionType() == TransactionType.WITHDRAWAL){
                senderAccount.setBalance(senderAccount.getBalance().subtract(amount));
            } else {
                String receiver = transaction.getReceiver();
                Account receiverAccount = accountsAfterDeposit.get(receiver);
                senderAccount.setBalance(senderAccount.getBalance().subtract(amount));
                receiverAccount.setBalance(receiverAccount.getBalance().add(amount));
            }
        }

        for (int i = 0; i < numTransactions; i++) {
            Transaction transaction = randomTransactions.get(i);
            createTransactions(Arrays.asList(transaction));
        }

        Thread.sleep(sleepTime);

        Map<String,Account> accountsAfterTransactions = getAccounts(accountNumbers);
        for(Map.Entry<String, Account> entry : accountsAfterTransactions.entrySet()){
            String accountNumber = entry.getKey();
            Account account = entry.getValue();
            Assert.assertEquals(accountsAfterDeposit.get(accountNumber).getBalance(),account.getBalance());
        }
    }

    @Test
    public void testTransactionStatuses() throws IOException, InterruptedException {
        Account account = new Account();
        account.setName("test");
        account.setEmail("test");
        List<Account> created = createAccounts(Arrays.asList(account));
        Account createdAccount = created.get(0);
        String accountNumber = createdAccount.getAccountNumber();

        Transaction withdrawal = new Transaction();
        withdrawal.setAmount(BigDecimal.valueOf(100000));
        withdrawal.setSender(accountNumber);
        withdrawal.setTransactionType(TransactionType.WITHDRAWAL);
        testTransactionStatus(withdrawal,BigDecimal.ZERO,TransactionStatus.REJECTED);

        Transaction deposit = new Transaction();
        deposit.setAmount(BigDecimal.TEN);
        deposit.setSender(accountNumber);
        deposit.setTransactionType(TransactionType.DEPOSIT);

        testTransactionStatus(deposit,BigDecimal.TEN,TransactionStatus.FINISHED);
    }

    private void testTransactionStatus(Transaction transaction, BigDecimal balance, TransactionStatus status) throws IOException, InterruptedException {
        List<String> transactions = createTransactions(Arrays.asList(transaction));

        Thread.sleep(50);

        Map<String, Account> accounts = getAccounts(Arrays.asList(transaction.getSender()));
        Map<String, TransactionStatus> transactionStatus = getTransactionStatus(Arrays.asList(transactions.get(0)));
        Assert.assertEquals(balance,accounts.get(transaction.getSender()).getBalance());
        Assert.assertEquals(status,transactionStatus.get(transactions.get(0)));
    }

    private Map<String, TransactionStatus> getTransactionStatus(List<String> transactions) throws IOException {
        return sendRequestAndGetResponse(GET_TRANSACTION_URL,transactions,this::createPost,transactionStatusMapType);
    }

    private Map<String, Account> getAccounts(List<String> accountNumbers) throws IOException {
        return sendRequestAndGetResponse(GET_ACCOUNTS_URL, accountNumbers, this::createPost, accountMapType);
    }

    private List<String> createTransactions(List<Transaction> transactions) throws IOException {
        return sendRequestAndGetResponse(CREATE_TRANSACTION_URL,transactions,this::createPost,stringListType);
    }

    private List<Account> getAllAccounts() throws IOException {
        return sendRequestAndGetResponse(GET_ALL_ACCOUNTS_URL, null, this::createGet, accountListType);
    }

    private List<Account> createAccounts(Object accountsToCreate) throws IOException {
        return sendRequestAndGetResponse(CREATE_ACCOUNTS_URL, accountsToCreate, this::createPost, accountListType);
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

    private List<Transaction> createTransactionsFromJson(Map<String, Object> json, List<Account> accounts) throws IOException {
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
        return transactions;
    }

    private List<Map<String, Object>> getAccountsFromJson(Map<String, Object> json) {
        return (List<Map<String, Object>>) (json.get("accounts"));
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