package ch.uzh.csg.coinblesk.server.entity;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@Entity(name = "BURNED_OUTPUTS")
@Table(indexes = {
    @Index(name = "BURNED_OUTPUTS_CLIENT_PUBLIC_KEY_INDEX", columnList = "CLIENT_PUBLIC_KEY")})
public class BurnedOutput implements Serializable {

    private static final long serialVersionUID = -7496348013847426913L;

    @Id
    @Column(name = "TX_OUTPOINT", updatable = false, length=255)
    private byte[] txOutpoint;
    
    @Column(name = "TX_OUTPOINT_COUNTER")
    private int txOutpointCounter;
    
    @Column(name = "CLIENT_PUBLIC_KEY", updatable = false, length=255)
    private byte[] clientPublicKey;
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "CREATION_DATE", nullable = false)
    private Date creationDate;
    
    public byte[] txOutpoint() {
        return txOutpoint;
    }
    
    public BurnedOutput txOutpoint(byte[] txOutpoint) {
        this.txOutpoint = txOutpoint;
        return this;
    }
    
    public int txOutpointCounter() {
        return txOutpointCounter;
    }
    
    public BurnedOutput txOutpointCounter(int txOutpointCounter) {
        this.txOutpointCounter = txOutpointCounter;
        return this;
    }
    
    public byte[] clientPublicKey() {
        return clientPublicKey;
    }
    
    public BurnedOutput clientPublicKey(byte[] clientPublicKey) {
        this.clientPublicKey = clientPublicKey;
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
