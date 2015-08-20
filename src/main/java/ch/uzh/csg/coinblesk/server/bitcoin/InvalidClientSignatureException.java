package ch.uzh.csg.coinblesk.server.bitcoin;

public class InvalidClientSignatureException extends InvalidTransactionException {
    
    public InvalidClientSignatureException(String msg) {
        super(msg);
    }
    
    public InvalidClientSignatureException() {
        super();
    }

}
