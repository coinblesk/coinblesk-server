/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.coinblesk.server.utilTest;

import javax.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author draft
 */
@Configuration
public class TestBean {
    @PostConstruct 
    public void init() {
        System.setProperty("db.injectCredentials", "false");
    }
}
