/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.service;

import ch.uzh.csg.coinblesk.server.config.BeanConfig;
import ch.uzh.csg.coinblesk.server.entity.UserAccount;
import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.ExpectedDatabase;
import com.github.springtestdbunit.assertion.DatabaseAssertionMode;
import java.math.BigDecimal;
import java.util.Date;
import javax.transaction.Transactional;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;
import org.springframework.test.context.web.WebAppConfiguration;

/**
 *
 * @author Thomas Bocek
 */
@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners({DependencyInjectionTestExecutionListener.class, TransactionalTestExecutionListener.class,
    DbUnitTestExecutionListener.class})
@WebAppConfiguration
@ContextConfiguration(classes = {BeanConfig.class})
public class UserTest {

    @Autowired
    UserAccountService userAccountService;

    @Test
    @ExpectedDatabase(value = "classpath:DbUnitFiles/addedUser.xml", assertionMode = DatabaseAssertionMode.NON_STRICT_UNORDERED)
    public void testAddUser() throws Exception {
        UserAccount userAccount = new UserAccount();
        userAccount.setBalance(BigDecimal.ONE)
                .setCreationDate(new Date(1))
                .setDeleted(false)
                .setEmail("test@test.test")
                .setEmailToken(null)
                .setPassword("blub")
                .setUsername("blib")
                .setVersion((byte)2);
        userAccountService.save(userAccount);
    }
    
    @Test
    @DatabaseSetup("classpath:DbUnitFiles/addedUser.xml")
    public void testGetUser() throws Exception {
        UserAccount u1 = userAccountService.getByUsername("test");
        Assert.assertNull(u1);
        UserAccount u2 = userAccountService.getByUsername("blib");
        Assert.assertEquals("blub", u2.getPassword());
    }
}
