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
package com.coinblesk.server.config;

import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;

/**
 *
 * same as, see: http://docs.spring.io/spring-security/site/docs/4.0.x/guides/html5/hellomvc.html
 * <filter>
 * <filter-name>springSecurityFilterChain</filter-name>
 * <filter-class>org.springframework.web.filter.DelegatingFilterProxy</filter-class>
 * </filter>
 *
 * <filter-mapping>
 * <filter-name>springSecurityFilterChain</filter-name>
 * <url-pattern>/*</url-pattern>
 * </filter-mapping>
 *
 */
public class SecurityWebApplicationInitializer extends AbstractSecurityWebApplicationInitializer {

}
