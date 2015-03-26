package ch.uzh.csg.coinblesk.server.utilTest;

import javax.naming.NamingException;

import org.springframework.http.MediaType;
import org.springframework.mock.jndi.SimpleNamingContextBuilder;

import ch.uzh.csg.coinblesk.server.util.CredentialsBean;

public class TestUtil {

	public static final MediaType APPLICATION_JSON_UTF8 = new MediaType(
			MediaType.APPLICATION_JSON.getType(),
			MediaType.APPLICATION_JSON.getSubtype());
	
	public static void mockJndi(CredentialsBean credentials) {
	    // mock JNDI
        SimpleNamingContextBuilder contextBuilder = new SimpleNamingContextBuilder();
        contextBuilder.bind("java:comp/env/bean/CredentialsBean", credentials);
        try {
            contextBuilder.activate();
        } catch (IllegalStateException | NamingException e) {
            e.printStackTrace();
        }
	}
	
	public static void mockJndi() {
	    mockJndi(new CredentialsBean());
	}

}
