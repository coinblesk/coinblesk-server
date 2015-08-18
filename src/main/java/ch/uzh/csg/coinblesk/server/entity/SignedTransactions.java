package ch.uzh.csg.coinblesk.server.entity;

import java.io.Serializable;
import java.util.Date;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@Entity
public class SignedTransactions  implements Serializable {
	
	private static final long serialVersionUID = -90416753586662201L;
	
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private long id;
	private byte[] txHash;

	public byte[] getTxHash() {
		return txHash;
	}

	public void setTxHash(byte[] txHash) {
		this.txHash = txHash;
	}

}
