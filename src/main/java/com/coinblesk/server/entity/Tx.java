/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.coinblesk.server.entity;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 *
 * @author Alessandro De Carli
 * @author Thomas Bocek
 */

@Entity(name = "TX")
@Table(indexes = {@Index(name = "TX_CLIENT_PUBLIC_KEY", columnList = "CLIENT_PUBLIC_KEY")})
public class Tx implements Serializable {
    private static final long serialVersionUID = -7496348013847426945L;

    @Id
    @Column(name = "TX_HASH", updatable = false, length = 255)
    private byte[] txHash;

    @Column(name = "CLIENT_PUBLIC_KEY", nullable = false, updatable = false, length = 255)
    private byte[] clientPublicKey;

    @Lob
    @Column(name = "TX", nullable = false, updatable = false)
    private byte[] tx;

    @Column(name = "APPROVED", nullable = false, updatable = false)
    private boolean approved;
    
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "CREATION_DATE", nullable = false)
    private Date creationDate;

    public byte[] txHash() {
        return txHash;
    }

    public Tx txHash(byte[] txHash) {
        this.txHash = txHash;
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
    
    public boolean approved() {
        return approved;
    }

    public Tx approved(boolean approved) {
        this.approved = approved;
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
