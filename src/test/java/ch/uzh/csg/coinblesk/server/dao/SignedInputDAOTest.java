package ch.uzh.csg.coinblesk.server.dao;

import java.util.Random;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.github.springtestdbunit.annotation.DbUnitConfiguration;


@RunWith(SpringJUnit4ClassRunner.class)
@DbUnitConfiguration(databaseConnection="dataSource")
@ContextConfiguration(locations = { 
        "classpath:context.xml", 
        "classpath:test-context.xml", 
        "classpath:test-database.xml" })
public class SignedInputDAOTest {
    
    private final static Random RND = new Random(42L);
    
    
    public final static long OUTPOINT_NOT_EXISTING = Long.MAX_VALUE;
    
    @Autowired
    private SignedInputDAO signedInputsDao;

    @Test
    public void testSignedInputDao() {
        
        byte[] txHash = new byte[32];
        RND.nextBytes(txHash);
        int outputIndex = 7;
        long locktime = 1337;
        
        signedInputsDao.addSignedInput(txHash, outputIndex, locktime);
        long loadedLocktime = signedInputsDao.getLockTime(txHash, outputIndex);
        
        // should return the correct locktime
        Assert.assertEquals(locktime, loadedLocktime);
        
        
        signedInputsDao.addSignedInput(txHash, outputIndex, 9999L);
        loadedLocktime = signedInputsDao.getLockTime(txHash, outputIndex);
        // should return the same locktime as before
        Assert.assertEquals(locktime, loadedLocktime);
        
        // test inexisting outpuints
        byte[] inexistingHash = new byte[32];
        RND.nextBytes(inexistingHash);
        //inexisting hash
        loadedLocktime = signedInputsDao.getLockTime(inexistingHash, outputIndex);
        Assert.assertEquals(OUTPOINT_NOT_EXISTING, loadedLocktime);
        //inexisting index
        loadedLocktime = signedInputsDao.getLockTime(txHash, 99);
        Assert.assertEquals(OUTPOINT_NOT_EXISTING, loadedLocktime);
        // both inexisting hash and index
        loadedLocktime = signedInputsDao.getLockTime(inexistingHash, 99);
        Assert.assertEquals(OUTPOINT_NOT_EXISTING, loadedLocktime);
        
        // test deleting
        signedInputsDao.removeSignedInput(txHash, outputIndex);
        loadedLocktime = signedInputsDao.getLockTime(txHash, outputIndex);
        Assert.assertEquals(OUTPOINT_NOT_EXISTING, loadedLocktime);
        
        

    }
    
    

}
