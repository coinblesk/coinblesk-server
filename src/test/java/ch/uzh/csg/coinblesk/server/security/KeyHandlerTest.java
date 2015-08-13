package ch.uzh.csg.coinblesk.server.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.util.Arrays;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ch.uzh.csg.coinblesk.customserialization.PKIAlgorithm;
import ch.uzh.csg.coinblesk.customserialization.exceptions.UnknownPKIAlgorithmException;
import ch.uzh.csg.coinblesk.server.service.KeyService;

public class KeyHandlerTest {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testGenerateKeys() throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException, UnknownPKIAlgorithmException {
		KeyPair keyPair = KeyService.generateKeyPair();
		assertNotNull(keyPair);
		keyPair = KeyService.generateKeyPair(PKIAlgorithm.DEFAULT);
		assertNotNull(keyPair);
	}
	
	static {
		Security.addProvider(new BouncyCastleProvider());
	}
	
	@Test
	public void testEncodeDecode() throws Exception {
		KeyPair keyPair = KeyService.generateKeyPair();
		String encodePublicKey = KeyService.encodePublicKey(keyPair.getPublic());
		PublicKey decodePublicKey = KeyService.decodePublicKey(encodePublicKey);
		String encodePublicKey2 = KeyService.encodePublicKey(decodePublicKey);
		
		assertTrue(Arrays.equals(keyPair.getPublic().getEncoded(), decodePublicKey.getEncoded()));
		assertEquals(encodePublicKey, encodePublicKey2);
		
		String encodePrivateKey = KeyService.encodePrivateKey(keyPair.getPrivate());
		PrivateKey decodePrivateKey = KeyService.decodePrivateKey(encodePrivateKey);
		String encodePrivateKey2 = KeyService.encodePrivateKey(decodePrivateKey);
		
		assertTrue(Arrays.equals(keyPair.getPrivate().getEncoded(), decodePrivateKey.getEncoded()));
		assertEquals(encodePrivateKey, encodePrivateKey2);
	}
	
}
