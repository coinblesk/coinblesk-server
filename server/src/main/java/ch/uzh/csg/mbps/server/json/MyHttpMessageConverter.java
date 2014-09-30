package ch.uzh.csg.mbps.server.json;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;

import net.minidev.json.JSONObject;

import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

import ch.uzh.csg.mbps.responseobject.TransferObject;

public class MyHttpMessageConverter extends AbstractHttpMessageConverter<TransferObject> {

	@Override
    protected boolean supports(Class<?> clazz) {
	    return true;
    }

	@Override
    protected TransferObject readInternal(Class<? extends TransferObject> clazz, HttpInputMessage inputMessage)
            throws IOException, HttpMessageNotReadableException {
		try {
	        TransferObject t = clazz.newInstance();
	        String body = convertStreamToString(inputMessage.getBody());
	        t.decode(body);
		    return t;
        } catch (Exception e) {
	        e.printStackTrace();
	        return null;
        }
    }

	@Override
    protected void writeInternal(TransferObject t, HttpOutputMessage outputMessage) throws IOException,
            HttpMessageNotWritableException {
		JSONObject o = new JSONObject();
		try {
	        t.encode(o);
	        outputMessage.getBody().write(o.toString().getBytes());
        } catch (Exception e) {
	        e.printStackTrace();
        }
    }

	//TODO: move this to a more appropriated utils class
    public String convertStreamToString(InputStream is) throws IOException {
        /*
         * To convert the InputStream to String we use the Reader.read(char[]
         * buffer) method. We iterate until the Reader return -1 which means
         * there's no more data to read. We use the StringWriter class to
         * produce the string.
         */
        if (is != null) {
            Writer writer = new StringWriter();

            char[] buffer = new char[1024];
            try {
                Reader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, n);
                }
            } finally {
                is.close();
            }
            return writer.toString();
        } else {
            return "";
        }
    }
}
