package ch.uzh.csg.mbps.server.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.mbps.server.clientinterface.IActivities;
import ch.uzh.csg.mbps.server.clientinterface.IServerAccountTasks;
import ch.uzh.csg.mbps.server.clientinterface.IUserAccount;
import ch.uzh.csg.mbps.server.dao.ServerAccountTasksDAO;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.ServerAccountTasks;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.ServerAccountTasksHandler;
import ch.uzh.csg.mbps.server.util.Subjects;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountTasksAlreadyExists;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

/**
 * Service class for {@link ServerAccountTasks}.
 * 
 */
@Service
public class ServerAccountTasksService implements IServerAccountTasks{

	@Autowired
	private ServerAccountTasksDAO serverAccountTasksDAO;
	@Autowired
	private IUserAccount userAccountService;
	@Autowired
	private IActivities activitiesService;
	
	public enum ServerAccountTaskTypes {
		CREATE_ACCOUNT((int) 1),
		UPDATE_ACCOUNT((int) 2),
		DELETE_ACCOUNT((int) 3);
		
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
		else if (code == ServerAccountTaskTypes.DELETE_ACCOUNT.getCode())
			return true;
		else
			return false;
	}
	
	@Override
	@Transactional
	public void persistsCreateNewAccount(ServerAccount account, String username, String email){
		ServerAccountTasks task = new ServerAccountTasks();
		task.setType(ServerAccountTaskTypes.CREATE_ACCOUNT.getCode());
		task.setUrl(account.getUrl());
		task.setUsername(username);
		task.setEmail(email);
		task.setToken(java.util.UUID.randomUUID().toString());
		serverAccountTasksDAO.persistCreateNewAccount(task);
	}
	
	@Override
	@Transactional(readOnly = true)
	public ServerAccountTasks getAccountTasksByUrl(String url){
		return serverAccountTasksDAO.getAccountTasksByUrl(url);
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
	public void processNewAccountTask(){
		List<ServerAccountTasks> createNewAccount = serverAccountTasksDAO.getAllAccountTasksBySubject(ServerAccountTaskTypes.CREATE_ACCOUNT.getCode());
		
		for(ServerAccountTasks task: createNewAccount){
			UserAccount user;
			try {
				user = userAccountService.getByUsername(task.getUsername());
			} catch (UserAccountNotFoundException e) {
				user = new UserAccount(Config.NOT_AVAILABLE, Config.NOT_AVAILABLE, null);
			}
			try {
				ServerAccountTasksHandler.getInstance().createNewAccount(task.getUrl(),task.getEmail(), user);
			} catch (Exception e) {
				activitiesService.activityLog(task.getUsername(), Subjects.FAILED_HOURLY_TASK, "Process hourly task to create new Account failed.");
			}
		}
	}
}
