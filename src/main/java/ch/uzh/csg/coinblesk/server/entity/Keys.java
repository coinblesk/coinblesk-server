package ch.uzh.csg.coinblesk.server.entity;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity(name = "KEYS")
public class Keys implements Serializable {

    private static final long serialVersionUID = -7496348013847426913L;

    @Id
    @Column(name = "CLIENT_PUBLIC_KEY", nullable = false, updatable = false, length=255)
    private byte[] clientPublicKey;
    
    @Column(name = "SERVER_PUBLIC_KEY", nullable = false, updatable = false, length=255)
    private byte[] serverPublicKey;
    
    @Column(name = "SERVER_PRIVATE_KEY", nullable = false, updatable = false, length=255)
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
