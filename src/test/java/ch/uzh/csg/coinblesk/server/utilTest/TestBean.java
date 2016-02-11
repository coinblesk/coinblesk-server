/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.utilTest;

import ch.uzh.csg.coinblesk.server.config.AdminEmail;
import ch.uzh.csg.coinblesk.server.utils.Pair;
import java.util.LinkedList;
import java.util.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;

/**
 *
 * @author draft
 */

@Configuration
public class TestBean {
    final private Queue<Pair<String,String>> emailQueue = new LinkedList<>();
    
    
    @Bean
    public AdminEmail adminEmail() {
        return new AdminEmail() {
            @Override
            public void send(String subject, String text) {
                emailQueue.add(new Pair<>(subject,text));
            }

            @Override
            public int sentEmails() {
                return emailQueue.size();
            }
        };
    }
    
}
