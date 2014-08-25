package ch.uzh.csg.mbps.server.security;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECParameterSpec;

import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.customserialization.exceptions.UnknownPKIAlgorithmException;

/**
 * This class handles the PKI key pair generation, encoding into and decoding
 * from base64.
 * 
 * @author Jeton Memeti
 * 
 */
public class KeyHandler {
	
	private static final String SECURITY_PROVIDER = "BC";
	
	/*
	 * The BouncyCastle security provider is added statically, to avoid errors
	 * when redeploying the war to tomcat. (see
	 * http://www.bouncycastle.org/wiki/display/JA1/Provider+Installation)
	 * 
	 * In order to work, edit java.security and copy the bouncycastle jars to
	 * /usr/lib/jvm/java-1.7.0-openjdk-amd64/jre/lib/ext/
	 */
	
	static {
		Security.addProvider(new BouncyCastleProvider());
	}
	
	/**
	 * Generates a new PKI key pair using the default {@link PKIAlgorithm}.
	 * 
	 * @throws UnknownPKIAlgorithmException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 * @throws InvalidAlgorithmParameterException
	 */
	public static KeyPair generateKeyPair() throws UnknownPKIAlgorithmException, NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
		return generateKeyPair(PKIAlgorithm.DEFAULT);
	}
	
	/**
	 * Generates a new PKI key pair using the {@link PKIAlgorithm} provided.
	 * 
	 * @param algorithm
	 *            the {@link PKIAlgorithm} to be used for key generation
	 * @throws UnknownPKIAlgorithmException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 * @throws InvalidAlgorithmParameterException
	 */
	public static KeyPair generateKeyPair(PKIAlgorithm algorithm) throws UnknownPKIAlgorithmException, NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
		if (algorithm.getCode() != PKIAlgorithm.DEFAULT.getCode())
			throw new UnknownPKIAlgorithmException();
		
		ECParameterSpec ecSpec = ECNamedCurveTable.getParameterSpec(algorithm.getKeyPairSpecification());
		KeyPairGenerator keyGen = KeyPairGenerator.getInstance(algorithm.getKeyPairAlgorithm(), SECURITY_PROVIDER);
		keyGen.initialize(ecSpec, new SecureRandom());
		return keyGen.generateKeyPair();
	}
	
	/**
	 * Encodes a private key using Base64 encoding.
	 * 
	 * @param privateKey
	 *            the private key to be encoded
	 * @return the encoded private key as string
	 */
	public static String encodePrivateKey(PrivateKey privateKey) {
		byte[] privateEncoded = Base64.encodeBase64(privateKey.getEncoded());
		return new String(privateEncoded);
	}
	
	/**
	 * Encodes a public key using Base64 encoding.
	 * 
	 * @param publicKey
	 *            the public key to be encoded
	 * @return the encoded public key as string
	 */
	public static String encodePublicKey(PublicKey publicKey) {
		byte[] publicEncoded = Base64.encodeBase64(publicKey.getEncoded());
		return new String(publicEncoded);
	}
	
	/**
	 * Decodes a string to a public key using Base64 encoding using the default
	 * {@link PKIAlgorithm}.
	 * 
	 * @param publicKeyEncoded
	 *            the encoded public key
	 * 
	 * @throws UnknownPKIAlgorithmException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 * @throws InvalidKeySpecException
	 */
	public static PublicKey decodePublicKey(String publicKeyEncoded) throws UnknownPKIAlgorithmException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException {
		return decodePublicKey(publicKeyEncoded, PKIAlgorithm.DEFAULT);
	}

	/**
	 * Decodes a string to a public key using Base64 encoding using the
	 * {@link PKIAlgorithm} provided.
	 * 
	 * @param publicKeyEncoded
	 *            the encoded public key
	 * @param algorithm
	 *            the {@link PKIAlgorithm} to be used for decoding
	 * 
	 * @throws UnknownPKIAlgorithmException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 * @throws InvalidKeySpecException
	 */
	public static PublicKey decodePublicKey(String publicKeyEncoded, PKIAlgorithm algorithm) throws UnknownPKIAlgorithmException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException {
		if (algorithm.getCode() != PKIAlgorithm.DEFAULT.getCode())
			throw new UnknownPKIAlgorithmException();
		
		byte[] decoded = Base64.decodeBase64(publicKeyEncoded.getBytes());
		EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(decoded);
		
		KeyFactory keyFactory = KeyFactory.getInstance(algorithm.getKeyPairAlgorithm(), SECURITY_PROVIDER);
		return keyFactory.generatePublic(publicKeySpec);
	}
	
	/**
	 * Decodes a string to a private key using Base64 encoding and the default
	 * {@link PKIAlgorithm}.
	 * 
	 * @param privateKeyEncoded
	 *            the encoded private key
	 * 
	 * @throws UnknownPKIAlgorithmException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 * @throws InvalidKeySpecException
	 */
	public static PrivateKey decodePrivateKey(String privateKeyEncoded) throws UnknownPKIAlgorithmException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException {
		return decodePrivateKey(privateKeyEncoded, PKIAlgorithm.DEFAULT);
	}
	
	/**
	 * Decodes a string to a private key using Base64 encoding and the
	 * {@link PKIAlgorithm} provided.
	 * 
	 * @param privateKeyEncoded
	 *            the encoded private key
	 * @param algorithm
	 *            the {@link PKIAlgorithm} to be used for decoding
	 * 
	 * @throws UnknownPKIAlgorithmException
	 * @throws NoSuchAlgorithmException
	 * @throws NoSuchProviderException
	 * @throws InvalidKeySpecException
	 */
	public static PrivateKey decodePrivateKey(String privateKeyEncoded, PKIAlgorithm algorithm) throws UnknownPKIAlgorithmException, NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException {
		if (algorithm.getCode() != PKIAlgorithm.DEFAULT.getCode())
			throw new UnknownPKIAlgorithmException();
		
		byte[] decoded = Base64.decodeBase64(privateKeyEncoded.getBytes());
		EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(decoded);
		
		KeyFactory keyFactory = KeyFactory.getInstance(algorithm.getKeyPairAlgorithm(), SECURITY_PROVIDER);
		return keyFactory.generatePrivate(privateKeySpec);
	}

}
