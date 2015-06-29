package ch.uzh.csg.coinblesk.server.service;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.params.RegTestParams;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:test-context.xml" })
public class BitcoinWalletServiceTest {


    /**
     * Network parameters. Must be the same as defined in test-context.xml!
     */
    private final static NetworkParameters params = RegTestParams.get();

    @Autowired
    private BitcoinWalletService bitcoinWalletService;

    @Test
    public void testGetSerializedServerWatchingKey() {

        String watchingKey = bitcoinWalletService.getSerializedServerWatchingKey();
        Assert.assertNotNull(watchingKey);
        DeterministicKey key = DeterministicKey.deserializeB58(watchingKey, params);

        Assert.assertNotNull(key);
        Assert.assertTrue(key.isPubKeyOnly());
    }
    


}
