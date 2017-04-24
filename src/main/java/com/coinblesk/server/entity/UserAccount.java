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

import static javax.persistence.EnumType.STRING;
import static javax.persistence.TemporalType.TIMESTAMP;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.OneToOne;
import javax.persistence.Temporal;

import com.coinblesk.server.config.UserRole;

/**
 * @author Thomas Bocek
 */
@Entity(name = "USER_ACCOUNT")
public class UserAccount implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	@Column(name = "ID", nullable = false)
	private long id;

	@Temporal(TIMESTAMP)
	@Column(name = "CREATION_DATE", nullable = false)
	private Date creationDate;

	@Column(name = "EMAIL", unique = true, nullable = false)
	private String email;

	@Column(name = "PASSWORD", nullable = false)
	private String password;

	@Column(name = "DELETED", nullable = false)
	private boolean deleted;

	@Column(name = "BALANCE", precision = 25, scale = 8)
	private BigDecimal balance;

	@Column(name = "ACTIVATION_EMAIL_TOKEN")
	private String activationEmailToken;

	@Column(name = "FORGOT_EMAIL_TOKEN")
	private String forgotEmailToken;

	@Enumerated(STRING)
	@Column(name = "USER_ROLE")
	private UserRole userRole;

	@OneToOne
	@JoinColumn(unique = true)
	private Account account;

	public boolean isDeleted() {
		return deleted;
	}

	public UserAccount setDeleted(boolean deleted) {
		this.deleted = deleted;
		return this;
	}

	private long getId() {
		return id;
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

	public String getActivationEmailToken() {
		return activationEmailToken;
	}

	public UserAccount setActivationEmailToken(String emailToken) {
		this.activationEmailToken = emailToken;
		return this;
	}

	public String getForgotEmailToken() {
		return forgotEmailToken;
	}

	public UserAccount setForgotEmailToken(String forgotEmailToken) {
		this.forgotEmailToken = forgotEmailToken;
		return this;
	}

	public UserRole getUserRole() {
		return userRole;
	}

	public UserAccount setUserRole(UserRole userRole) {
		this.userRole = userRole;
		return this;
	}

	public boolean isEmailVerified() {
		return this.activationEmailToken == null;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder()
				.append("id: ").append(getId())
				.append(", email: ").append(getEmail())
				.append(", isDeleted: ").append(isDeleted())
				.append(", creationDate: ").append(getCreationDate())
				.append(", balance: ").append(getBalance())
				.append(", emailToken: ").append(getActivationEmailToken())
				.append(", userRoles: ").append(getUserRole());
		return sb.toString();
	}
}
