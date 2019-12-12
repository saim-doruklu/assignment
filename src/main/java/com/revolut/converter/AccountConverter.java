package com.revolut.converter;

import com.revolut.dto.AccountDto;
import com.revolut.model.Account;

import java.util.List;
import java.util.stream.Collectors;

public class AccountConverter {

    public List<Account> toEntity(List<AccountDto> accountDtos){
        return accountDtos.stream().map(dto -> {
            Account account = new Account();
            account.setEmail(dto.getEmail());
            account.setName(dto.getName());
            account.setAccountNumber(dto.getAccountNumber());
            return account;
        }).collect(Collectors.toList());
    }

    public List<AccountDto> toDto(List<Account> accounts) {
        return accounts.stream().map(entity -> {
            AccountDto accountDto = new AccountDto();
            accountDto.setAccountNumber(entity.getAccountNumber());
            accountDto.setBalance(entity.getBalance());
            accountDto.setEmail(entity.getEmail());
            accountDto.setName(entity.getName());
            return accountDto;
        }).collect(Collectors.toList());
    }
}
