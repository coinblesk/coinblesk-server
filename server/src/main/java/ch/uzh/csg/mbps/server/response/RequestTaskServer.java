package ch.uzh.csg.mbps.server.response;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

import net.minidev.json.JSONObject;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import ch.uzh.csg.mbps.responseobject.TransferObject;

public abstract class RequestTaskServer<I extends TransferObject, O extends TransferObject> implements FutureCallback<O> {

	private static final int HTTP_CONNECTION_TIMEOUT = 3 * 1000;
	private static final int HTTP_SOCKET_TIMEOUT = 5 * 1000;
	
	final private I requestObject;
	final private O responseObject;
	final private IAsyncTaskCompleteListener<O> callback;
	final private String url;
	
	
	public RequestTaskServer(I requestObject, O responseObject, String url, IAsyncTaskCompleteListener<O> callback) {
		this.requestObject = requestObject;
		this.responseObject = responseObject;
		this.callback = callback;
		this.url = url;
	}
	
	@Override
	public void cancelled() {
		O cancelled = createFailed("Request was cancelled");
		this.callback.onTaskComplete(cancelled);
	}
	
	@Override
	public void completed(O response) {
		this.callback.onTaskComplete(response);
	}
	
	
	@Override
	public void failed(Exception e) {
		O failed = createFailed("Request failed");
		this.callback.onTaskComplete(failed);
	}

	protected abstract O responseCompleted(I restTemplate) throws Exception;

	public String getURL() {
		return url;
	}
	
	private static HttpPost createPost(String url, JSONObject jsonObject, List<NameValuePair> postParameters) throws UnsupportedEncodingException {
		HttpPost post = new HttpPost(url);
		
		if (postParameters != null && !postParameters.isEmpty()) {
        	post.setEntity(new UrlEncodedFormEntity(postParameters));
        } else if (jsonObject != null) {
        	post.addHeader("Content-Type", "application/json;charset=UTF-8");
            post.addHeader("Accept", "application/json");
        	post.setEntity(new StringEntity(jsonObject.toString(), "UTF-8"));
        }
        return post;
	}
	
	private static HttpGet createGet(String url) throws UnsupportedEncodingException {
		HttpGet get = new HttpGet(url);
		get.addHeader("Accept", "application/json");
        return get;
	}

	public O createFailed(String failedMessage) {
		responseObject.setSuccessful(false);
		responseObject.setMessage(failedMessage);
		responseObject.setVersion(-1);
		return responseObject;
	}
	
	private HttpResponse executePost(List<NameValuePair> postParameters) throws ClientProtocolException, IOException {
		HttpClient httpclient = createDefaultHttpsClient();
		HttpPost post = createPost(url, null, postParameters);
		HttpResponse response = httpclient.execute(post);
		return response;
    }
	
	public HttpResponse executePost(JSONObject jsonObject) throws ClientProtocolException, IOException {
		HttpClient httpclient = createDefaultHttpsClient();
		HttpPost post = createPost(url, jsonObject, null);
		HttpResponse response = httpclient.execute(post);
		return response;
	}
	
	public HttpResponse executeGet() throws ClientProtocolException, IOException {
		HttpClient httpclient = createDefaultHttpsClient();
		HttpUriRequest request = createGet(url);
		HttpResponse response = httpclient.execute(request);
		return response;
	}
	
	private CloseableHttpClient createDefaultHttpsClient() {
		RequestConfig.Builder requestBuilder = RequestConfig.custom();
		requestBuilder = requestBuilder.setConnectTimeout(HTTP_CONNECTION_TIMEOUT);
		requestBuilder = requestBuilder.setSocketTimeout(HTTP_SOCKET_TIMEOUT);
		HttpClientBuilder builder = HttpClientBuilder.create();
		builder.setDefaultRequestConfig(requestBuilder.build());
		return builder.build();
	}

	public O execPost(JSONObject jsonObject) {
		try {
        	//request
        	HttpResponse response = executePost(jsonObject);
        	//reply
            StatusLine statusLine = response.getStatusLine();
            if(statusLine.getStatusCode() == HttpStatus.SC_OK){
            	HttpEntity entity1 = response.getEntity();
            	String responseString = EntityUtils.toString(entity1);
            	if(responseString != null && responseString.trim().length() > 0) {
            		responseObject.decode(responseString);
            	}
            	return responseObject;
            } else {
                //Closes the connection.
                response.getEntity().getContent().close();
                return createFailed(statusLine.getReasonPhrase());
            }
        } catch (Exception e) {
        	e.printStackTrace();
        	return createFailed(e.getMessage());
        }
	}
	
	public O execPost(List<NameValuePair> postParameters) {
		try {
        	//request
			HttpResponse response = executePost(postParameters);
			//reply
            StatusLine statusLine = response.getStatusLine();
            if(statusLine.getStatusCode() == HttpStatus.SC_OK){
            	HttpEntity entity1 = response.getEntity();
            	String responseString = EntityUtils.toString(entity1);
            	
            	if(responseString != null && responseString.trim().length() > 0) {
            		responseObject.decode(responseString);
            	}
            	return responseObject;
            } else{
                //Closes the connection.
                response.getEntity().getContent().close();
                return createFailed(statusLine.getReasonPhrase());
            }
        } catch (Exception e) {
        	e.printStackTrace();
        	return createFailed(e.getMessage());
        }
	}
	
	public O execGet( ) {
		try {
        	//request
			HttpResponse response = executeGet();
        	//reply
            StatusLine statusLine = response.getStatusLine();
            if(statusLine.getStatusCode() == HttpStatus.SC_OK){
            	HttpEntity entity1 = response.getEntity();
            	String responseString = EntityUtils.toString(entity1);
            	if(responseString != null && responseString.trim().length() > 0) {
            		responseObject.decode(responseString);
            	}
            	return responseObject;
            } else {
                //Closes the connection.
                response.getEntity().getContent().close();
                return createFailed(statusLine.getReasonPhrase());
            }
        } catch (Exception e) {
        	e.printStackTrace();
        	return createFailed(e.getMessage());
        }
	}
	
//	class  MyHttpClient extends DefaultHttpClient {
//		
//	    public MyHttpClient() {
//	    	HttpClientContext.create();
//	    }
//
//		@Override
//		public ClientConnectionManager getConnectionManager() {
//			SchemeRegistry registry = new SchemeRegistry();
//	        registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
//	        // Register for port 443 our SSLSocketFactory with our keystore
//	        // to the ConnectionManager
//	        registry.register(new Scheme("https", newSslSocketFactory(), 443));
//	        
//	        HttpParams params = new BasicHttpParams(); 
//	        return new ThreadSafeClientConnManager(params, registry);
//		}
//
//	    private SSLSocketFactory newSslSocketFactory() {
//	        try {
//	            // Get an instance of the Bouncy Castle KeyStore format
//	            KeyStore trusted = KeyStore.getInstance("BKS");
//	            // Get the raw resource, which contains the keystore with
//	            // your trusted certificates (root and any intermediate certs)
//	            InputStream in = context.getResources().openRawResource(R.raw.bitcoinkeystore);
//	            try {
//	                // Initialize the keystore with the provided trusted certificates
//	                // Also provide the password of the keystore
//	                trusted.load(in, "changeit".toCharArray());
//	            } finally {
//	                in.close();
//	            }
//	            // Pass the keystore to the SSLSocketFactory. The factory is responsible
//	            // for the verification of the server certificate.
//	            SSLSocketFactory sf = new SSLSocketFactory(trusted);
//	            // Hostname verification from certificate
//	            // http://hc.apache.org/httpcomponents-client-ga/tutorial/html/connmgmt.html#d4e506
//	            //sf.setHostnameVerifier(SSLSocketFactory.STRICT_HOSTNAME_VERIFIER);
//	            return sf;
//	        } catch (Exception e) {
//	            throw new AssertionError(e);
//	        }
//	    }
//	}
}
