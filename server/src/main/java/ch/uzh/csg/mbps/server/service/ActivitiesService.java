package ch.uzh.csg.mbps.server.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.mbps.server.clientinterface.IActivities;
import ch.uzh.csg.mbps.server.dao.ActivitiesDAO;
import ch.uzh.csg.mbps.server.domain.Activities;

/**
 * Service class for {@link Activities}.
 * 
 */
@Service
public class ActivitiesService implements IActivities{
	
	@Autowired
	private ActivitiesDAO activitiesDAO;
	
	@Override
	@Transactional
	public void activityLog (String username, String title, String message){
		Activities activity = new Activities(username, title, message);
		activitiesDAO.createActivityLog(activity);
	}
	
	@Override
	@Transactional(readOnly = true)
	public List<Activities> getLogs(int page){
		return activitiesDAO.getLogs(page);
	}
}
