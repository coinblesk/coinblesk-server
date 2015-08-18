package ch.uzh.csg.coinblesk.server.entity;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
public class ClientWatchingKey implements Serializable {

    private static final long serialVersionUID = -7496348013847426914L;

    @Id
    @Column(unique = true, updatable = false)
    private String clientWatchingKey;
    
    
    public ClientWatchingKey() {      
    }
    
    public ClientWatchingKey(String base58EncodedWatchingKey) {
        this.clientWatchingKey = base58EncodedWatchingKey;
    }

    @Override
    public boolean equals(final Object otherObj) {
        return clientWatchingKey.equals(otherObj);
    }

}
