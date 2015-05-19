package ch.uzh.csg.coinblesk.server.local;

import java.math.BigDecimal;
import java.util.Calendar;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.coinblesk.server.dao.UserAccountDAO;
import ch.uzh.csg.coinblesk.server.domain.UserAccount;
import ch.uzh.csg.coinblesk.server.util.CustomPasswordEncoder;

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
		userAccount.setEmail("tom.pfischer1@bocek.ch");
		userAccount.setPassword(CustomPasswordEncoder.getEncodedPassword("wwww"));
		userAccount.setUsername("palomafischer");
		userAccount.setEmailVerified(true);
		userAccountDAO.createAccount(userAccount,"dummy-token1");
		//
		userAccount = new UserAccount();
		userAccount.setBalance(new BigDecimal(222.22d));
		userAccount.setCreationDate(Calendar.getInstance().getTime());
		userAccount.setEmail("tom.pfischer2@bocek.ch");
		userAccount.setPassword(CustomPasswordEncoder.getEncodedPassword("wwww"));
		userAccount.setUsername("palomafischer2");
		userAccount.setEmailVerified(true);
		userAccountDAO.createAccount(userAccount,"dummy-token2");
	}
}
