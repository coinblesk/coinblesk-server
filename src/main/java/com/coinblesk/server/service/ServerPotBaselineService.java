package com.coinblesk.server.service;

import java.util.Date;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.coinblesk.server.dao.ServerPotBaselineRepository;
import com.coinblesk.server.entity.ServerPotBaseline;

@Service
public class ServerPotBaselineService {

	private final static Logger LOG = LoggerFactory.getLogger(ServerPotBaselineService.class);

	private ServerPotBaselineRepository serverPotBaselineRepository;

	@Autowired
	public ServerPotBaselineService(ServerPotBaselineRepository serverPotBaselineRepository) {
		this.serverPotBaselineRepository = serverPotBaselineRepository;
	}

	@Transactional(readOnly = true)
	public long getServerPotBaseline() {
		LOG.debug("ServerPotBaseline is requested");
		Long dbValue = serverPotBaselineRepository.getSumOfAllAmounts();

		if(dbValue == null) {
			return 0L;
		} else {
			return dbValue;
		}
	}

	@Transactional
	public void addNewServerPotBaselineAmount(long amount) {
		LOG.debug("Adding new amount {} to server pot baseline.");

		ServerPotBaseline baselinePotAmount = new ServerPotBaseline();
		baselinePotAmount.setAmount(amount);
		baselinePotAmount.setTimestamp(new Date());
		serverPotBaselineRepository.save(baselinePotAmount);
	}

	@Transactional
	public List<ServerPotBaseline> getAllServerPotBaselineRows() {
		return serverPotBaselineRepository.findAllByOrderByTimestampAsc();
	}

}
