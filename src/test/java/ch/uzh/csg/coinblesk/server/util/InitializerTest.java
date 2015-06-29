package ch.uzh.csg.coinblesk.server.util;

import java.io.File;
import java.net.URISyntaxException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class InitializerTest {
    
    private Initializer initializer;
    
    @Before
    public void setUp() {
        initializer = new Initializer();
    }
    
    @After
    public void tearDown() throws URISyntaxException {
        new File(getClass().getResource("/" + Initializer.KEY_FILE_NAME).toURI());
    }

    @Test
    public void testInitializer() {
        initializer.init();
        Assert.assertNotNull(Constants.SERVER_KEY_PAIR);
        initializer.init();
        Assert.assertNotNull(Constants.SERVER_KEY_PAIR);
    }

}
