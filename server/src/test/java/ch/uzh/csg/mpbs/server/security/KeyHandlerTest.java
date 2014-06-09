package ch.uzh.csg.mpbs.server.security;

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

import ch.uzh.csg.mbps.customserialization.PKIAlgorithm;
import ch.uzh.csg.mbps.customserialization.UnknownPKIAlgorithmException;
import ch.uzh.csg.mbps.server.security.KeyHandler;

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
		KeyPair keyPair = KeyHandler.generateKeyPair();
		assertNotNull(keyPair);
		keyPair = KeyHandler.generateKeyPair(PKIAlgorithm.DEFAULT);
		assertNotNull(keyPair);
	}
	
	static {
		Security.addProvider(new BouncyCastleProvider());
	}
	
	@Test
	public void testEncodeDecode() throws Exception {
		KeyPair keyPair = KeyHandler.generateKeyPair();
		String encodePublicKey = KeyHandler.encodePublicKey(keyPair.getPublic());
		PublicKey decodePublicKey = KeyHandler.decodePublicKey(encodePublicKey);
		String encodePublicKey2 = KeyHandler.encodePublicKey(decodePublicKey);
		
		assertTrue(Arrays.equals(keyPair.getPublic().getEncoded(), decodePublicKey.getEncoded()));
		assertEquals(encodePublicKey, encodePublicKey2);
		
		String encodePrivateKey = KeyHandler.encodePrivateKey(keyPair.getPrivate());
		PrivateKey decodePrivateKey = KeyHandler.decodePrivateKey(encodePrivateKey);
		String encodePrivateKey2 = KeyHandler.encodePrivateKey(decodePrivateKey);
		
		assertTrue(Arrays.equals(keyPair.getPrivate().getEncoded(), decodePrivateKey.getEncoded()));
		assertEquals(encodePrivateKey, encodePrivateKey2);
	}
	
}
