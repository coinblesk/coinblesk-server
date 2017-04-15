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
 * @author Alessandro De Carli
 * @author Thomas Bocek
 */

@Entity(name = "TX_QUEUE")
public class TxQueue implements Serializable {
	private static final long serialVersionUID = -7496348013847426945L;

	@Id
	@Column(name = "TX_HASH", updatable = false)
	private byte[] txHash;

	@Lob
	@Column(name = "TX", nullable = false, updatable = false)
	private byte[] tx;

	@Temporal(TemporalType.TIMESTAMP)
	@Column(name = "CREATION_DATE", nullable = false)
	private Date creationDate;


	public TxQueue txHash(byte[] txHash) {
		this.txHash = txHash;
		return this;
	}

	public TxQueue tx(byte[] tx) {
		this.tx = tx;
		return this;
	}

	public TxQueue creationDate(Date creationDate) {
		this.creationDate = creationDate;
		return this;
	}

	public byte[] tx() {
		return tx;
	}

}
