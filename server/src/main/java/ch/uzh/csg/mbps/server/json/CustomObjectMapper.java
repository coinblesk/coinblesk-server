package ch.uzh.csg.mbps.server.json;

import java.util.Set;

import org.reflections.Reflections;
import org.springframework.stereotype.Component;

import ch.uzh.csg.mbps.responseobject.TransferObject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

@Component
public class CustomObjectMapper extends ObjectMapper {

	private static final long serialVersionUID = -3817527144412772520L;
	final private static Set<Class<? extends TransferObject>> classes;

	static {
		Reflections reflections = new Reflections("ch.uzh.csg.mbps.responseobject");
		classes = reflections.getSubTypesOf(TransferObject.class);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public CustomObjectMapper() {
		SimpleModule module = new SimpleModule("custom json mapper");
		module.addSerializer(TransferObject.class, new JSONObjectSerializer());
		module.addSerializer(Object.class, new JSONNonSerializer());
		for (Class<? extends TransferObject> clazz : classes) {
			module.addDeserializer(clazz, new JSONObjectDeserializer(clazz));
		}
		registerModule(module);
	}
}
