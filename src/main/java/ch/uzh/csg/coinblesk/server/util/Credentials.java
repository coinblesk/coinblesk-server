package ch.uzh.csg.coinblesk.server.util;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.log4j.Logger;

public class Credentials {

    private static Logger LOGGER = Logger.getLogger(Credentials.class);

    private Credentials() {
    }

    public static CredentialsBean getBean() {

        // get bitcoind credentials from server context
        Context initCtx;
        CredentialsBean credentials = null;
        
        try {
            initCtx = new InitialContext();
            Context envCtx = (Context) initCtx.lookup("java:comp/env");
            credentials = (CredentialsBean) envCtx.lookup("bean/CredentialsFactory");
        } catch (NamingException e) {
            LOGGER.error("Could not load credentials from server context");
        }

        return credentials;
    }

}
