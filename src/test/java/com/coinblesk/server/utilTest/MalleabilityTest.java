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
	@Test
	public void attack() {
		byte[] message = {0x01, 0x02, 0x03, 0x04};
		Sha256Hash hash = Sha256Hash.of(message);

		ECKey key = new ECKey();
		ECDSASignature sig = key.sign(hash);

		// Is valid
		key.verify(hash, sig);

		// Change signature
		BigInteger sPrime;
		if (sig.s.compareTo(ECKey.HALF_CURVE_ORDER) <= 0) {
			// If below half order set s' = -s mod N
			sPrime = sig.s.multiply(BigInteger.valueOf(-1)).mod(CURVE.getN());
		} else {
			// set s' = N - s
			sPrime = CURVE.getN().subtract(sig.s);
		}
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
