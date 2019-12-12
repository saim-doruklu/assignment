package com.revolut;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.revolut.converter.AccountConverter;
import com.revolut.dto.AccountDto;
import com.revolut.model.Account;
import com.revolut.repository.AccountRepository;

import java.io.ByteArrayInputStream;
import java.util.List;

import static spark.Spark.*;

public class Main {
    public static void main(String[] args) {
        AccountConverter accountConverter = new AccountConverter();
        AccountRepository accountRepository = new AccountRepository();
        ObjectMapper objectMapper = new ObjectMapper();
        post("/account/create", (req, res) -> {
            List<AccountDto> accountDtos = objectMapper.readValue(new ByteArrayInputStream(req.bodyAsBytes()),new TypeReference<List<AccountDto>>(){});
            List<Account> accounts = accountConverter.toEntity(accountDtos);
            List<Account> created = accountRepository.create(accounts);
            return objectMapper.writeValueAsString(accountConverter.toDto(created));
        });
        get("/account/all",(req,res) -> {
           List<Account> allAccounts = accountRepository.all();
           return objectMapper.writeValueAsString(accountConverter.toDto(allAccounts));
        });
    }
}