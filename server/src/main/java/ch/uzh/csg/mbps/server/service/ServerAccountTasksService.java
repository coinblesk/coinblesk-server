package ch.uzh.csg.mbps.server.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.mbps.server.clientinterface.IActivities;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccountTasks;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.dao.ServerAccountTasksDAO;
import ch.uzh.csg.mbps.server.domain.ServerAccountTasks;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountTasksAlreadyExists;

/**
 * Service class for {@link ServerAccountTasks}.
 * 
 */
@Service
public class ServerAccountTasksService implements IServerAccountTasks{
	private static boolean TESTING_MODE = false;

	@Autowired
	private ServerAccountTasksDAO serverAccountTasksDAO;
	@Autowired
	private IUserAccount userAccountService;
	@Autowired
	private IActivities activitiesService;

	/**
	 * Enables testing mode for JUnit Tests.
	 */
	public static void enableTestingMode() {
		TESTING_MODE = true;
	}
	
	public static boolean isTestingMode(){
		return TESTING_MODE;
	}

	/**
	 * Disables testing mode for JUnit Tests.
	 */
	public static void disableTestingMode() {
		TESTING_MODE = false;
	}
	
	public enum ServerAccountTaskTypes {
		CREATE_ACCOUNT((int) 1),
		UPDATE_ACCOUNT((int) 2);
		
		private int code;
		
		private ServerAccountTaskTypes(int code) {
			this.code = code;
		}
		
		public int getCode() {
			return code;
		}
	}
	
	public static boolean isValidServerAccountTaskType(int code) {
		if (code == ServerAccountTaskTypes.CREATE_ACCOUNT.getCode())
			return true;
		else if (code == ServerAccountTaskTypes.UPDATE_ACCOUNT.getCode())
			return true;
		else
			return false;
	}
	
	@Override
	@Transactional
	public void persistsCreateNewAccount(String url, String username, String email){
		ServerAccountTasks task = new ServerAccountTasks();
		task.setType(ServerAccountTaskTypes.CREATE_ACCOUNT.getCode());
		task.setUrl(url);
		task.setUsername(username);
		task.setEmail(email);
		task.setToken(java.util.UUID.randomUUID().toString());
		
		if(isTestingMode()){			
			String strDate = "2014-08-31 15:15:15.0";
			Date date = new Date();
			try {
				date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(strDate);
			} catch (ParseException e) {
				//ignore
			}
			task.setTimestamp(date);
			task.setToken("123456");
		}
		
		serverAccountTasksDAO.persistCreateNewAccount(task);
	}

	@Override
	@Transactional
	public void persistsCreateNewAccountPayOutAddress(String url, String username, String email, String payoutAddress){
		ServerAccountTasks task = new ServerAccountTasks();
		task.setType(ServerAccountTaskTypes.CREATE_ACCOUNT.getCode());
		task.setUrl(url);
		task.setUsername(username);
		task.setEmail(email);
		task.setPayoutAddress(payoutAddress);
		task.setToken(java.util.UUID.randomUUID().toString());
		
		if(isTestingMode()){			
			String strDate = "2014-08-31 15:15:15.0";
			Date date = new Date();
			try {
				date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(strDate);
			} catch (ParseException e) {
				//ignore
			}
			task.setTimestamp(date);
			task.setToken("123457");
		}
		
		serverAccountTasksDAO.persistCreateNewAccount(task);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ServerAccountTasks getAccountTasksCreateByUrl(String url){
		return serverAccountTasksDAO.getAccountTasksCreateByUrl(url);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ServerAccountTasks getAccountTasksByToken(String token){
		return serverAccountTasksDAO.getAccountTasksByToken(token);
	}

	@Override
	@Transactional
	public void deleteCreateNewAccount(String url) {
		serverAccountTasksDAO.delete(ServerAccountTaskTypes.CREATE_ACCOUNT.getCode(),url);
	}
	
	@Override
	@Transactional(readOnly=true)
	public boolean checkIfExists(String url){
		try{			
			serverAccountTasksDAO.checkIfExists(url);
			return true;
		} catch (ServerAccountTasksAlreadyExists e) {
			return false;
		}
	}
	
	@Override
	@Transactional
	public List<ServerAccountTasks> processNewAccountTask(int type){
		return serverAccountTasksDAO.getAllAccountTasksBySubject(type);
	}
	
	@Override
	@Transactional
	public List<ServerAccountTasks> getProceedAccounts(){
		return serverAccountTasksDAO.getProceedAccounts();
	}
	
	@Override
	@Transactional
	public void updateProceed(String token){
		ServerAccountTasks task = serverAccountTasksDAO.getAccountTasksByToken(token);
		serverAccountTasksDAO.updatedProceed(task);
	}
}
