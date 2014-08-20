package ch.uzh.csg.mbps.server.local;

import java.math.BigDecimal;
import java.util.Calendar;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.mbps.server.dao.UserAccountDAO;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.CustomPasswordEncoder;


@Transactional
public class DummyData implements ApplicationListener<ContextRefreshedEvent>{
	
	private static boolean once = true;
	@Autowired
	private UserAccountDAO userAccountDAO;
	
	@Override
    public void onApplicationEvent(ContextRefreshedEvent arg0) {
		if(once) {
			initData();
			once = false;
		}
    }
	
	private void initData() {
		UserAccount userAccount = new UserAccount();
		userAccount.setBalance(new BigDecimal(111.11d));
		userAccount.setCreationDate(Calendar.getInstance().getTime());
		userAccount.setEmail("tom.mbps1@bocek.ch");
		userAccount.setPassword(CustomPasswordEncoder.getEncodedPassword("wwww"));
		userAccount.setUsername("tom11");
		userAccount.setEmailVerified(true);
		userAccountDAO.createAccount(userAccount,"dummy");
		//
		userAccount = new UserAccount();
		userAccount.setBalance(new BigDecimal(222.22d));
		userAccount.setCreationDate(Calendar.getInstance().getTime());
		userAccount.setEmail("tom.mbps2@bocek.ch");
		userAccount.setPassword(CustomPasswordEncoder.getEncodedPassword("wwww"));
		userAccount.setUsername("tom22");
		userAccount.setEmailVerified(true);
		userAccountDAO.createAccount(userAccount,"dummy");
	}
}
