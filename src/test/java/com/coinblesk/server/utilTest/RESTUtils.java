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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.coinblesk.json.BaseTO;
import com.coinblesk.util.SerializeUtils;

/**
 * 
 * @author Andreas Albrecht
 *
 */
public final class RESTUtils {
	private RESTUtils() {
		// prevent instance
	}
	
	@SuppressWarnings("unchecked")
	public static <T extends BaseTO<?>> T postRequest(MockMvc mockMvc, String url, T requestTO) throws Exception {
		String requestJSON = SerializeUtils.GSON.toJson(requestTO);
		return (T) postRequest(mockMvc, url, requestJSON, requestTO.getClass());
	}
	
	public static <T> T postRequest(MockMvc mockMvc, String url, String requestJSON, Class<T> responseClass) throws Exception {
    	final MvcResult res = mockMvc
				.perform(
						post(url)
						.secure(true)
						.contentType(MediaType.APPLICATION_JSON)
						.content(requestJSON))
				.andExpect(status().isOk())
				.andReturn();
    	final String responseJson = res.getResponse().getContentAsString();
    	final T responseTO = SerializeUtils.GSON.fromJson(responseJson, responseClass);
		return responseTO;
	}
}
