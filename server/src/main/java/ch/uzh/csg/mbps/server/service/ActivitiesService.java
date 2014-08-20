package ch.uzh.csg.mbps.server.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.mbps.server.dao.ActivitiesDAO;
import ch.uzh.csg.mbps.server.domain.Activities;

@Service
public class ActivitiesService {

	public static final String IVITE_ADMIN = "Invitation for administration rights";
	public static final String UPDATE_EMAIL = "Email is update";
	public static final String UPDATE_PASSWORD = "New password is set";
	public static final String DELETE_ACCOUNT = "Account is deleted";
	public static final String INVITE_SERVER_ACCOUNT = "Invite server account";
	public static final String CREATE_SERVER_ACCOUNT = "Server account is created";
	public static final String DECLINE_SERVER_ACCOUNT = "Server account declined request";
	public static final String UPDATE_BALANCE_LIMIT = "Balance limit is updated";
	public static final String REMOVE_SERVER_ACCOUNT = "Server account relation is cancelled";
	public static final String UPGRADE_TRUST_LEVEL = "Updgrade trust level";
	public static final String ACCEPT_UPGRADE_TRUST_LEVEL = "Upgrade accepted";
	public static final String DECLINE_UPGRADE_TRUST_LEVEL = "Upgrade declined";
	public static final String DOWNGRADE_TRUST_LEVEL = "Trust level downgraded";
	public static final String CREATE_PAYOUT_RULES = "Created payout rule for bitcoins";
	
	@Autowired
	private ActivitiesDAO activitiesDAO;
	
	//TODO: mehmet test & javadoc
	
	/**
	 * 
	 * @param username
	 * @param title
	 * @param message
	 */
	@Transactional
	public void activityLog (String username, String title, String message){
		Activities activity = new Activities(username, title, message);
		activitiesDAO.createActivityLog(activity);
	}
	
	/**
	 * 
	 * @param page
	 * @return
	 */
	@Transactional(readOnly = true)
	public List<Activities> getLogs(int page){
		return activitiesDAO.getLogs(page);
	}
}
