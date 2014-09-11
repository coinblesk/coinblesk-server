package ch.uzh.csg.mbps.server.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.mbps.server.clientinterface.IServerAccountTasks;
import ch.uzh.csg.mbps.server.dao.ServerAccountTasksDAO;
import ch.uzh.csg.mbps.server.domain.ServerAccount;
import ch.uzh.csg.mbps.server.domain.ServerAccountTasks;
import ch.uzh.csg.mbps.server.util.exceptions.ServerAccountTasksAlreadyExists;

/**
 * Service class for {@link ServerAccountTasks}.
 * 
 */
@Service
public class ServerAccountTasksService implements IServerAccountTasks{

	@Autowired
	private ServerAccountTasksDAO serverAccountTasksDAO;
	
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
}
