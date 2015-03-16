package ch.uzh.csg.coinblesk.server.json;

import java.io.IOException;

import net.minidev.json.JSONObject;
import ch.uzh.csg.coinblesk.responseobject.TransferObject;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class JSONObjectSerializer extends JsonSerializer<TransferObject> {
	@Override
	public void serialize(TransferObject item, JsonGenerator jgen, SerializerProvider provider) throws IOException,
	        JsonProcessingException {
		JSONObject jsonObject = new JSONObject();
		try {
			item.encode(jsonObject);
			jgen.writeRaw(jsonObject.toJSONString());
		} catch (Exception e) {
			throw new IOException(e);
		}

	}
}
