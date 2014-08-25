package ch.uzh.csg.mbps.server.json;

import java.io.IOException;

import ch.uzh.csg.mbps.responseobject.TransferObject;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

public class JSONObjectDeserializer<T extends TransferObject> extends JsonDeserializer<T> {

	final private Class<T> clazz;

	public JSONObjectDeserializer(Class<T> clazz) {
		this.clazz = clazz;
	}

	@Override
	public T deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
		try {
			T response = clazz.newInstance();
			String jsonString = jp.readValueAsTree().toString();
			response.decode(jsonString);
			return response;
		} catch (Exception e) {
			throw new IOException(e);
		}
		
	}
}
