package ch.uzh.csg.coinblesk.server.bitcoin;

/**
 * This class holds a raw bitcoin transaction and an array of child numbers that
 * specify which keys have been used to sign the transaction
 * 
 * @author rvoellmy
 *
 */
public class TransactionAndChildNumbers {
    
    private byte[] tx;
    private int[] childNumbers[];
    
    public TransactionAndChildNumbers(byte[] tx, int[][] childNumbers) {
        super();
        this.tx = tx;
        this.childNumbers = childNumbers;
    }
    
    public byte[] getTx() {
        return tx;
    }
    public void setTx(byte[] tx) {
        this.tx = tx;
    }
    public int[][] getChildNumbers() {
        return childNumbers;
    }
    public void setChildNumbers(int[][] childNumbers) {
        this.childNumbers = childNumbers;
    }
    
}
