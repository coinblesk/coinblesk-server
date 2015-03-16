package ch.uzh.csg.coinblesk.server.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.coinblesk.server.clientinterface.IActivities;
import ch.uzh.csg.coinblesk.server.dao.ActivitiesDAO;
import ch.uzh.csg.coinblesk.server.domain.Activities;

/**
 * Service class for {@link Activities}.
 * 
 */
@Service
public class ActivitiesService implements IActivities{
	private static boolean TESTING_MODE = false;
	
	@Autowired
	private ActivitiesDAO activitiesDAO;
	
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
	
	@Override
	@Transactional
	public void activityLog (String username, String subject, String message){
		Activities activity = new Activities(username, subject, message);
		if(isTestingMode()){			
			String strDate = "2014-08-31 15:15:15.0";
			Date date;
			try {
				date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(strDate);
				activity.setCreationDate(date);
			} catch (ParseException e) {
				activity.setCreationDate(new Date());				
			}
		}
		activitiesDAO.createActivityLog(activity);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<Activities> getLogs(int page){
		return activitiesDAO.getLogs(page);
	}
}