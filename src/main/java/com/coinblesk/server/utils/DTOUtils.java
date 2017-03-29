package com.coinblesk.server.utils;

import com.coinblesk.server.dto.Signable;
import com.coinblesk.server.dto.SignedDTO;
import com.coinblesk.server.exceptions.MissingFieldException;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.jsonwebtoken.impl.TextCodec;
import org.bitcoinj.core.ECKey;

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
	 * Takes a SignedDTO with embedded payload and signature and returns the embedded DTO.
	 * Checks for valid signature.
	 * Checks for illegal null values in the embedded DTO.
	 *
	 * @param request: The SignedDTO that contains a payload and a signature.
	 * @param typeofPayload The type of the embedded DTO in the payload. Must be fo type {@link Signable}
	 * @param <T> The type of the embedded DTO in the payload. Must be fo type {@link Signable}
	 *
	 * @return The parsed checked DTO object of type T.
	 */
	public static <T extends Signable> T parseAndValidatePayload(SignedDTO request, Class<T> typeofPayload) {
		final String payloadBase64String = request.getPayload();

		// Parse base64 payload back to json string
		String jsonPayload = fromBase64(payloadBase64String);

		// Parse string back to object
		T parsedObject = gson.fromJson(jsonPayload, typeofPayload);

		// Check for illegal null fields
		validateNonNullFields(parsedObject, typeofPayload);

		// Check signature
		ECKey signingKey = SignatureUtils.getECKeyFromHexPublicKey(parsedObject.getPublicKeyForSigning());
		final String sigR = request.getSignature().getSigR();
		final String sigS = request.getSignature().getSigS();
		SignatureUtils.validateSignature(payloadBase64String, sigR, sigS, signingKey);

		return parsedObject;
	}

	/***
	 * Takes an object of type T and checks if any fields annotated with the following annotations,
	 * is null:
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
	 * @throws MissingFieldException If a {@link NotNull} annotated field is null
	 */
	private static <T> void validateNonNullFields(T obj, Class<T> objClass) {
		Field[] fields = objClass.getDeclaredFields();
		for(Field field : fields) {
			if (field.isAnnotationPresent(NotNull.class)) {
				try {
					field.setAccessible(true);
					if (field.get(obj) == null)
						throw new MissingFieldException(field.getName());
				} catch (IllegalAccessException e) {
					throw new MissingFieldException(field.getName());
				}
			}
		}
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
