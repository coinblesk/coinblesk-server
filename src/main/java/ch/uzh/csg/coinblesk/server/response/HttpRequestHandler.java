package ch.uzh.csg.coinblesk.server.response;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

import net.minidev.json.JSONObject;

import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.reactor.IOReactorException;

import ch.uzh.csg.coinblesk.server.util.Config;

/**
 * Prepares the http request for post, get
 *
 */
public class HttpRequestHandler {
	
	/**
	 * build the http async client with connection timeout and pooling
	 * 
	 * @return CloseableHttpAsyncClient
	 * @throws IOReactorException
	 */
	public static CloseableHttpAsyncClient createDefaultHttpsAsyncClient() throws IOReactorException {
		RequestConfig.Builder requestBuilder = RequestConfig.custom();
		requestBuilder.setConnectTimeout(Config.HTTP_CONNECTION_TIMEOUT);
		requestBuilder.setConnectionRequestTimeout(Config.HTTP_CONNECTION_TIMEOUT);
		requestBuilder.setSocketTimeout(Config.HTTP_SOCKET_TIMEOUT);
		PoolingNHttpClientConnectionManager cm = new PoolingNHttpClientConnectionManager(
				new DefaultConnectingIOReactor(IOReactorConfig.DEFAULT));
		cm.setMaxTotal(Config.DEFAULT_MAX_TOTAL_CONNECTIONS);
		cm.setDefaultMaxPerRoute(Config.DEFAULT_MAX_CONNECTIONS_PER_ROUTE);
		HttpAsyncClientBuilder builder = HttpAsyncClientBuilder.create();
		builder.setConnectionManager(cm);
		builder.setDefaultRequestConfig(requestBuilder.build());
		return builder.build();
	}

	/**
	 * build the http client with connection timeout and pooling
	 * 
	 * @return CloseableHttpClient
	 */
	public static CloseableHttpClient createDefaultHttpsClient() {
		RequestConfig.Builder requestBuilder = RequestConfig.custom();
		requestBuilder.setConnectTimeout(Config.HTTP_CONNECTION_TIMEOUT);
		requestBuilder.setConnectionRequestTimeout(Config.HTTP_CONNECTION_TIMEOUT);
		requestBuilder.setSocketTimeout(Config.HTTP_SOCKET_TIMEOUT);
		PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
		cm.setMaxTotal(Config.DEFAULT_MAX_TOTAL_CONNECTIONS);
		cm.setDefaultMaxPerRoute(Config.DEFAULT_MAX_CONNECTIONS_PER_ROUTE);
		HttpClientBuilder builder = HttpClientBuilder.create();
		builder.setConnectionManager(cm);
		builder.setDefaultRequestConfig(requestBuilder.build());
		builder.setMaxConnPerRoute(20);
		return builder.build();
	}

	/**
	 * build the http client with connection timeout and pooling
	 * 
	 * @return CloseableHttpClient
	 */
	public static CloseableHttpClient createDefaultHttpsPaymentClient() {
		RequestConfig.Builder requestBuilder = RequestConfig.custom();
		requestBuilder.setConnectTimeout(Config.HTTP_CONNECTION_TIMEOUT_PAYMENT);
		requestBuilder.setConnectionRequestTimeout(Config.HTTP_CONNECTION_TIMEOUT_PAYMENT);
		requestBuilder.setSocketTimeout(Config.HTTP_SOCKET_TIMEOUT_PAYMENT);
		PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
		cm.setMaxTotal(Config.DEFAULT_MAX_TOTAL_CONNECTIONS);
		cm.setDefaultMaxPerRoute(Config.DEFAULT_MAX_CONNECTIONS_PER_ROUTE);
		HttpClientBuilder builder = HttpClientBuilder.create();
		builder.setConnectionManager(cm);
		builder.setDefaultRequestConfig(requestBuilder.build());
		builder.setMaxConnPerRoute(20);
		return builder.build();
	}
	
	/**
	 * Creates HttpPost request.
	 * 
	 * @param url
	 * @param jsonObject
	 * @param postParameters
	 * @return HttpPost
	 * @throws UnsupportedEncodingException
	 */
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
	
	/**
	 * Creates HttpGet request.
	 * 
	 * @param url
	 * @param jsonObject
	 * @param getParameters
	 * @return HttpGet
	 * @throws UnsupportedEncodingException
	 */
	private static HttpGet createGet(String url, JSONObject jsonObject, List<NameValuePair> getParameters) throws UnsupportedEncodingException {
		HttpGet get = new HttpGet(url);
		if(jsonObject != null || getParameters != null){			
			if(getParameters != null && !getParameters.isEmpty()){
				JSONObject obj = new JSONObject();
				obj.put("params", getParameters);
				get.addHeader("params", obj.toString());
			}else if(jsonObject != null){
				get.addHeader("params", jsonObject.toString());
			}
		}
		return get;
	}
	
	/**
	 * 
	 * @param postParameters
	 * @param url
	 * @return HttpPost
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public static HttpPost preparePost(List<NameValuePair> postParameters, String url) throws ClientProtocolException, IOException {
		HttpPost post = createPost(url, null, postParameters);
		return post;
    }
	
	/**
	 * 
	 * @param jsonObject
	 * @param url
	 * @return HttpPost
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public static HttpPost preparePost(JSONObject jsonObject, String url) throws ClientProtocolException, IOException {
		HttpPost post = createPost(url, jsonObject, null);
		return post;
	}
	
	/**
	 * 
	 * @param jsonObject
	 * @param url
	 * @return HttpUriRequest
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public static HttpUriRequest prepareGet(JSONObject jsonObject, String url) throws ClientProtocolException, IOException {
		HttpUriRequest request = createGet(url, jsonObject, null);
		return request;
	}
	
	/**
	 * 
	 * @param getParameters
	 * @param url
	 * @return HttpUriRequest
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public static HttpUriRequest prepareGet(List<NameValuePair> getParameters, String url) throws ClientProtocolException, IOException {
		HttpUriRequest request = createGet(url, null, getParameters);
		return request;
	}
	
	/**
	 * 
	 * @param url
	 * @return HttpUriRequest
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public static HttpUriRequest prepareGet(String url) throws ClientProtocolException, IOException {
		HttpUriRequest request = createGet(url, null, null);
		return request;
	}
	
	/**
	 * 
	 * @param jsonObj
	 * @param url
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public static CloseableHttpResponse prepPostResponse(JSONObject jsonObj, String url) throws ClientProtocolException, IOException {
		CloseableHttpClient httpClient = createDefaultHttpsClient();
		HttpPost post = preparePost(jsonObj, url);
		return httpClient.execute(post);
	}

	/**
	 * 
	 * @param postParameters
	 * @param url
	 * @return
	 * @throws ClientProtocolException
	 * @throws IOException
	 */
	public static CloseableHttpResponse prepPostResponse(List<NameValuePair> postParameters, String url) throws ClientProtocolException, IOException {
		CloseableHttpClient httpClient = createDefaultHttpsClient();
		HttpPost post = preparePost(postParameters, url);
		return httpClient.execute(post);
	}
	
}
