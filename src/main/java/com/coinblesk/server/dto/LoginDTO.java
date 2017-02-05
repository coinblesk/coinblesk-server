package com.coinblesk.server.dto;

import com.coinblesk.server.service.UserAccountService;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

public class LoginDTO {

    @Pattern(regexp = UserAccountService.EMAIL_PATTERN)
    @NotNull
    private String username;

    @NotNull
    private String password;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "LoginDTO{" +
                "password='*****'" +
                ", username='" + username + "'}";
    }
}
