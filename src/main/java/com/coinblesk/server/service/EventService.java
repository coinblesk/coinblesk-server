package com.coinblesk.server.service;

import static com.coinblesk.server.enumerator.EventType.EVENT_SERVICE_AUTO_REMOVAL;
import static com.coinblesk.server.enumerator.EventType.EVENT_SERVICE_EMERGENCY_EMAIL_SENT;
import static com.coinblesk.server.enumerator.EventUrgence.DEBUG;
import static com.coinblesk.server.enumerator.EventUrgence.ERROR;
import static com.coinblesk.server.enumerator.EventUrgence.FATAL;
import static com.coinblesk.server.enumerator.EventUrgence.INFO;
import static com.coinblesk.server.enumerator.EventUrgence.WARN;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.coinblesk.server.dao.EventRepository;
import com.coinblesk.server.entity.Event;
import com.coinblesk.server.enumerator.EventType;
import com.coinblesk.server.enumerator.EventUrgence;

@Service
public class EventService {

	private final static Logger LOG = LoggerFactory.getLogger(EventService.class);

	private EventRepository eventRepository;
	private MailService mailService;

	@Autowired
	public EventService(EventRepository eventRepository, MailService mailService) {
		this.eventRepository = eventRepository;
		this.mailService = mailService;
	}

	@Transactional
	public void addEvent(EventUrgence urgence, EventType type, String description) {
		LOG.debug("Create an event with urgence {}, type {} and description {}", urgence, type, description);

		Event event = new Event();
		event.setDate(new Date());
		event.setUrgence(urgence);
		event.setType(type);
		event.setDescription(description);

		eventRepository.save(event);
	}

	public void debug(EventType type, String description) {
		this.addEvent(DEBUG, type, description);
	}
	public void info(EventType type, String description) {
		this.addEvent(INFO, type, description);
	}
	public void warn(EventType type, String description) {
		this.addEvent(WARN, type, description);
	}
	public void error(EventType type, String description) {
		this.addEvent(ERROR, type, description);
	}
	public void fatal(EventType type, String description) {
		this.addEvent(FATAL, type, description);
	}

	public List<Event> getEventsWithUrgenceOrHigher(EventUrgence urgence) {
		List<Event> events = new ArrayList<>();
		if(FATAL.equals(urgence) || ERROR.equals(urgence) || WARN.equals(urgence) || INFO.equals(urgence) || DEBUG.equals(urgence)) {
			events.addAll(eventRepository.getEventsWithUrgence(FATAL));
		}
		if(ERROR.equals(urgence) || WARN.equals(urgence) || INFO.equals(urgence) || DEBUG.equals(urgence)) {
			events.addAll(eventRepository.getEventsWithUrgence(ERROR));
		}
		if(WARN.equals(urgence) || INFO.equals(urgence) || DEBUG.equals(urgence)) {
			events.addAll(eventRepository.getEventsWithUrgence(WARN));
		}
		if(INFO.equals(urgence) || DEBUG.equals(urgence)) {
			events.addAll(eventRepository.getEventsWithUrgence(INFO));
		}
		if(DEBUG.equals(urgence)) {
			events.addAll(eventRepository.getEventsWithUrgence(DEBUG));
		}
		return events;
	}

	@Transactional
	@Scheduled(cron = "0 0 3 * * *") // every day at 3am
	public void scheduledRemovalOfOldEntries() {
		final int DELETE_AFTER_DAYS = 90;

		Date calculatedDate = addTime(new Date(), Calendar.DATE, - DELETE_AFTER_DAYS);
		long numberOfDeletingEntries = eventRepository.countOldEntries(calculatedDate);

		if(numberOfDeletingEntries > 0) {
			eventRepository.deleteOldEntries(calculatedDate);

			this.info(EVENT_SERVICE_AUTO_REMOVAL, "Approx. " + numberOfDeletingEntries + " entries were automatically removed.");
		}
	}

	@Transactional
	@Scheduled(cron = "0 */10 * * * *") // every 10 minutes (00, 10, 20, 30, 40, 50)
	public void scheduledCheckForWarningsAndNotifyInEmergency() {
		final int TEN_MINUTES_THRESHOLD_FATAL = 0;
		final int TEN_MINUTES_THRESHOLD_ERROR = 5;
		final int TEN_MINUTES_THRESHOLD_WARN = 20;

		Date beforeDate = addTime(new Date(), Calendar.MINUTE, -10);
		Date nowDate = new Date();

		Map<EventUrgence, Set<Event>> groupedEvents = new HashMap<>();
		EventUrgence[] allEventUrgences = EventUrgence.values();
		for(EventUrgence eventUrgence : allEventUrgences) {
			groupedEvents.put(eventUrgence, new HashSet<>());
		}

		Set<Event> events = eventRepository.getEventsBetween(beforeDate, nowDate);
		for(Event event : events) {
			groupedEvents.get(event.getUrgence()).add(event);
		}

		if(groupedEvents.get(FATAL).size() > TEN_MINUTES_THRESHOLD_FATAL
				|| groupedEvents.get(ERROR).size() > TEN_MINUTES_THRESHOLD_ERROR
				|| groupedEvents.get(WARN).size() > TEN_MINUTES_THRESHOLD_WARN) {

			StringBuffer buffer = new StringBuffer();
			if(groupedEvents.get(FATAL).size() > 0) {
				buffer.append(FATAL + ":\n");
				buffer.append(countEventTypesAndGetString(groupedEvents.get(FATAL)));
				buffer.append("\n");
			}
			if(groupedEvents.get(ERROR).size() > 0) {
				buffer.append(ERROR + ":\n");
				buffer.append(countEventTypesAndGetString(groupedEvents.get(ERROR)));
				buffer.append("\n");
			}
			if(groupedEvents.get(WARN).size() > 0) {
				buffer.append(WARN + ":\n");
				buffer.append(countEventTypesAndGetString(groupedEvents.get(WARN)));
				buffer.append("\n");
			}
			int totalWarningAndAboveEvents = groupedEvents.get(FATAL).size() + groupedEvents.get(ERROR).size() + groupedEvents.get(WARN).size();

			mailService.sendAdminMail("emergency: event threshold exceeded",
					"A lot of events occured in the last 10 minutes on the server. See the following list (warning and above events):\n\n" + buffer.toString());
			this.info(EVENT_SERVICE_EMERGENCY_EMAIL_SENT, "An emergency e-mail was sent ("+totalWarningAndAboveEvents+" warning and above events occurred in the last 10min)");
		}
	}

	private String countEventTypesAndGetString(Set<Event> events) {
		Map<EventType, Integer> countedEventTypes = new HashMap<>();
		for(Event event : events) {
			if(!countedEventTypes.containsKey(event.getType())) {
				countedEventTypes.put(event.getType(), 0);
			}
			countedEventTypes.put(event.getType(), countedEventTypes.get(event.getType()) + 1);
		}

		StringBuffer buffer = new StringBuffer();
		for(Map.Entry<EventType, Integer> entry : countedEventTypes.entrySet()) {
			EventType type = entry.getKey();
			Integer count = entry.getValue();
			buffer.append(type);
			buffer.append(": ");
			buffer.append(count);
			buffer.append("\n");
		}
		return buffer.toString();
	}

	private Date addTime(Date date, int dateCategory, int amount) {
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(date);
		calendar.add(dateCategory, amount);
		Date calculatedDate = calendar.getTime();

		return calculatedDate;
	}

}
