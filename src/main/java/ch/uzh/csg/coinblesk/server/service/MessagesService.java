package ch.uzh.csg.coinblesk.server.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.coinblesk.server.clientinterface.IActivities;
import ch.uzh.csg.coinblesk.server.clientinterface.IMessages;
import ch.uzh.csg.coinblesk.server.dao.MessagesDAO;
import ch.uzh.csg.coinblesk.server.domain.Messages;
import ch.uzh.csg.coinblesk.server.util.AuthenticationInfo;
import ch.uzh.csg.coinblesk.server.util.Subjects;
import ch.uzh.csg.coinblesk.server.util.exceptions.MessageNotFoundException;

/**
 * Service class for {@link Messages}.
 * 
 */
@Service
public class MessagesService implements IMessages{
	private static boolean TESTING_MODE = false;
	
	@Autowired
	MessagesDAO messagesDAO;
	
	@Autowired
	IActivities activitiesService;
	
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
	public boolean createMessage(Messages message){
		if(isTestingMode()){			
			String strDate = "2014-08-31 15:15:15";
			Date date = new Date();
			try {
				date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(strDate);
			} catch (ParseException e) {
				//ignored
			}
			
			message.setCreationDate(date);
		}
				
		boolean success =  messagesDAO.createMessage(message);
		
		if(!isTestingMode()){			
			if(success){
				activitiesService.activityLog(AuthenticationInfo.getPrincipalUsername(), Subjects.SUCCEDED_CREATE_MESSAGE, "Message with subject " + message.getSubject() +" is created");
			} else {
				activitiesService.activityLog(AuthenticationInfo.getPrincipalUsername(), Subjects.FAILED_CREATE_MESSAGE, "Message with subject " + message.getSubject() +" is failed");			
			}
		}
		if(success)
			return true;
		else
			return false;
	}

	@Override
	@Transactional
	public boolean updatedMessagesAnswered(long id) throws MessageNotFoundException{
		Date date = new Date();
		try {
			if(isTestingMode()){			
				String strDate = "2014-08-31 15:15:15";
				try {
					date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(strDate);
				} catch (ParseException e) {
					//ignored
				}	
			}				
			Messages message = messagesDAO.updatedMessagesAnswered(id, true, date);
			
			if (message == null){
				if(!isTestingMode())
					activitiesService.activityLog(AuthenticationInfo.getPrincipalUsername(), Subjects.FAILED_ANSWERED_MESSAGE, "Message with id " + id + " could not be answered");
				return false;
			}
			if(!isTestingMode())
				activitiesService.activityLog(AuthenticationInfo.getPrincipalUsername(), Subjects.SUCCEDED_ANSWERED_MESSAGE, "Message with subject and date (" + message.getSubject() + ", " + message.getCreationDate() +") is answered");			
			return true;
		} catch (MessageNotFoundException e) {
			throw new MessageNotFoundException(id);
		}
	}

	@Override
	@Transactional(readOnly=true)
	public List<Messages> getLast5Messages(){
		return messagesDAO.getLast5Messages();
	}
	
	@Override
	@Transactional(readOnly=true)
	public List<Messages> getNotAnsweredMessages(int page){		
		return messagesDAO.getNotAnsweredMessages(page);
	}
	
	@Override
	@Transactional(readOnly=true)
	public List<Messages> getAnsweredMessages(int page){		
		return messagesDAO.getAnsweredMessages(page);
	}
	
	@Override
	@Transactional(readOnly=true)
	public List<Messages> getMessages(int page){
		return messagesDAO.getMessages(page);
		
	}
	
	@Override
	@Transactional(readOnly=true)
	public long getMessagesCount(){		
		return messagesDAO.getMessagesCount();
	}
	
	@Override
	@Transactional(readOnly=true)
	public long getNotAnsweredMessagesCount(){
		return messagesDAO.getNotAnsweredMessagesCount();
	}
	
	@Override
	@Transactional(readOnly=true)
	public long getAnsweredMessagesCount(){
		return messagesDAO.getAnsweredMessagesCount();		
	}
	
	
	@Override
	@Transactional(readOnly=true)
	public Messages getMessageById(long id) throws MessageNotFoundException{
		return messagesDAO.getMessageById(id);
	}
	
	@Override
	@Transactional(readOnly=true)
	public Messages getMessageByDateAndSubject(Date date, String subject) throws MessageNotFoundException{		
		return messagesDAO.getMessageByDateAndSubject(date, subject);
	}

	@Override
	public boolean exits(String subject, String url) {
		return messagesDAO.exits(subject, url);
	}
}