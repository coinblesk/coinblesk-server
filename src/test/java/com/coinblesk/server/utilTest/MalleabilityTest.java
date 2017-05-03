package com.coinblesk.server.utilTest;

import com.coinblesk.util.DTOUtils;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.junit.Test;
import java.math.BigInteger;

import static org.bitcoinj.core.ECKey.*;

/**
 * @author
 */
public class MalleabilityTest {
	/***
	 * This test showcases that changing the S value of a signature does not invalidate it.
	 * Even though nodes do not relay high-S value signatures, they accept blocks containing
	 * them, as some old wallets used to produce them.
	 *
	 * There were cases where miners changed the signatures, causing chains of transaction
	 * to be invalidated.
	 */
	@Test
	public void attack() {
		System.out.println(ECKey.CURVE.getN());
		System.out.println(ECKey.HALF_CURVE_ORDER);

		byte[] message = {0x01, 0x02, 0x03, 0x04};
		Sha256Hash hash = Sha256Hash.of(message);

		ECKey key = new ECKey();
		ECDSASignature sig = key.sign(hash);

		// Is valid
		key.verify(hash, sig);

		// Change signature
		BigInteger sPrime = sig.s.multiply(BigInteger.valueOf(-1)).mod(CURVE.getN());
		ECDSASignature sig2 = new ECDSASignature(sig.r, sPrime);

		// Still valid
		key.verify(hash, sig2);

		// Show that hashes are different
		String hash1 = DTOUtils.toHex(Sha256Hash.of(sig.encodeToDER()).getBytes());
		String hash2 = DTOUtils.toHex(Sha256Hash.of(sig2.encodeToDER()).getBytes());
		assert (!hash1.equals(hash2));
		System.out.println(hash1);
		System.out.println(hash2);
	}
}
