/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.coinblesk.server.service;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.coinblesk.dto.ServerBalanceDTO;
import com.coinblesk.server.utilTest.CoinbleskTest;

public class ServerBalanceServiceTest extends CoinbleskTest {

	@Autowired
	private ServerBalanceService serverBalanceService;

	@Autowired
	private ServerPotBaselineService serverPotBaselineService;

	@Test
	public void testServerBalanceSyncStatus() {
		ServerBalanceDTO serverBalance = serverBalanceService.getServerBalance();
		Assert.assertTrue(serverBalance.isInSync());

		serverPotBaselineService.addNewServerPotBaselineAmount(100);
		serverBalance = serverBalanceService.getServerBalance();
		Assert.assertFalse(serverBalance.isInSync());

		serverPotBaselineService.addNewServerPotBaselineAmount(-100);
		serverBalance = serverBalanceService.getServerBalance();
		Assert.assertTrue(serverBalance.isInSync());
	}

}
