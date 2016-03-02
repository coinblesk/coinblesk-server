package ch.uzh.csg.coinblesk.server.entity;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@Entity(name = "REFUND")
@Table(indexes = {
    @Index(name = "REFUND_INDEX", columnList = "REFUND_TX")})
public class Refund implements Serializable {

    private static final long serialVersionUID = -7496348013847426913L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID", nullable = false)
    private long id;
    
    @Column(name = "CLIENT_PUBLIC_KEY", nullable = false, updatable = false, length=255)
    private byte[] clientPublicKey;
    
    @Lob
    @Column(name = "REFUND_TX", nullable = false, updatable = false)
    private byte[] refundTx;
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "CREATION_DATE", nullable = false)
    private Date creationDate;
    
    public long id() {
        return id;
    }

    public Refund id(long id) {
        this.id = id;
        return this;
    }
    
    public byte[] clientPublicKey() {
        return clientPublicKey;
    }
    
    public Refund clientPublicKey(byte[] clientPublicKey) {
        this.clientPublicKey = clientPublicKey;
        return this;
    }
    
    public byte[] refundTx() {
        return refundTx;
    }
    
    public Refund refundTx(byte[] refundTx) {
        this.refundTx = refundTx;
        return this;
    }
    
    public Date creationDate() {
        return creationDate;
    }

    public Refund creationDate(Date creationDate) {
        this.creationDate = creationDate;
        return this;
    }
}
