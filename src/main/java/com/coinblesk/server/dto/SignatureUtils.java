package com.coinblesk.server.dto;

import com.coinblesk.server.exceptions.InvalidSignatureException;
import com.coinblesk.server.exceptions.MissingFieldException;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.jsonwebtoken.impl.TextCodec;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.ECKey.ECDSASignature;
import org.bitcoinj.core.Sha256Hash;

import javax.annotation.Nonnull;
import javax.validation.constraints.NotNull;
import java.lang.reflect.Field;
import java.math.BigInteger;

/**
 * A helper class that provides functions to parse base64 encoded DTOs, checking of unwanted null values in
 * object instance variables, creating {@link org.bitcoinj.core.ECKey} from public keys and verifying ECDSA
 * signatures.
 *
 * @author Sebastian Stephan
 *
 */
public class SignatureUtils {
	private static Gson gson = new GsonBuilder().create();

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
		String jsonPayload = TextCodec.BASE64URL.decodeToString(base64Payload);

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
	 * @throws MissingFieldException
	 */
	public static <T> T validateNonNullFields(T obj, Class<T> objClass) {
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

	/***
	 * Converts a base16 hex formatted public key into an ECKey
	 * Since the ECKey contains only the public key, it can only be used
	 * for checking signatures.
	 *
	 * @param hexFormattedPublicKey Public key in hex format.
	 * @return ECKey with only public key
	 */
	public static ECKey getECKeyFromHexPublicKey(String hexFormattedPublicKey) {
		return ECKey.fromPublicOnly(BaseEncoding.base16().decode(hexFormattedPublicKey.toUpperCase()));
	}

	/***
	 * Hashes the given string with SHA-256 and checks the given signature against the given public key.
	 * Signature is given in the form of R and S.
	 *
	 * @param payload The string that was used to create the signature. .getBytes() is called internally.
	 * @param sigR The number of the R value of the signature as a string. BigInteger is used internally.
	 * @param sigS The number of the S value of the signature as a string. BigInteger is used internally.
	 * @param publicKey The EC2Key containing the public key, which is used to check validity of the signature.
	 */
	public static void validateSignature(String payload, String sigR, String sigS, ECKey publicKey)
	{
		final ECDSASignature signature = new ECDSASignature(new BigInteger(sigR), new BigInteger(sigS));
		boolean valid = publicKey.verify(Sha256Hash.of(payload.getBytes()), signature);
		if (valid) {
			return;
		} else {
			throw new InvalidSignatureException("Signature is not valid");
		}
	}

}
