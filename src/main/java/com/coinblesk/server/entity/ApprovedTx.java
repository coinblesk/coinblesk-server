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

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Lob;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

/**
 *
 * @author Thomas Bocek
 */
@Entity(name = "APPROVED_TX")
@Table(indexes = @Index(name = "CLIENT_PUBLIC_KEY_INDEX", columnList = "CLIENT_PUBLIC_KEY"))
public class ApprovedTx implements Serializable {

    private static final long serialVersionUID = -7496348013847426913L;

    @Id
    @Column(name = "TX_HASH", updatable = false, length = 255)
    private byte[] txHash;

    @Column(name = "CLIENT_PUBLIC_KEY", nullable = false, updatable = false, length = 255)
    private byte[] clientPublicKey;

    @Lob
    @Column(name = "TX", nullable = false, updatable = false, unique = true)
    private byte[] tx;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "CREATION_DATE", nullable = false)
    private Date creationDate;

    public byte[] txHash() {
        return txHash;
    }

    public ApprovedTx txHash(byte[] txHash) {
        this.txHash = txHash;
        return this;
    }

    public byte[] clientPublicKey() {
        return clientPublicKey;
    }

    public ApprovedTx clientPublicKey(byte[] clientPublicKey) {
        this.clientPublicKey = clientPublicKey;
        return this;
    }

    public byte[] tx() {
        return tx;
    }

    public ApprovedTx tx(byte[] tx) {
        this.tx = tx;
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
