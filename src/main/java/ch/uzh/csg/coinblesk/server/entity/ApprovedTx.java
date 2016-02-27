package ch.uzh.csg.coinblesk.server.entity;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.Table;

@Entity(name = "APPROVED_TX")
@Table(indexes = {
    @Index(name = "ADDRESS_FROM_INDEX", columnList = "ADDRESS_FROM"),
    @Index(name = "ADDRESS_TO_INDEX", columnList = "ADDRESS_TO")})
public class ApprovedTx implements Serializable {

    private static final long serialVersionUID = -7496348013847426913L;

    @Id
    @Column(name = "TX_HASH", updatable = false, length=255)
    private byte[] txHash;
    
    @Lob
    @Column(name = "TX", nullable = false, updatable = false, unique = true)
    private byte[] tx;
    
    @Column(name = "ADDRESS_FROM", nullable = false, updatable = false, length=255)
    private byte[] addressFrom;
    
    @Column(name = "ADDRESS_TO", nullable = false, updatable = false, length=255)
    private byte[] addressTo;
    
    @Column(name = "CREATIONDATE", nullable = false)
    private Date creationDate;
    
    public byte[] txHash() {
        return txHash;
    }
    
    public ApprovedTx txHash(byte[] txHash) {
        this.txHash = txHash;
        return this;
    }
    
    public byte[] tx() {
        return tx;
    }
    
    public ApprovedTx tx(byte[] tx) {
        this.tx = tx;
        return this;
    }
    
    public byte[] addressFrom() {
        return addressFrom;
    }
    
    public ApprovedTx addressFrom(byte[] addressFrom) {
        this.addressFrom = addressFrom;
        return this;
    }
    
    public byte[] addressTo() {
        return addressTo;
    }
    
    public ApprovedTx addressTo(byte[] addressTo) {
        this.addressTo = addressTo;
        return this;
    }
    
    public Date creationDate() {
        return creationDate;
    }

    public ApprovedTx creationDate(Date creationDate) {
        this.creationDate = creationDate;
        return this;
    }
}
