package ch.uzh.csg.mbps.server.clientinterface;

import java.util.Date;
import java.util.List;

import ch.uzh.csg.mbps.server.domain.Messages;
import ch.uzh.csg.mbps.server.util.exceptions.MessageNotFoundException;

public interface IMessages {

	/**
	 * Creates a {@link Messages}.
	 * 
	 * @param message
	 * @return boolean
	 * @throws MessageNotFoundException
	 */
	public boolean createMessage(Messages message);

	/**
	 * 
	 * @return List of Message
	 */
	public List<Messages> getLast5Messages();

	/**
	 * 
	 * @param page
	 * @return List of Messages
	 */
	public List<Messages> getNotAnsweredMessages(int page);

	/**
	 * 
	 * @param page
	 * @return List of Messages
	 */
	public List<Messages> getAnsweredMessages(int page);

	/**
	 * 
	 * @param page
	 * @return List of Messages
	 */
	public List<Messages> getMessages(int page);

	/**
	 * 
	 * @return number Messages
	 */
	public long getMessagesCount();

	/**
	 * 
	 * @return number Messages
	 */
	public long getNotAnsweredMessagesCount();

	/**
	 * 
	 * @return number Messages
	 */
	public long getAnsweredMessagesCount();

	/**
	 * Updates the {@link Messages} from not answered to answered.
	 * 
	 * @param id
	 * @return Boolean
	 * @throws MessageNotFoundException 
	 */
	public boolean updatedMessagesAnswered(long id) throws MessageNotFoundException;

	/**
	 * 
	 * @param id
	 * @return Messages
	 * @throws MessageNotFoundException
	 */
	public Messages getMessageById(long id) throws MessageNotFoundException;

	/**
	 * 
	 * @param id
	 * @return Messages
	 * @throws MessageNotFoundException
	 */
	public Messages getMessageByDateAndSubject(Date date, String subject) throws MessageNotFoundException;

	/**
	 * Checks if already a message exits by the given parameter subject and url
	 * 
	 * @param subject
	 * @param url
	 * @return boolean
	 */
	public boolean exits(String subject, String url);
}