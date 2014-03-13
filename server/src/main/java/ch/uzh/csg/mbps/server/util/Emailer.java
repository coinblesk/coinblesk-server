package ch.uzh.csg.mbps.server.util;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.log4j.Logger;

import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.service.UserAccountService;

/**
 * Email Service for sending emails from MBPS to users.
 *
 */
public class Emailer {
	private static Logger LOGGER = Logger.getLogger(Emailer.class);
	private static String messageText;
	private static String subject;
	
	/**
	 * Sends initial Email Configuration Link to User via defined MailService
	 * 
	 * @param confirmationID
	 * @param toEmail
	 * @return Status if email has been sent to the user.
	 */
	public static void sendEmailConfirmationLink(String confirmationID, String toEmail){
		messageText = "Please verify your account by clicking on the following link: " + Config.BASE_URL + "/user/verify/" + confirmationID;
		subject = "MBPS Account Verification";
		sendEmail(toEmail, null);
	}
	
	/**
	 * Sends email with link to reset Password to emailAddress from UserAccount
	 * 
	 * @param user
	 * @param resetPWToken
	 */
	public static void sendResetPasswordLink(UserAccount user, String resetPWToken) {
		messageText = "Dear " + user.getUsername() + ",<br><br>Please reset your account password by clicking on the following link: " + Config.BASE_URL + "/user/resetPassword/" + resetPWToken;
		subject = "MBPS Account Password Reset";
		sendEmail(user.getEmail(), null);
	}
	
	/**
	 * Sends file with history of all transactions to Users email address.
	 * @param userName
	 * @param email
	 * @param file
	 */
	public static void sendHistoryCSV(String userName, String email, File file) {
		messageText = "Dear " + userName + ",<br><br>Attached you can find the requested history list.";
		subject = "MBPS history list";
		sendEmail(email, file);
	}
	
	/**
	 * Non-blocking call to send the email, otherwise the client runs into a
	 * timeout exception.
	 * 
	 * @param toEmail
	 */
	private static void sendEmail(String toEmail, File attachment) {
		if(!UserAccountService.isTestingMode()){
			EmailSenderTask task = new EmailSenderTask(toEmail, attachment);
			new Thread(task).start();
		}
	}

	private static class EmailSenderTask implements Runnable {
		private String toEmail;
		private File attachment;
		
		protected EmailSenderTask(String toEmail, File attachment) {
			this.toEmail = toEmail;
			this.attachment = attachment;
		}

		@Override
		public void run() {
			Properties props = new Properties();
			props.put("mail.smtp.auth", "true");
			props.put("mail.smtp.starttls.enable", "true");
			props.put("mail.smtp.host", "mail.nope.ch");
			props.put("mail.smtp.port", "587");
			props.put("mail.smtp.ssl.trust", "mail.nope.ch" );
	 
			Session session = Session.getInstance(props, new javax.mail.Authenticator() {
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(Config.EMAIL_USER, Config.EMAIL_PASSWORD);
				}
			});
	 
			try {
				Message message = new MimeMessage(session);
				message.setFrom(new InternetAddress(Config.FROM));
				message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
				message.setSubject(subject);
	 
				Multipart mp = new MimeMultipart();
				
				MimeBodyPart htmlPart = new MimeBodyPart();
				htmlPart.setContent(messageText, "text/html; charset=ISO-8859-1");
				mp.addBodyPart(htmlPart);
				
				if (attachment != null) {
			        MimeBodyPart mbp = new MimeBodyPart();
			        mbp.attachFile(attachment);
			        mbp.setHeader("Content-Type", "text/comma-separated-values; name=\""+attachment.getName()+"\"");

			        mp.addBodyPart(mbp);
				}
				
				message.setContent(mp);
				
				Transport.send(message);
				
				//delete the file on the servers hd
				if(attachment != null){
					attachment.delete();					
				}
			} catch (MessagingException | IOException e) {
				LOGGER.error("Couldn't send email. Reason: " + e.getMessage());
			}
		}
	}

	/**
	 * Sends an email message with defined message, subject to receiver email
	 * address.
	 * 
	 * @param toEmail
	 * @param subj
	 * @param message
	 */
	public static void send(String toEmail, String subj, String message) {
		messageText = message;
		subject = subj;
		sendEmail(toEmail, null);
	}
	
	public static void sendPayInAddressAsEmail(String username,String email, String payInAddress){
		messageText ="Dear " + username +",<br><br>Your pay in address is " + payInAddress +".";
		subject = "MBPS Pay In Address";
		sendEmail(email,null);
	}
	
}
