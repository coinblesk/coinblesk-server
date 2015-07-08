package ch.uzh.csg.coinblesk.server.bitcoin;

public class ValidRefundTransactionException extends InvalidTransactionException {

    private static final long serialVersionUID = 1L;
    
    public ValidRefundTransactionException(String msg) {
        super(msg);
    }

}
