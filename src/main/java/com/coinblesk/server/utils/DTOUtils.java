package com.coinblesk.server.utils;

import com.coinblesk.server.exceptions.MissingFieldException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.jsonwebtoken.impl.TextCodec;

import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;
import java.lang.reflect.Field;

/***
 * Helper class for everything related to data transfer objects.
 * Includes - Serialization
 *          - Deserializaion
 *          - Validation
 *
 * @author Sebastian Stephan
 */
public class DTOUtils {
	public static Gson gson = new GsonBuilder().create();

	/***
	 * Takes a Base64URL encoded string that represents some UTF8 json string and parses it back
	 * to the object given by the type parameter.
	 *
	 * Illegal null values are validated by {@link #validateNonNullFields(Object, Class)}
	 *
	 * @param base64Payload Base64URL encoded string containing a valid JSON representation of the given type
	 * @param typeofPayload The type that the JSON string represents
	 * @param <T> The type that the JSON string represents.
	 * @return The parsed and for illegal null-values checked object of type T.
	 */
	public static <T> T parsePayload(String base64Payload, Class<T> typeofPayload) {
		// Parse base64 payload back to json string
		String jsonPayload = fromBase64(base64Payload);

		// Parse string back to object
		T parsedObject = gson.fromJson(jsonPayload, typeofPayload);

		// Check for illegal null fields
		return validateNonNullFields(parsedObject, typeofPayload);
	}

	/***
	 * Takes an object of type T and checks if any fields annotated with the following annotations,
	 * is null:
	 * - {@link Nonnull} (javax)
	 * - {@link javax.validation.constraints.NotNull} (javax)
	 *
	 * This can happen if the object was created with reflection for example by GSON.
	 * An exception will be thrown if any field is null but was annotated with one of the above annotations.
	 *
	 * Note: The annotation must have Retention policy RUNTIME.
	 *
	 * @param obj The object to be checked
	 * @param objClass The
	 * @param <T> The type of the object to be checked
	 * @return The object obj
	 * @throws MissingFieldException If a {@link NotNull} or {@link Nonnull} annotated field is null
	 */
	private static <T> T validateNonNullFields(T obj, Class<T> objClass) {
		Field[] fields = objClass.getDeclaredFields();
		for(Field field : fields) {
			if (field.isAnnotationPresent(Nonnull.class) || field.isAnnotationPresent(NotNull.class)) {
				try {
					field.setAccessible(true);
					if (field.get(obj) == null)
						throw new MissingFieldException(field.getName());
				} catch (IllegalAccessException e) {
					throw new MissingFieldException(field.getName());
				}
			}
		}

		return obj;
	}

	private static String fromBase64(String input)
	{
		return TextCodec.BASE64URL.decodeToString(input);
	}

	public static String toBase64 (String input)
	{
		return TextCodec.BASE64URL.encode(input);
	}
}
