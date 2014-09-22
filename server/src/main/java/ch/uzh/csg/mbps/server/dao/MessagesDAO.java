package ch.uzh.csg.mbps.server.dao;

import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Repository;

import ch.uzh.csg.mbps.server.domain.Messages;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.util.exceptions.MessageNotFoundException;


/**
 * DatabaseAccessObject for {@link Messages}. Handles all DB operations
 * regarding {@link Messages}.
 * 
 */
@Repository
public class MessagesDAO {

	private static Logger LOGGER = Logger.getLogger(MessagesDAO.class);

	@PersistenceContext
	private EntityManager em;
	
	/**
	 * Persists {@link Messages} into the database.
	 * 
	 * @param message
	 * @return boolean
	 * @throws MessageNotFoundException
	 */
	public boolean createMessage(Messages message){
		em.persist(message);
		LOGGER.info("Message is created with id " + message.getId() + " and subject " + message.getSubject());
		
		try {
			getMessageByDateAndSubject(message.getCreationDate(), message.getSubject());
		} catch (MessageNotFoundException e) {
			return false;
		}
		return true;
	}
	
	/**
	 * Returns the last 5 not answered {@link Messages} order by creation date
	 * 
	 * @return
	 */
	public List<Messages> getLast5Messages(){
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Messages> cq = cb.createQuery(Messages.class);
		Root<Messages> root = cq.from(Messages.class);

		Predicate condition = cb.equal(root.get("answered"), false);
		cq.where(condition);
		
		cq.orderBy(cb.desc(root.get("creationDate")));
		List<Messages> resultWithAliasedBean = em.createQuery(cq)
				.setMaxResults(5)
				.getResultList();

		return resultWithAliasedBean;
	}
	
	/**
	 * Returns all not answered {@link Messages}.
	 * 
	 * @param page number of page
	 * @return List of Messages
	 */
	public List<Messages> getNotAnsweredMessages(int page){
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Messages> cq = cb.createQuery(Messages.class);
		Root<Messages> root = cq.from(Messages.class);

		Predicate condition = cb.equal(root.get("answered"), false);
		cq.where(condition);
		
		cq.orderBy(cb.desc(root.get("creationDate")));
		List<Messages> resultWithAliasedBean = em.createQuery(cq)
				.setFirstResult(page * Config.MESSAGES_MAX_RESULTS)
				.setMaxResults(Config.MESSAGES_MAX_RESULTS)
				.getResultList();

		return resultWithAliasedBean;
	}
	
	/**
	 * Returns all answered {@link Messages}.
	 * 
	 * @param page number of page
	 * @return List of Messages
	 */
	public List<Messages> getAnsweredMessages(int page){
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Messages> cq = cb.createQuery(Messages.class);
		Root<Messages> root = cq.from(Messages.class);

		Predicate condition = cb.equal(root.get("answered"), true);
		cq.where(condition);
		
		cq.orderBy(cb.desc(root.get("answeredDate")));
		List<Messages> resultWithAliasedBean = em.createQuery(cq)
				.setFirstResult(page * Config.MESSAGES_MAX_RESULTS)
				.setMaxResults(Config.MESSAGES_MAX_RESULTS)
				.getResultList();

		return resultWithAliasedBean;
	}

	/**
	 * Returns all {@link Messages}.
	 * 
	 * @param page number of page
	 * @return List of Messages
	 */
	public List<Messages> getMessages(int page){
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Messages> cq = cb.createQuery(Messages.class);
		Root<Messages> root = cq.from(Messages.class);

		cq.orderBy(cb.desc(root.get("creationDate")));
		List<Messages> resultWithAliasedBean = em.createQuery(cq)
				.setFirstResult(page * Config.MESSAGES_MAX_RESULTS)
				.setMaxResults(Config.MESSAGES_MAX_RESULTS)
				.getResultList();

		return resultWithAliasedBean;
	}
	
	/**
	 * Returns the number {@link Messages}.
	 * 
	 * @return long number of transactions
	 */
	public long getMessagesCount(){
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		Root<Messages> root = cq.from(Messages.class);
		cq.select(cb.count(root));
		
		long nofResults = em.createQuery(cq).getSingleResult().longValue();	
		return nofResults;	
	}
	
	/**
	 * Returns the number {@link Messages} which are not answered yet.
	 * 
	 * @return long number of transactions
	 */
	public long getNotAnsweredMessagesCount(){
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		Root<Messages> root = cq.from(Messages.class);
		cq.select(cb.count(root));
		Predicate condition = cb.equal(root.get("answered"), false);
		cq.where(condition);
		
		long nofResults = em.createQuery(cq).getSingleResult().longValue();	
		return nofResults;	
	}
	
	/**
	 * Returns the number {@link Messages} which are answered.
	 * 
	 * @return long number of transactions
	 */
	public long getAnsweredMessagesCount(){
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		Root<Messages> root = cq.from(Messages.class);
		cq.select(cb.count(root));
		Predicate condition = cb.equal(root.get("answered"), true);
		cq.where(condition);
		
		long nofResults = em.createQuery(cq).getSingleResult().longValue();	
		return nofResults;
	}

	/**
	 * Updates the messages from not answered to answered.
	 * 
	 * @param message
	 * @return boolean
	 * @throws MessageNotFoundException 
	 */
	public Messages updatedMessagesAnswered(long id, boolean answered, Date date) throws MessageNotFoundException{
		Messages message = getMessageById(id);
		message.setAnswered(answered);
		message.setAnsweredDate(date);
		em.merge(message);
		return message;
	}
	
	/**
	 * Gets the message by the given parameter id.
	 * 
	 * @param id
	 * @return Message
	 * @throws MessageNotFoundException 
	 */
	public Messages getMessageById(long id) throws MessageNotFoundException{
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Messages> cq = cb.createQuery(Messages.class);
		Root<Messages> root = cq.from(Messages.class);
		
		Predicate condition = cb.equal(root.get("id"), id);
		cq.where(condition);
		
		Messages message = getSingle(cq, em);
		
		if(message == null)
			throw new MessageNotFoundException(id);
		
		return message;
	}
	
	/**
	 * Gets the message by the given parameters date and subject.
	 * 
	 * @param date
	 * @param subject
	 * @return Message
	 * @throws MessageNotFoundException 
	 */
	public Messages getMessageByDateAndSubject(Date date, String subject) throws MessageNotFoundException{
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Messages> cq = cb.createQuery(Messages.class);
		Root<Messages> root = cq.from(Messages.class);
		
		Predicate condition1 = cb.equal(root.get("creationDate"), date);
		Predicate condition2 = cb.equal(root.get("subject"), subject);
		Predicate condition3 = cb.and(condition1, condition2);
		cq.where(condition3);
		
		Messages message = getSingle(cq, em);
		
		if(message == null)
			throw new MessageNotFoundException(subject, date);
		
		return message;
	}

	public boolean exits(String subject, String url) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Messages> cq = cb.createQuery(Messages.class);
		Root<Messages> root = cq.from(Messages.class);
		
		Predicate condition1 = cb.equal(root.get("subject"), subject);
		Predicate condition2 = cb.equal(root.get("serverUrl"), url);
		Predicate condition3 = cb.equal(root.get("answered"), false); 
		Predicate condition4 = cb.and(condition1, condition2, condition3);
		cq.where(condition4);
		
		Messages message = getSingle(cq, em);
		
		if(message == null)
			return false;
		
		return true;
	}

	/**
	 * 
	 * @param cq
	 * @param em
	 * @return
	 */
	private static <K> K getSingle(CriteriaQuery<K> cq, EntityManager em) {
		List<K> list = em.createQuery(cq).getResultList();
		if (list.size() == 0) {
			return null;
		}
		return list.get(0);
	}

}
