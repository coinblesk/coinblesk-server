package ch.uzh.csg.coinblesk.server.util;

public class CredentialsBean {
    
    private String bitcoindUsername;
    private String bitcoindPassword;
    private String bitcoindEncryptionKey;
    private String emailUsername;
    private String emailPassword;
    private String bitstampUsername;
    private String bitstampApiKey;
    private String bitstampSecretKey;
    
    public String getEmailUsername() {
        return emailUsername;
    }
    public void setEmailUsername(String emailUsername) {
        this.emailUsername = emailUsername;
    }
    public String getEmailPassword() {
        return emailPassword;
    }
    public void setEmailPassword(String emailPassword) {
        this.emailPassword = emailPassword;
    }
    public String getBitstampApiKey() {
        return bitstampApiKey;
    }
    public void setBitstampApiKey(String bitstampApiKey) {
        this.bitstampApiKey = bitstampApiKey;
    }
    public String getBitstampSecretKey() {
        return bitstampSecretKey;
    }
    public void setBitstampSecretKey(String bitstampSecretKey) {
        this.bitstampSecretKey = bitstampSecretKey;
    }
    public String getBitcoindEncryptionKey() {
        return bitcoindEncryptionKey;
    }
    public void setBitcoindEncryptionKey(String bitcoindEncryptionKey) {
        this.bitcoindEncryptionKey = bitcoindEncryptionKey;
    }
    public String getBitstampUsername() {
        return bitstampUsername;
    }
    public void setBitstampUsername(String bitstampUsername) {
        this.bitstampUsername = bitstampUsername;
    }
    public String getBitcoindUsername() {
        return bitcoindUsername;
    }
    public void setBitcoindUsername(String bitcoindUsername) {
        this.bitcoindUsername = bitcoindUsername;
    }
    public String getBitcoindPassword() {
        return bitcoindPassword;
    }
    public void setBitcoindPassword(String bitcoindPassword) {
        this.bitcoindPassword = bitcoindPassword;
    }

    
}
