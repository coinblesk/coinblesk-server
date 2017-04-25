package com.coinblesk.server.dto;

import java.util.Date;

import com.coinblesk.server.config.UserRole;

import lombok.Data;

@Data
public class UserAccountAdminDTO {

	private String email;
	private long balance;
	private boolean deleted;
	private UserRole userRole;
	private Date creationDate;

}
