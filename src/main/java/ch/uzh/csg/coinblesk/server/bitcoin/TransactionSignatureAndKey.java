package ch.uzh.csg.coinblesk.server.bitcoin;

import org.bitcoinj.core.ECKey;

public class TransactionSignatureAndKey {

    public final ECKey.ECDSASignature sig;
    public final ECKey pubKey;

    public TransactionSignatureAndKey(ECKey.ECDSASignature sig, ECKey pubKey) {
        this.sig = sig;
        this.pubKey = pubKey;
    }

}
