package ch.uzh.csg.coinblesk.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import ch.uzh.csg.coinblesk.server.clientinterface.IMessages;
import ch.uzh.csg.coinblesk.server.domain.Messages;
import ch.uzh.csg.coinblesk.server.service.MessagesService;
import ch.uzh.csg.coinblesk.server.util.Subjects;
import ch.uzh.csg.coinblesk.server.util.exceptions.MessageNotFoundException;
import ch.uzh.csg.coinblesk.server.utilTest.ReplacementDataSetLoader;
import ch.uzh.csg.coinblesk.server.utilTest.TestUtil;

import com.github.springtestdbunit.DbUnitTestExecutionListener;
import com.github.springtestdbunit.annotation.DatabaseOperation;
import com.github.springtestdbunit.annotation.DatabaseSetup;
import com.github.springtestdbunit.annotation.DbUnitConfiguration;
import com.github.springtestdbunit.annotation.ExpectedDatabase;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {
		"classpath:context.xml",
		"classpath:test-database.xml"})
@DbUnitConfiguration(databaseConnection="dataSource", dataSetLoader = ReplacementDataSetLoader.class)
@TestExecutionListeners({ 
	DependencyInjectionTestExecutionListener.class,
	DbUnitTestExecutionListener.class })
public class MessagesServiceTests {
	private static boolean initialized = false;

	@Autowired
	private IMessages messagesService;
	
	@BeforeClass
    public static void setUpClass() throws Exception {
        TestUtil.mockJndi();
    }
	
	@Before
	public void setUp() throws Exception {
		MessagesService.enableTestingMode();
		if (!initialized){		
			initialized = true;
		}
	}
	
	@After
	public void tearDown(){
		MessagesService.disableTestingMode();
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/messagesData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@ExpectedDatabase(value="classpath:DbUnitFiles/Services/messagesExcpectedData.xml", table="messages")
	public void testCreateMessage() throws MessageNotFoundException{
		List<Messages> allMessages = messagesService.getMessages(0);
		assertNotNull(allMessages);

		int nOfMessages = allMessages.size();
		assertEquals(nOfMessages,14);

		Messages message = new Messages();
		message.setMessage("new message");
		message.setServerUrl("https://www.my_url.ch");
		message.setSubject(Subjects.UPGRADE_TRUST_LEVEL);
		
		boolean success = false;
		success = messagesService.createMessage(message);
		
		List<Messages> allNewMessages = messagesService.getMessages(0);
		assertNotNull(allNewMessages);
		int nOfNewMessages = allNewMessages.size();

		assertTrue(success);
		assertFalse(nOfMessages == nOfNewMessages);
		assertTrue(nOfMessages < nOfNewMessages);		
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/messagesData.xml",type=DatabaseOperation.CLEAN_INSERT)
	@ExpectedDatabase(value="classpath:DbUnitFiles/Services/messagesExcpectedUpdatedData.xml", table="messages")
	public void testAnsweredMessage() throws MessageNotFoundException{
		long id = 10;
		Messages oldStatus = messagesService.getMessageById(id);
		assertNotNull(oldStatus);
		assertFalse(oldStatus.getAnswered());
		assertTrue(oldStatus.getAnsweredDate() == null);
		
		try {
			messagesService.updatedMessagesAnswered(id);
		} catch (MessageNotFoundException e) {
			throw new MessageNotFoundException(id);
		}
		
		Messages newStatus = messagesService.getMessageByDateAndSubject(oldStatus.getCreationDate(), oldStatus.getSubject());
		assertNotNull(newStatus);
		assertTrue(newStatus.getAnswered());
		assertFalse(newStatus.getAnsweredDate() == null);
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/messagesData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testMessagesCount(){
		long allMessages = messagesService.getMessagesCount();
		assertNotNull(allMessages);
		
		long allAnsweredMessages = messagesService.getAnsweredMessagesCount();
		assertNotNull(allAnsweredMessages);

		long allNotAnsweredMessages = messagesService.getNotAnsweredMessagesCount();
		assertNotNull(allNotAnsweredMessages);
		
		assertEquals(allMessages, 14);
		assertEquals(allAnsweredMessages, 7);
		assertEquals(allNotAnsweredMessages, 7);
		assertEquals(allMessages, allAnsweredMessages + allNotAnsweredMessages);
		
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/messagesData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testLast5Messages(){
		List<Messages> messages = messagesService.getLast5Messages();
		assertNotNull(messages);
		int nofMessages = messages.size();
		
		assertEquals(nofMessages, 5);
		
		for(Messages message: messages){
			assertEquals(message.getAnswered(), false);
		}
		
		for(Messages message: messages){
			assertEquals(message.getAnswered(), false);
		}

		for(int i = 0; i < messages.size() - 1; i++){
			assertTrue(messages.get(i).getCreationDate().compareTo(messages.get(i+1).getCreationDate()) >= 0);
		}
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/messagesData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void testGetMessages(){
		
		List<Messages> allMessages_1 = messagesService.getMessages(0);
		assertNotNull(allMessages_1);
		
		List<Messages> allAnsweredMessages_1 = messagesService.getAnsweredMessages(0);
		assertNotNull(allAnsweredMessages_1);

		List<Messages> allNotAnsweredMessages_1 = messagesService.getNotAnsweredMessages(0);
		assertNotNull(allNotAnsweredMessages_1);
		
		int nOFAll = allMessages_1.size();
		int nOfAnswered = allAnsweredMessages_1.size();
		int nOfNotAnswered = allNotAnsweredMessages_1.size();
		
		assertEquals(nOFAll, 14);
		assertEquals(nOfAnswered, 7);
		assertEquals(nOfNotAnswered, 7);
		assertEquals(nOFAll, nOfAnswered + nOfNotAnswered);
		
		List<Messages> allMessages_2 = messagesService.getMessages(1);
		List<Messages> allAnsweredMessages_2 = messagesService.getAnsweredMessages(1);
		List<Messages> allNotAnsweredMessages_2 = messagesService.getNotAnsweredMessages(1);
		
		assertEquals(allMessages_2.size(), 0);
		assertEquals(allAnsweredMessages_2.size(), 0);
		assertEquals(allNotAnsweredMessages_2.size(), 0);
	}
	
	@Test
	@DatabaseSetup(value="classpath:DbUnitFiles/Services/messagesData.xml",type=DatabaseOperation.CLEAN_INSERT)
	public void test_GetMessageByDateAndSubject() throws ParseException, MessageNotFoundException{
		String subject = "Trust level updgrade";
		String strDate = "2014-06-19 14:35:54.0";
		Date date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(strDate);
		Messages message = messagesService.getMessageByDateAndSubject(date, subject);
		assertNotNull(message);
		
		assertEquals(subject, message.getSubject());
		
	}
}
