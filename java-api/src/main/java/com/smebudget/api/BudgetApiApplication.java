package com.smebudget.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * Entry point of the SME budget API.
 * The default in-memory test user is switched off because logins come from the app_user table.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BudgetApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BudgetApiApplication.class, args);
    }
}
