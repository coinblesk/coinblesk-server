package ch.uzh.csg.coinblesk.server.entity;

import java.io.Serializable;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.OneToMany;
import javax.persistence.Table;

@Entity(name = "KEYS")
@Table(indexes = {
    @Index(name = "P2SH_HASH_INDEX", columnList = "P2SH_HASH")})
public class Keys implements Serializable {

    private static final long serialVersionUID = -7496348013847426913L;

    @Id
    @GeneratedValue(strategy=GenerationType.SEQUENCE)
    private long id;
    
    @Column(name = "CLIENT_PUBLIC_KEY", updatable = false, length=255)
    private byte[] clientPublicKey;
    
    @Column(name = "P2SH_HASH", unique=true, nullable = false, updatable = false, length=255)
    private byte[] p2shHash;
    
    @Column(name = "SERVER_PUBLIC_KEY", unique=true, nullable = false, updatable = false, length=255)
    private byte[] serverPublicKey;
    
    @Column(name = "SERVER_PRIVATE_KEY", unique=true, nullable = false, updatable = false, length=255)
    private byte[] serverPrivateKey;
    
    @OneToMany(mappedBy="keys", fetch = FetchType.EAGER)
    private Set<AddressEntity> addresses; 
    
    public byte[] clientPublicKey() {
        return clientPublicKey;
    }
    
    public Keys clientPublicKey(byte[] clientPublicKey) {
        this.clientPublicKey = clientPublicKey;
        return this;
    }
    
    public byte[] p2shHash() {
        return p2shHash;
    }
    
    public Keys p2shHash(byte[] p2shHash) {
        this.p2shHash = p2shHash;
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
    
    public Set<AddressEntity> addresses() {
    	return addresses;
    }
    
    public Keys addresses(Set<AddressEntity> addresses) {
    	this.addresses = addresses;
    	return this;
    }
}
