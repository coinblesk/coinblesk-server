package ch.uzh.csg.coinblesk.server.entity;

import java.io.Serializable;
import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Entity
public class ClientWatchingKey implements Serializable {

    private static final long serialVersionUID = -7496348013847426914L;

    @Id
    @Column(updatable = false)
    private String clientWatchingKey;
    
    
    public ClientWatchingKey() {      
    }
    
    public ClientWatchingKey(final String base58EncodedWatchingKey) {
        this.clientWatchingKey = base58EncodedWatchingKey;
    }

    @Override
    public boolean equals(final Object otherObj) {
    	return Objects.equals(clientWatchingKey, otherObj);
    }

}
