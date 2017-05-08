package com.coinblesk.server.dao;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import com.coinblesk.server.entity.ServerPotBaseline;

public interface ServerPotBaselineRepository extends CrudRepository<ServerPotBaseline, Long> {

	@Query("SELECT SUM(amount) FROM SERVER_POT_BASELINE")
	public Long getSumOfAllAmounts();

}