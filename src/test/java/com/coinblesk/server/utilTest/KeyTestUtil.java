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
package com.coinblesk.server.utilTest;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;

public class KeyTestUtil {
	/**
	 * Keys correspond to the keys in the keys.xml dataset.
	 */
	public static final ECKey ALICE_CLIENT = ECKey.fromPrivate(Sha256Hash.hash("alice-client".getBytes()));
	public static final ECKey ALICE_SERVER = ECKey.fromPrivate(Sha256Hash.hash("alice-server".getBytes()));

	public static final ECKey BOB_CLIENT = ECKey.fromPrivate(Sha256Hash.hash("bob-client".getBytes()));
	public static final ECKey BOB_SERVER = ECKey.fromPrivate(Sha256Hash.hash("bob-server".getBytes()));

	public static final ECKey CAROL_CLIENT = ECKey.fromPrivate(Sha256Hash.hash("carol-client".getBytes()));
	public static final ECKey CAROL_SERVER = ECKey.fromPrivate(Sha256Hash.hash("carol-server".getBytes()));

	public static final ECKey DAVE_CLIENT = ECKey.fromPrivate(Sha256Hash.hash("dave-client".getBytes()));
	public static final ECKey DAVE_SERVER = ECKey.fromPrivate(Sha256Hash.hash("dave-server".getBytes()));
}