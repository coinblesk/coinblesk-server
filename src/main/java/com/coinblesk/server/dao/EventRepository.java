package com.coinblesk.server.dao;

import java.util.Date;
import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import com.coinblesk.server.entity.Event;
import com.coinblesk.server.enumerator.EventUrgence;

public interface EventRepository extends CrudRepository<Event, Long> {

	@Query("SELECT COUNT(*) FROM EVENT e WHERE e.date < (:olderThanDate)")
	public long countOldEntries(@Param("olderThanDate") Date olderThanDate);

	@Modifying
	@Query("DELETE FROM EVENT e WHERE e.date < (:olderThanDate)")
	public void deleteOldEntries(@Param("olderThanDate") Date olderThanDate);

	@Query("SELECT e FROM EVENT e WHERE e.date BETWEEN (:start) AND (:end)")
	public Set<Event> getEventsBetween(@Param("start") Date beginningDate, @Param("end") Date endDate);

	@Query("SELECT e FROM EVENT e WHERE e.urgence = (:urgence) ORDER BY e.date DESC")
	public List<Event> getEventsWithUrgence(@Param("urgence") EventUrgence urgence);

}
