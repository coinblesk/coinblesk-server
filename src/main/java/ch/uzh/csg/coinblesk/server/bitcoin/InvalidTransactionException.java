package ch.uzh.csg.coinblesk.server.bitcoin;

public class InvalidTransactionException extends Exception {
    
    private static final long serialVersionUID = -459932833358942621L;

    public InvalidTransactionException(String msg) {
        super(msg);
    }
    
    public InvalidTransactionException() {
        super();
    }

}
