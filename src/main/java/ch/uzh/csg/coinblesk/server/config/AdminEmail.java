/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.config;

import org.springframework.mail.SimpleMailMessage;

/**
 *
 * @author draft
 */
public interface AdminEmail {
    public void send(String subject, String text);
}
