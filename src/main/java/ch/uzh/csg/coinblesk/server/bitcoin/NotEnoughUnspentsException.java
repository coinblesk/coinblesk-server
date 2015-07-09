package ch.uzh.csg.coinblesk.server.bitcoin;

public class NotEnoughUnspentsException extends InvalidTransactionException {

    private static final long serialVersionUID = 5503248967581478507L;
    
    public NotEnoughUnspentsException(String msg) {
        super(msg);
    }

}
