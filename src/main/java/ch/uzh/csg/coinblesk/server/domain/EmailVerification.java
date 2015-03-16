package ch.uzh.csg.coinblesk.server.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;

@Entity
@Table(name = "EMAILVERIFICATION", indexes = {@Index(name = "USER_ID_INDEX",  columnList="USER_ID")})
public class EmailVerification {
	
	@Id
	@Column(name="ID")
	@SequenceGenerator(name="pk_sequence",sequenceName="emailVerfication_id_seq", allocationSize=1)
	@GeneratedValue(strategy=GenerationType.SEQUENCE,generator="pk_sequence")
	private long id;
	@Column(name="USER_ID")
	private long userID;
	@Column(name="VERIFICATION_TOKEN")
	private String verificationToken;
	
	public EmailVerification(){
	}
	
	public EmailVerification(long UserID, String token) {
		this.userID = UserID;
		this.verificationToken = token;
	}

	public long getUserID() {
		return userID;
	}

	public void setUserID(long userID) {
		this.userID = userID;
	}

	public String getVerificationToken() {
		return verificationToken;
	}

	public void setVerificationToken(String verificationToken) {
		this.verificationToken = verificationToken;
	}
	
	public long getId() {
		return id;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("id: ");
		sb.append(getId());
		sb.append(" userId: ");
		sb.append(getUserID());
		sb.append(" verificationToken: ");
		sb.append(getVerificationToken());
		return sb.toString();
	}

}
