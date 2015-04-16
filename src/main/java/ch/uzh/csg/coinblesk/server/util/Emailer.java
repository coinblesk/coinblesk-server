package ch.uzh.csg.coinblesk.server.util;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ch.uzh.csg.coinblesk.server.domain.UserAccount;
import ch.uzh.csg.coinblesk.server.service.UserAccountService;

/**
 * Email Service for sending emails from CoinBlesk to users.
 *
 */
@Service
public class Emailer {
    
    private static final Logger LOGGER = Logger.getLogger(Emailer.class);
    
    @Autowired
    private static Credentials credentials;

    /**
     * Sends initial Email Configuration Link to User via defined MailService
     * 
     * @param confirmationID
     * @param toEmail
     * @return Status if email has been sent to the user.
     */
    public void sendEmailConfirmationLink(String confirmationID, String toEmail) {

        String link = ServerProperties.getProperty("url.base") + ServerProperties.getProperty("url.server") + "/user/verify/" + confirmationID;
        String message = "Please verify your account by clicking on the following link: <a href = \"" + link + "\">" + link + "</a>";
        String subject = "MBPS Account Verification";
        sendEmail(message, subject, toEmail, null);
    }

    /**
     * Sends email with link to reset Password to emailAddress from UserAccount
     * 
     * @param user
     * @param resetPWToken
     */
    public void sendResetPasswordLink(UserAccount user, String resetPWToken) {

        String link = ServerProperties.getProperty("url.base") + ServerProperties.getProperty("url.server") + "/user/resetPassword/" + resetPWToken;
        String message = "Dear " + user.getUsername() + ",<br><br>Please reset your account password by clicking on the following link: <a href = \"" + link + "\">" + link + "</a>";
        String subject = "MBPS Account Password Reset";
        sendEmail(message, subject, user.getEmail(), null);
    }

    /**
     * Sends file with history of all transactions to Users email address.
     * 
     * @param userName
     * @param email
     * @param file
     */
    public void sendHistoryCSV(String userName, String email, File file) {
        String message = "Dear " + userName + ",<br><br>Attached you can find the requested history list.";
        String subject = "MBPS history list";
        sendEmail(message, subject, email, file);
    }

    /**
     * Non-blocking call to send the email, otherwise the client runs into a
     * timeout exception.
     * 
     * @param toEmail
     */
    private void sendEmail(String message, String subject, String toEmail, File attachment) {
        if (!UserAccountService.isTestingMode()) {
            EmailSenderTask task = new EmailSenderTask(message, subject, toEmail, attachment);
            new Thread(task).start();
        }
    }

    private class EmailSenderTask implements Runnable {
        private String message;
        private String subject;
        private String toEmail;
        private File attachment;

        protected EmailSenderTask(String message, String subject, String toEmail, File attachment) {
            this.message = message;
            this.subject = subject;
            this.toEmail = toEmail;
            this.attachment = attachment;
        }

        @Override
        public void run() {
            Properties props = ServerProperties.getProperties();
            
            Session session = Session.getInstance(props, new javax.mail.Authenticator() {
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(credentials.getEmailUsername(), credentials.getEmailPassword());
                }
            });

            try {
                Message message = new MimeMessage(session);
                message.setFrom(new InternetAddress(ServerProperties.getProperty("mail.from")));
                message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
                message.setSubject(subject);

                Multipart mp = new MimeMultipart();

                MimeBodyPart htmlPart = new MimeBodyPart();
                htmlPart.setContent(message, "text/html; charset=ISO-8859-1");
                mp.addBodyPart(htmlPart);

                if (attachment != null) {
                    MimeBodyPart mbp = new MimeBodyPart();
                    mbp.attachFile(attachment);
                    mbp.setHeader("Content-Type", "text/comma-separated-values; name=\"" + attachment.getName() + "\"");

                    mp.addBodyPart(mbp);
                }

                message.setContent(mp);

                Transport.send(message);

                // delete the file on the servers hd
                if (attachment != null) {
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
    public void send(String toEmail, String subj, String message) {
        sendEmail(message, subj, toEmail, null);
    }

    public void sendPayInAddressAsEmail(String username, String email, String payInAddress) {
        String message = "Dear " + username + ",<br><br>Your pay in address is " + payInAddress + ".";
        String subject = "MBPS Pay In Address";
        sendEmail(message, subject, email, null);
    }

    /**
     * Sends email to notify user that is account was updated from Role_User
     * user to Role_Both.
     * 
     * @param user
     * @param resetPWToken
     */
    public void sendUpdateRoleBothLink(UserAccount user) {
        String link = ServerProperties.getProperty("url.base") + ServerProperties.getProperty("url.server");
        String message = "Dear " + user.getUsername() + ",<br><br>Your account was updated! Now, you gained administration rights for the server at the following link:  <a href = \""
                + link + "\">" + link + "</a>";
        String subject = "MBPS Update Account";
        sendEmail(message, subject, user.getEmail(), null);
    }

    /**
     * Sends email with link to create an account as admin.
     * 
     * @param user
     * @param token
     */
    public void sendCreateRoleAdminLink(String email, String adminRoleToken) {
        String server = ServerProperties.getProperty("url.base") + ServerProperties.getProperty("url.server");
        String link = server + "/user/createAdmin/" + adminRoleToken;
        String message = "Dear user,<br><br>You are invited to adminsitred the following website: <a href = \"" + server + "\">" + server
                + "</a>. To create an account, please enter your credentials on the follwowing link: <a href = \"" + link + "\">" + link + "</a>";
        String subject = "MBPS Admin Account";
        sendEmail(message, subject, email, null);
    }

    // TODO: for mensa testrun only, delete afterwards
    /**
     * Sends an xls Report with the daily transactions for Account "MensaBinz"
     * to the defined email recipients.
     *
     * @param file
     */
    public void sendMensaReport(File file) {
        String message = "Im Anhang finden Sie das Excel-Sheet mit den heutigen Transaktionen der Mensa.";
        String subject = "[CoinBlesk] Tagestransaktionen - Mensa Bitcoin Testlauf";
        sendEmail(message, subject, "bitcoin@ifi.uzh.ch,binzmuehle@zfv.ch,debitoren@zfv.ch", file);
    }

    /**
     * 
     * @param emailsToSend
     * @param subjectToSend
     * @param messageToSend
     */
    public void sendMessageToAllUsers(String emailsToSend, String subjectToSend, String messageToSend) {
        String message = messageToSend;
        String subject = subjectToSend;
        sendEmail(message, subject, emailsToSend, null);
    }
}
