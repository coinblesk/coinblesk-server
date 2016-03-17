/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * Created by ale on 10/03/16.
 */

@Entity(name = "TX")
@Table(indexes = {@Index(name = "TX_CLIENT_PUBLIC_KEY", columnList = "CLIENT_PUBLIC_KEY")})
public class Tx implements Serializable {
    private static final long serialVersionUID = -7496348013847426945L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID", nullable = false)
    private long id;

    @Column(name = "CLIENT_PUBLIC_KEY", nullable = false, updatable = false, length = 255)
    private byte[] clientPublicKey;

    @Lob
    @Column(name = "TX", nullable = false, updatable = false)
    private byte[] tx;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "CREATION_DATE", nullable = false)
    private Date creationDate;

    public long id() {
        return id;
    }

    public Tx id(long id) {
        this.id = id;
        return this;
    }

    public byte[] clientPublicKey() {
        return clientPublicKey;
    }

    public Tx clientPublicKey(byte[] clientPublicKey) {
        this.clientPublicKey = clientPublicKey;
        return this;
    }

    public byte[] tx() {
        return tx;
    }

    public Tx tx(byte[] tx) {
        this.tx = tx;
        return this;
    }

    public Date creationDate() {
        return creationDate;
    }

    public Tx creationDate(Date creationDate) {
        this.creationDate = creationDate;
        return this;
    }
}
