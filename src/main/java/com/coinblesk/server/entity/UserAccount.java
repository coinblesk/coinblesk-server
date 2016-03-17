/*
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
import java.math.BigDecimal;
import java.util.Date;
import java.util.Set;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
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
    @Index(name = "USERNAME_INDEX", columnList = "USERNAME")})
public class UserAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "ID", nullable = false)
    private long id;
    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "CREATION_DATE", nullable = false)
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
    @ElementCollection(targetClass=UserRole.class, fetch=FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name="USER_ROLES", indexes = {
    		@Index(name = "USER_ROLES_INDEX", columnList = "USER_ACCOUNT_ID"), 
    		@Index(name="user_role_unique", unique=true, columnList="user_account_id,user_role")})
    @Column(name="USER_ROLE")
    private Set<UserRole> userRoles;
    
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
    
    public Set<UserRole> getUserRoles() {
    	return userRoles;
    }
    
    public UserAccount setUserRoles(Set<UserRole> userRoles) {
    	this.userRoles = userRoles;
    	return this;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder()
            .append("id: ")
            .append(getId())
            .append(", username: ")
            .append(getUsername())
            .append(", email: ")
            .append(getEmail())
            .append(", isDeleted: ")
            .append(isDeleted())
            .append(", creationDate: ")
            .append(getCreationDate())
            .append(", balance: ")
            .append(getBalance())
            .append(", emailToken: ")
            .append(getEmailToken());
        sb.append(", userRoles: ");
        userRoles.forEach(r -> sb.append(r.name()).append(";"));
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
                .append(getUserRoles(), other.getUserRoles())
                .isEquals();
                
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(31, 59)
        		.append(getId())
                .append(getUsername())
                .append(getPassword())
                .append(getEmail())
                .append(getCreationDate())
                .append(getBalance())
                .append(getUserRoles())
                .toHashCode();
    }
}
