package ch.uzh.csg.mbps.server.service;

import java.util.ArrayList;

import ch.uzh.csg.mbps.server.dao.ActivitiesDAO;
import ch.uzh.csg.mbps.server.domain.Activities;

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
	
	private static ActivitiesService activitiesService;
	
	public ActivitiesService(){
	}
	
	//TODO: mehmet test & javadoc
	
	/**
	 * 
	 * @return
	 */
	public static ActivitiesService getInstrance(){
		if(activitiesService == null)
			activitiesService = new ActivitiesService();
		
		return activitiesService;
	}
	
	/**
	 * 
	 * @param username
	 * @param title
	 * @param message
	 */
	public void activityLog (String username, String title, String message){
		Activities activity = new Activities(username, title, message);
		ActivitiesDAO.createActivityLog(activity);
	}
	
	/**
	 * 
	 * @param page
	 * @return
	 */
	public ArrayList<Activities> getLogs(int page){
		return ActivitiesDAO.getLogs(page);
	}
}
