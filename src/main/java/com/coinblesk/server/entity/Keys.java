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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

/**
 *
 * @author Thomas Bocek
 */
@Entity(name = "KEYS")
public class Keys implements Serializable {

    private static final long serialVersionUID = -7496348013847426913L;

    @Id
    @Column(name = "CLIENT_PUBLIC_KEY", updatable = false, length = 255)
    private byte[] clientPublicKey;

    @Column(name = "SERVER_PUBLIC_KEY", unique = true, nullable = false, updatable = false, length = 255)
    private byte[] serverPublicKey;

    @Column(name = "SERVER_PRIVATE_KEY", unique = true, nullable = false, updatable = false, length = 255)
    private byte[] serverPrivateKey;

    public byte[] clientPublicKey() {
        return clientPublicKey;
    }

    public Keys clientPublicKey(byte[] clientPublicKey) {
        this.clientPublicKey = clientPublicKey;
        return this;
    }

    public byte[] serverPublicKey() {
        return serverPublicKey;
    }

    public Keys serverPublicKey(byte[] serverPublicKey) {
        this.serverPublicKey = serverPublicKey;
        return this;
    }

    public byte[] serverPrivateKey() {
        return serverPrivateKey;
    }

    public Keys serverPrivateKey(byte[] serverPrivateKey) {
        this.serverPrivateKey = serverPrivateKey;
        return this;
    }
}
