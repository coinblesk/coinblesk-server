package ch.uzh.csg.coinblesk.server.entity;

import java.io.Serializable;
import java.util.Arrays;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;

import org.apache.commons.lang3.builder.HashCodeBuilder;

@Table(indexes = {	@Index(name = "txHashIndex", columnList ="txHash"), 
					@Index(name = "outputIndexIndex", columnList ="outputIndex")})
@Entity
public class SignedInput implements Serializable {

    private static final long serialVersionUID = -7496348013847426914L;
    
    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private long id;
    private byte[] txHash;
    private long lockTime;
    private long outputIndex;
    
    
    public SignedInput() {      
    }
    
    public SignedInput(long lockTime, byte[] txHash, long index) {
        this.lockTime = lockTime;
        this.txHash = txHash;
        this.outputIndex = index;
    }

    public long getLockTime() {
        return lockTime;
    }
    
    public void setLockTime(long lockTime) {
        this.lockTime = lockTime;
    }

    public byte[] getTxHash() {
        return txHash;
    }

    public long getIndex() {
        return outputIndex;
    }
    
    @Override
    public int hashCode() {
        return new HashCodeBuilder()
        .append(getLockTime())
        .append(getTxHash())
        .append(getIndex())
        .toHashCode();
    }

    @Override
    public boolean equals(final Object otherObj) {
    	if(otherObj == this) {
    		return true;
    	}
        if ((otherObj == null) || !(otherObj instanceof SignedInput)) {
            return false;
        }
        final SignedInput other = (SignedInput) otherObj;
        return lockTime == other.lockTime 
        		&& outputIndex == other.outputIndex
        		&& Arrays.equals(txHash, other.txHash);
    }

}
