/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.entity;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 *
 * @author Thomas Bocek
 */
@Entity(name = "USER_ACCOUNT")
@Table(indexes = {
    @Index(name = "USERNAME_INDEX", columnList = "username")})
public class UserAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @SequenceGenerator(name = "pk_sequence", sequenceName = "useracccount_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "pk_sequence")
    @Column(name = "ID", nullable = false)
    private long id;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "CREATIONDATE", nullable = false)
    private Date creationDate;
    @Column(name = "USERNAME", nullable = true)
    private String username;
    @Column(name = "EMAIL", unique = true, nullable = false)
    private String email;
    @Column(name = "PASSWORD", nullable = false)
    private String password;
    @Column(name = "DELETED", nullable = false)
    private boolean deleted;
    @Column(name = "BALANCE", nullable = true, precision = 25, scale = 8)
    private BigDecimal balance;
    @Column(name = "EMAIL_TOKEN", nullable = true)
    private String emailToken;
    @Column(name = "VERSION", nullable = false)
    private byte version;
    
    public boolean isDeleted() {
        return deleted;
    }

    public UserAccount setDeleted(boolean deleted) {
        this.deleted = deleted;
        return this;
    }

    public long getId() {
        return id;
    }

    public UserAccount setId(long id) {
        this.id = id;
        return this;
    }

    public String getUsername() {
        return username;
    }

    public UserAccount setUsername(String username) {
        this.username = username;
        return this;
    }

    public String getEmail() {
        return email;
    }

    public UserAccount setEmail(String email) {
        this.email = email;
        return this;
    }

    public String getPassword() {
        return password;
    }

    public UserAccount setPassword(String password) {
        this.password = password;
        return this;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public UserAccount setBalance(BigDecimal balance) {
        this.balance = balance;
        return this;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public UserAccount setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
        return this;
    }

    public String getEmailToken() {
        return emailToken;
    }

    public UserAccount setEmailToken(String emailToken) {
        this.emailToken = emailToken;
        return this;
    }

    public byte getVersion() {
        return version;
    }

    public UserAccount setVersion(byte version) {
        this.version = version;
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("id: ");
        sb.append(getId());
        sb.append(", username: ");
        sb.append(getUsername());
        sb.append(", email: ");
        sb.append(getEmail());
        sb.append(", isDeleted: ");
        sb.append(isDeleted());
        sb.append(", creationDate: ");
        sb.append(getCreationDate());
        sb.append(", balance: ");
        sb.append(getBalance());
        sb.append(", emailToken: ");
        sb.append(getEmailToken());
        sb.append(", version: ");
        sb.append(getVersion());
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (o == this) {
            return true;
        }

        if (!(o instanceof UserAccount)) {
            return false;
        }

        UserAccount other = (UserAccount) o;
        return new EqualsBuilder().append(getId(), other.getId())
                .append(getUsername(), other.getUsername())
                .append(getPassword(), other.getPassword())
                .append(getEmail(), other.getEmail())
                .append(isDeleted(), other.isDeleted())
                .append(getCreationDate(), other.getCreationDate())
                .append(getBalance(), other.getBalance())
                .append(getVersion(), other.getVersion()).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(31, 59).append(getId())
                .append(getUsername()).append(getPassword())
                .append(getEmail())
                .append(getCreationDate()).append(isDeleted())
                .append(getBalance()).append(getVersion()).toHashCode();
    }
}
