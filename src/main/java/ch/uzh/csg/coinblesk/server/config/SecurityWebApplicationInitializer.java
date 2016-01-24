/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.config;

import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;

/**
 *
 * same as, see: http://docs.spring.io/spring-security/site/docs/4.0.x/guides/html5/hellomvc.html
 * <filter>
 *   <filter-name>springSecurityFilterChain</filter-name>
 *   <filter-class>org.springframework.web.filter.DelegatingFilterProxy</filter-class>
 * </filter>
 * 
 * <filter-mapping>
 *    <filter-name>springSecurityFilterChain</filter-name>
 *    <url-pattern>/*</url-pattern>
 * </filter-mapping>
 * 
 */
public class SecurityWebApplicationInitializer extends AbstractSecurityWebApplicationInitializer {
 
}
