package ch.uzh.csg.coinblesk.server.domain;

import java.io.Serializable;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

@Entity
public class SignedInput implements Serializable {

    private static final long serialVersionUID = -7496348013847426914L;
    
    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private long id;
    //@Column(name = "tx_hash", columnDefinition="binary(32)", nullable = false)
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
        if ((otherObj == null) || !(otherObj instanceof SignedInput)) {
            return false;
        }
        final SignedInput other = (SignedInput) otherObj;
        return new EqualsBuilder()
                .append(getLockTime(), other.getLockTime())
                .append(getTxHash(), other.getTxHash())
                .append(getIndex(), other.getIndex())
                .isEquals();
    }

}
