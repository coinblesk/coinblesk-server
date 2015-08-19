package ch.uzh.csg.coinblesk.server.dao;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.testing.FakeTxBuilder;
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

import ch.uzh.csg.coinblesk.server.config.DispatcherConfig;

@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class, DirtiesContextTestExecutionListener.class, TransactionalTestExecutionListener.class,
        DbUnitTestExecutionListener.class })
@WebAppConfiguration
@ContextConfiguration(classes={DispatcherConfig.class})
public class SignedTransactionDAOTest {
    
    private final static NetworkParameters PARAMS = TestNet3Params.get();
    
    @Autowired
    private SignedTransactionDAO signedTransactionDao;

    @Test
    public void testAllInputsServerSigned() throws Exception {
        Transaction[] txs = FakeTxBuilder.createFakeTx(PARAMS, Coin.COIN, new Address(PARAMS, "2NFCKjVtpzN65DW6d6zWy5HfejV8BGKw31x"), new Address(PARAMS, "mtqsG5SWFmrhfpW1MVH7V63Dwe3XWCwxpf"));
        Assert.assertFalse(signedTransactionDao.allInputsServerSigned(txs[1]));
        signedTransactionDao.addSignedTransaction(txs[0]);
        Assert.assertTrue(signedTransactionDao.allInputsServerSigned(txs[1]));
        System.out.println(txs[0]);
        System.out.println(txs[1]);
    }
    
    @Test
    public void testIsInstantTransaction() throws Exception {
        Transaction[] txs = FakeTxBuilder.createFakeTx(PARAMS, Coin.COIN, new Address(PARAMS, "2NFCKjVtpzN65DW6d6zWy5HfejV8BGKw31x"), new Address(PARAMS, "mtqsG5SWFmrhfpW1MVH7V63Dwe3XWCwxpf"));
        Assert.assertFalse(signedTransactionDao.isInstantTransaction(txs[1].getHashAsString()));
        signedTransactionDao.addSignedTransaction(txs[1]);
        Assert.assertTrue(signedTransactionDao.isInstantTransaction(txs[1].getHashAsString()));

    }

    

}
