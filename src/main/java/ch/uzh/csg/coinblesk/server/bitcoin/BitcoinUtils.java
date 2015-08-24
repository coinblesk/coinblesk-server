package ch.uzh.csg.coinblesk.server.bitcoin;

import javax.xml.bind.DatatypeConverter;

import org.bitcoinj.core.TransactionOutPoint;

public class BitcoinUtils {
    
    public static String getOutpointHash(TransactionOutPoint outpoint) {
        return DatatypeConverter.printHexBinary(outpoint.getHash().getBytes()).toLowerCase();
    }

}
