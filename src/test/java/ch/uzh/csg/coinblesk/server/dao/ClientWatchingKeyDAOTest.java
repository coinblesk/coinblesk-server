package ch.uzh.csg.coinblesk.server.dao;

import java.security.SecureRandom;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.test.context.web.WebAppConfiguration;

import com.github.springtestdbunit.DbUnitTestExecutionListener;

import ch.uzh.csg.coinblesk.server.config.BeanConfig;
import ch.uzh.csg.coinblesk.server.service.BitcoinWalletService;


@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class, DirtiesContextTestExecutionListener.class, TransactionalTestExecutionListener.class,
        DbUnitTestExecutionListener.class })
@WebAppConfiguration
@ContextConfiguration(classes={BeanConfig.class})
public class ClientWatchingKeyDAOTest {
    
    private final static NetworkParameters PARAMS = TestNet3Params.get();
    
    @Autowired
    private ClientWatchingKeyDAO clientWatchingKeyDao;
    
    @Autowired
    private BitcoinWalletService bitcoinWalletService;

    @Test
    public void testClientWatchingKeyDao() {
        
        DeterministicKeyChain keyChain = new DeterministicKeyChain(new SecureRandom());
        String watchingKey = keyChain.getWatchingKey().serializePubB58(PARAMS);
        
        bitcoinWalletService.addWatchingKey(watchingKey);
        
        Assert.assertTrue(clientWatchingKeyDao.exists(watchingKey));
        Assert.assertFalse(clientWatchingKeyDao.exists("fake watching key"));

    }
    
    

}
