package ch.uzh.csg.coinblesk.server.entity;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;

@Entity(name = "BURNED_OUTPUTS")
public class BurnedOutput implements Serializable {

    private static final long serialVersionUID = -7496348013847426913L;

    @Id
    @Column(name = "TX_OUTPOINT", updatable = false, length=255)
    private byte[] transactionOutpoint;
    
    @Column(name = "CREATIONDATE", nullable = false)
    private Date creationDate;
    
    public byte[] transactionOutpoint() {
        return transactionOutpoint;
    }
    
    public BurnedOutput transactionOutpoint(byte[] transactionOutpoint) {
        this.transactionOutpoint = transactionOutpoint;
        return this;
    }
    
    public Date creationDate() {
        return creationDate;
    }

    public BurnedOutput creationDate(Date creationDate) {
        this.creationDate = creationDate;
        return this;
    }
}
