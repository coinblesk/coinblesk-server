package ch.uzh.csg.mpbs.server.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignedObject;

import org.junit.Test;

import ch.uzh.csg.mbps.model.Transaction;
import ch.uzh.csg.mbps.util.KeyHandler;
import ch.uzh.csg.mbps.util.Serializer;


public class KeyHandlerTest {
	
	@Test
	public void testKeyHandling() throws Exception {
		KeyPair keyPair = KeyHandler.generateKeys();
		String privateKeyEncoded = KeyHandler.encodePrivateKey(keyPair.getPrivate());
		String publicKeyEncoded = KeyHandler.encodePublicKey(keyPair.getPublic());
		PrivateKey privateKey = KeyHandler.decodePrivateKey(privateKeyEncoded);
		PublicKey publicKey = KeyHandler.decodePublicKey(publicKeyEncoded);
		
		assertEquals(keyPair.getPrivate(), privateKey);
		assertEquals(keyPair.getPublic(), publicKey);
	}
	
	@Test
	public void testSigning() throws Exception {
		KeyPair keyPair = KeyHandler.generateKeys();
		
		Transaction unsignedTransaction = new Transaction(1, 1, "jeton", "simon", new BigDecimal("0.0001"), "", BigDecimal.ZERO);
		
		SignedObject signedTransaction = KeyHandler.signTransaction(unsignedTransaction, keyPair.getPrivate());
		assertTrue(KeyHandler.verifyObject(signedTransaction, keyPair.getPublic()));
		
		byte[] ba = Serializer.serialize(signedTransaction);
		SignedObject signedTransaction2 = Serializer.deserialize(ba);
		Transaction transaction2 = KeyHandler.retrieveTransaction(signedTransaction2);
		
		assertEquals(unsignedTransaction, transaction2);
		
		String encodedSignedObject = KeyHandler.encodeSignedObject(signedTransaction2);
		SignedObject signedTransaction3 = KeyHandler.decodeSignedObject(encodedSignedObject);
		Transaction transaction3 = KeyHandler.retrieveTransaction(signedTransaction3);
		assertEquals(unsignedTransaction, transaction3);
		
		SignedObject signedTransaction4 = KeyHandler.signTransaction(unsignedTransaction, KeyHandler.encodePrivateKey(keyPair.getPrivate()));
		assertTrue(KeyHandler.verifyObject(signedTransaction, KeyHandler.encodePublicKey(keyPair.getPublic())));
		
		assertEquals(KeyHandler.retrieveTransaction(signedTransaction), KeyHandler.retrieveTransaction(signedTransaction4));
	}
}
