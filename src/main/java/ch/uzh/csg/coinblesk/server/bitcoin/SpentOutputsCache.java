package ch.uzh.csg.coinblesk.server.bitcoin;

import java.util.concurrent.TimeUnit;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

/**
 * This class keeps track of spent outputs of the clients (i.e. outputs that
 * have been signed by the server). In general, an output can only be signed
 * once by the server. But after a certain amount of time has passed, we can
 * savely sign an output again, because there are only 2 possibilities in this
 * case:
 * <ul>
 * <li>The output has already been spent, therefore any transaction after that
 * will be rejected by the network</li>
 * <li>The transaction failed the first time for some reason</li>
 * </ul>
 * 
 * @author rvoellmy
 *
 */
public class SpentOutputsCache {

    /**
     * Dummy class to use for the values of the cache. We cannot use
     * {@link Void} because to see whether a key has been stored, we need to
     * check if the value returned is null.
     * 
     * @author rvoellmy
     */
    public static class NonVoid {
        private static NonVoid INSTANCE = new NonVoid();

        private NonVoid() {
        }
    }

    private Cache<TransactionOutPoint, NonVoid> cache;

    public SpentOutputsCache() {
        this.cache = CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.DAYS).build();
    }

    /**
     * Takes a transaction that has been or will be signed by the server and
     * stores the inputs of this transaction in the cache. Transactions using
     * any of the outputs that have already been used will not be signed.
     * 
     * @param tx
     */
    public void cacheOutputs(Transaction tx) {

        for (TransactionInput txIn : tx.getInputs()) {
            cache.put(txIn.getOutpoint(), NonVoid.INSTANCE);
        }
    }

    /**
     * Checks the inputs of a transactions for attempted double spends
     * 
     * @param tx
     *            The transaction to check for double spends
     * @return true if the transaction passed is an attempted double spend
     */
    public boolean isDoubleSpend(Transaction tx) {
        for (TransactionInput txIn : tx.getInputs()) {
            if (null != cache.getIfPresent(txIn.getOutpoint())) {
                return true;
            }
        }
        return false;
    }
}
