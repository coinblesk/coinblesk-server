package ch.uzh.csg.coinblesk.server.bitcoin;

public class DoubleSignatureRequestedException extends InvalidTransactionException {

    private static final long serialVersionUID = 1L;
    
    public DoubleSignatureRequestedException(String msg) {
        super(msg);
    }

}
