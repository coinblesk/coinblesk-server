package com.coinblesk.server.service;

import static com.coinblesk.server.enumerator.EventType.SERVER_BALANCE_NOT_IN_SYNC;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.coinblesk.dto.ServerBalanceDTO;

@Service
public class ServerBalanceService {

	private final MicropaymentService microPaymentService;
	private final AccountService accountService;
	private final ServerPotBaselineService serverPotBaselineService;
	private final EventService eventService;

	@Autowired
	public ServerBalanceService(MicropaymentService microPaymentService, AccountService accountService,
			ServerPotBaselineService serverPotBaselineService, EventService eventService) {
		this.microPaymentService = microPaymentService;
		this.accountService = accountService;
		this.serverPotBaselineService = serverPotBaselineService;
		this.eventService = eventService;
	}

	public ServerBalanceDTO getServerBalance() {
		ServerBalanceDTO result = new ServerBalanceDTO();
		result.setSumOfAllPendingTransactions(microPaymentService.getPendingChannelValue().getValue());
		result.setSumOfAllVirtualBalances(accountService.getSumOfAllVirtualBalances());
		result.setServerPotCurrent(microPaymentService.getMicroPaymentPotValue().getValue());
		result.setServerPotBaseline(serverPotBaselineService.getTotalServerPotBaseline());
		result.setInSync(balanceIsInSync(result));

		return result;
	}

	private boolean balanceIsInSync(ServerBalanceDTO serverBalance) {
		return serverBalance.getSumOfAllPendingTransactions()
				== serverBalance.getSumOfAllVirtualBalances()
				+ serverBalance.getServerPotBaseline()
				- serverBalance.getServerPotCurrent();
	}

	@Scheduled(fixedDelay = 60000L)
	public void checkServerBalanceAndStoreCriticalEvent() {
		ServerBalanceDTO serverBalance = getServerBalance();
		if(!serverBalance.isInSync()) {
			String message = "Server Balance is not in Sync. Check the input values: " + serverBalance;
			eventService.fatal(SERVER_BALANCE_NOT_IN_SYNC, message);
		}
	}
}
