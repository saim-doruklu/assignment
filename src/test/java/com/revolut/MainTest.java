package com.revolut;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.revolut.dto.AccountDto;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class MainTest {

    private ObjectMapper objectMapper = new ObjectMapper();

    @BeforeClass
    public static void init(){
        Main.main(null);
    }

    @Test
    public void testCreateNewAccount() throws IOException {
        // Given
        HttpGet getAccounts = new HttpGet("http://localhost:4567/account/all");

        List<AccountDto> accountsToCreate = objectMapper.reader(new TypeReference<List<AccountDto>>() {
        }).readValue(Thread.currentThread().getContextClassLoader().getResourceAsStream("create-accounts.json"));
        StringEntity accountsEntity = new StringEntity(objectMapper.writeValueAsString(accountsToCreate));
        HttpPost createAccountsRequest = new HttpPost("http://localhost:4567/account/create");
        createAccountsRequest.setEntity(accountsEntity);
        // When
        HttpResponse created = HttpClientBuilder.create().build().execute(createAccountsRequest);
        // Then
        HttpResponse getAllAccount = HttpClientBuilder.create().build().execute(getAccounts);
        List<AccountDto> returnedAccounts = objectMapper.reader(new TypeReference<List<AccountDto>>() {
        }).readValue(getAllAccount.getEntity().getContent());
        Assert.assertEquals(accountsToCreate.size(),returnedAccounts.size());
    }

}