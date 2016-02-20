/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.bitcoinj.core.TransactionOutput;
import org.springframework.stereotype.Service;

/**
 *
 * @author draft
 */
@Service
public class TransactionService {

    public List<TransactionOutput> pendingOutputs() {
        return Collections.emptyList();
    }
    
}
