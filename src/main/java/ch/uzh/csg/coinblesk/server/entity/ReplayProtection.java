/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.entity;

import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

/**
 *
 * @author draft
 */

@Entity(name = "REPLAY_PROTECTION")
@Table(indexes = {
    @Index(name = "CLIENT_PUBLIC_KEY_INDEX", columnList = "CLIENT_PUBLIC_KEY")})
public class ReplayProtection {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID", nullable = false)
    private long id;
    
    @Column(name = "CLIENT_PUBLIC_KEY", updatable = false, nullable = false, length=255)
    private byte[] clientPublicKey;
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "SEEN_DATE", updatable = false, nullable = false)
    private Date seenDate;
    
    public long getId() {
        return id;
    }

    public ReplayProtection setId(long id) {
        this.id = id;
        return this;
    }
    
    public byte[] clientPublicKey() {
        return clientPublicKey;
    }
    
    public ReplayProtection clientPublicKey(byte[] clientPublicKey) {
        this.clientPublicKey = clientPublicKey;
        return this;
    }
    
    public Date seenDate() {
        return seenDate;
    }

    public ReplayProtection seenDate(Date seenDate) {
        this.seenDate = seenDate;
        return this;
    }
}
