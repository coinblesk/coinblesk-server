package com.coinblesk.server.utils;

import com.coinblesk.server.dto.SignatureDTO;
import com.coinblesk.server.exceptions.InvalidSignatureException;
import com.google.common.io.BaseEncoding;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.ECKey.ECDSASignature;
import org.bitcoinj.core.Sha256Hash;

import java.math.BigInteger;

/**
 * Helper class for everything related to Signatures and ECKeys
 *
 * @author Sebastian Stephan
 *
 */
public class SignatureUtils {

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
	public static SignatureDTO sign(String payload, ECKey key)
	{
		ECDSASignature signature = key.sign(Sha256Hash.of(payload.getBytes()));
		return new SignatureDTO(signature.r.toString(), signature.s.toString());
	}

}
