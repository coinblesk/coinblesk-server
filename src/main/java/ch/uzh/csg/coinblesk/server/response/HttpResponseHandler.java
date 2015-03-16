package ch.uzh.csg.coinblesk.server.response;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;

import ch.uzh.csg.coinblesk.responseobject.TransferObject;
import ch.uzh.csg.coinblesk.server.util.Config;

/**
 * Decodes and returns the response of the http request
 * 
 *
 */
public class HttpResponseHandler {

	
	public static <T extends TransferObject> T getResponse(T response, CloseableHttpResponse responseBody){
		try {
			HttpEntity entity1 = responseBody.getEntity();
			String respString = EntityUtils.toString(entity1);
			if(respString != null && respString.trim().length() > 0) {
				response.decode(respString);
			} else {
				response.setSuccessful(false);
				response.setMessage(Config.FAILED);
			}
		} catch (Exception e) {
			//if response not correct store account into db for hourly tasks
			response.setSuccessful(false);
			response.setMessage(Config.FAILED);
		}
		
		return response;
	}
}
