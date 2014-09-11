package ch.uzh.csg.mbps.server.service;

import java.util.Date;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.mbps.server.clientinterface.IActivities;
import ch.uzh.csg.mbps.server.clientinterface.IMessages;
import ch.uzh.csg.mbps.server.dao.MessagesDAO;
import ch.uzh.csg.mbps.server.domain.Messages;
import ch.uzh.csg.mbps.server.util.AuthenticationInfo;
import ch.uzh.csg.mbps.server.util.Subjects;
import ch.uzh.csg.mbps.server.util.exceptions.MessageNotFoundException;

/**
 * Service class for {@link Messages}.
 * 
 */
@Service
public class MessagesService implements IMessages{

	@Autowired
	MessagesDAO messagesDAO;
	
	@Autowired
	IActivities activitiesService;
	
	@Override
	@Transactional
	public boolean createMessage(Messages message) throws MessageNotFoundException{
		boolean success =  messagesDAO.createMessage(message);
		if(success){
			activitiesService.activityLog(AuthenticationInfo.getPrincipalUsername(), Subjects.SUCCEDED_CREATE_MESSAGE, "Message with subject " + message.getSubject() +" is created");
		} else {
			activitiesService.activityLog(AuthenticationInfo.getPrincipalUsername(), Subjects.FAILED_CREATE_MESSAGE, "Message with subject " + message.getSubject() +" is failed");			
		}
		
		return success;
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
	@Transactional
	public boolean updatedMessagesAnswered(long id) throws MessageNotFoundException{
		try {
			Messages message = messagesDAO.updatedMessagesAnswered(id);
			if (message == null){				
				activitiesService.activityLog(AuthenticationInfo.getPrincipalUsername(), Subjects.FAILED_ANSWERED_MESSAGE, "Message with id " + id + " could not be answered");
				return false;
			}
			
			activitiesService.activityLog(AuthenticationInfo.getPrincipalUsername(), Subjects.SUCCEDED_ANSWERED_MESSAGE, "Message with subject and date (" + message.getSubject() + ", " + message.getCreationDate() +") is answered");			
			return true;
		} catch (MessageNotFoundException e) {
			throw new MessageNotFoundException(id);
		}
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
}