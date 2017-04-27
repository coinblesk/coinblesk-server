package com.coinblesk.server.utils;

import com.coinblesk.dto.SignatureDTO;
import com.coinblesk.dto.SignedDTO;
import com.coinblesk.server.exceptions.InvalidSignatureException;
import com.coinblesk.server.exceptions.MissingFieldException;
import com.google.common.io.BaseEncoding;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.jsonwebtoken.impl.TextCodec;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;

import javax.validation.constraints.NotNull;
import java.lang.reflect.Field;
import java.math.BigInteger;

/***
 * Helper class for everything related to data transfer objects.
 * Includes - Serialization
 *          - Deserializaion
 *          - Validation
 *
 * @author Sebastian Stephan
 */
public class DTOUtils {
	private static final Gson gson = new GsonBuilder().create();

	public static <T> T parseAndValidate(SignedDTO signedDTO, Class<T> typeOfPayload) {
		final String payloadBase64String = signedDTO.getPayload();
		String jsonPayload = fromBase64(payloadBase64String);
		T parsedObject = gson.fromJson(jsonPayload, typeOfPayload);
		validateNonNullFields(parsedObject, typeOfPayload);
		return parsedObject;
	}

	public static <T> SignedDTO serializeAndSign(T innerDTO, ECKey privKey) {
		String base64Payload = toBase64(gson.toJson(innerDTO));
		SignatureDTO signature = sign(base64Payload, privKey);
		return new SignedDTO(base64Payload, signature);
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
		for (Field field : fields) {
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

	public static String fromBase64(String input) {
		return TextCodec.BASE64URL.decodeToString(input);
	}

	public static String toBase64(String input) {
		return TextCodec.BASE64URL.encode(input);
	}

	public static String toBase64(Object o) {
		return TextCodec.BASE64URL.encode(gson.toJson(o));
	}

	public static byte[] fromHex(String input) {
		return BaseEncoding.base16().decode(input.toUpperCase());
	}

	public static String toHex(byte[] input) {
		return BaseEncoding.base16().encode(input);
	}

	public static String toJSON(Object o) {
		return gson.toJson(o);
	}

	public static <T> T fromJSON(String input, Class<T> typeOfT) {
		return gson.fromJson(input, typeOfT);
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
		return ECKey.fromPublicOnly(fromHex(hexFormattedPublicKey));
	}

	/***
	 * Hashes the given string with SHA-256 and checks the given signature against the given public key.
	 *
	 * @param payload The string that was used to create the signature. .getBytes() is called internally.
	 * @param signatureDTO The signature that should be validated.
	 * @param publicKey The EC2Key containing the public key, which is used to check validity of the signature.
	 */
	public static void validateSignature(String payload, SignatureDTO signatureDTO, ECKey publicKey) {
		final BigInteger sigR = new BigInteger(signatureDTO.getSigR());
		final BigInteger sigS = new BigInteger(signatureDTO.getSigS());
		final ECKey.ECDSASignature signature = new ECKey.ECDSASignature(sigR, sigS);
		boolean valid = publicKey.verify(Sha256Hash.of(payload.getBytes()), signature);
		if (!valid) {
			throw new InvalidSignatureException("Signature is not valid");
		}
	}

	/***
	 * Takes a string and returns the signature in form of a {@link SignatureDTO}
	 * The input string is hashed with Sha256.
	 *
	 * @param payload The target payload string to hash and sign.
	 * @param key The key used to sign the payload
	 * @return {@link SignatureDTO} with the Base64Url encoded json of the object and the ECDSA signature
	 */
	public static SignatureDTO sign(String payload, ECKey key) {
		ECKey.ECDSASignature signature = key.sign(Sha256Hash.of(payload.getBytes()));
		return new SignatureDTO(signature.r.toString(), signature.s.toString());
	}
}
