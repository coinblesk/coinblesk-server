package ch.uzh.csg.coinblesk.server.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.OneToMany;
import javax.persistence.Table;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;


@Entity(name = "KEYS")
@Table(indexes = {
    @Index(name = "CLIENT_PUB_KEY_INDEX", columnList = "CLIENT_PUBLIC_KEY", unique=true)})
public class Keys implements Serializable {

    private static final long serialVersionUID = -7496348013847426913L;

    @Id
    @GeneratedValue(strategy=GenerationType.SEQUENCE)
    private long id;
    
    @Column(name = "CLIENT_PUBLIC_KEY", unique=true, nullable=false, updatable = false, length=255)
    private byte[] clientPublicKey;
    
    @Column(name = "SERVER_PUBLIC_KEY", unique=true, nullable = false, updatable = false, length=255)
    private byte[] serverPublicKey;
    
    @Column(name = "SERVER_PRIVATE_KEY", unique=true, nullable = false, updatable = false, length=255)
    private byte[] serverPrivateKey;
    
    @Column(name = "TIME_CREATED", updatable = false, nullable = false)
	private long timeCreated;
    
    @OneToMany(mappedBy="keys", fetch = FetchType.EAGER)
    private List<AddressEntity> addresses; 
    
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
    
    public long timeCreated() {
    	return timeCreated;
    }
    
    public Keys timeCreated(long timeCreatedSeconds) {
    	this.timeCreated = timeCreatedSeconds;
    	return this;
    }

	public List<AddressEntity> addresses() {
		return addresses;
	}
    
	public Keys addresses(List<AddressEntity> addresses) {
		this.addresses = addresses;
		return this;
	}
	
	public List<Address> btcAddresses(NetworkParameters params) {
		List<Address> addressList = new ArrayList<>(addresses.size());
		for (AddressEntity e : addresses) {
			addressList.add(e.toAddress(params));
		}
		// make explicit that this list is not stored in DB!
		return Collections.unmodifiableList(addressList);
	}
}
