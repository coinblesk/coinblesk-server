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

import com.coinblesk.server.service.UserAccountService;
import java.util.Properties;

import javax.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.annotation.PersistenceExceptionTranslationPostProcessor;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * This is the default configuration for testcases. If you want to change these
 * settings e.g. when using tomcat, add these values to context.xml:
 * 
 * <pre>
 *  ...
 *  <Parameter name="db.url" value="jdbc:postgresql:coinblesk" />
 *  <Parameter name="db.username" value="coinblesk" /> 
 *  <Parameter name="db.password" value="****" />
 *  <Parameter name="db.hbm2ddl.auto" value="verify" />
 *  <Parameter name="db.dialect" value="org.hibernate.dialect.PostgreSQLDialect" />
 *  <Parameter name="db.driver.class.name" value="org.postgresql.Driver" />
 *  ...
 * </pre>
 * 
 * @author Raphael Voellmy
 * @author Thomas Bocek
 */
@Configuration
@EnableTransactionManagement
public class DatabaseConfig {

	@Value("${db.url:jdbc:hsqldb:mem:paging}")
        //@Value("${db.url:jdbc:hsqldb:file:/tmp/db2}")
	private String databaseUrl;

	@Value("${db.username:sa}")
	private String databaseUsername;

	@Value("${db.password:}")
	private String databasePassword;

	@Value("${db.hbm2ddl.auto:create}")
	private String hbm2ddlAuto;

	@Value("${db.dialect:org.hibernate.dialect.HSQLDialect}")
	private String dialect;

	@Value("${db.driver.class.name:org.hsqldb.jdbcDriver}")
	private String driverClassName;
        
        @Value("${db.injectCredentials:true}")
	private boolean injectCredentials;
        
        @Autowired UserAccountService userAccountService;
        
        public boolean isTest() {
            return driverClassName.equals("org.hsqldb.jdbcDriver") && injectCredentials;
        }

	@Bean
	public DriverManagerDataSource dataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource(databaseUrl, databaseUsername,
				databasePassword);
		dataSource.setDriverClassName(driverClassName);
		return dataSource;
	}

	@Bean
	public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
		LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
		em.setDataSource(dataSource());
		em.setPackagesToScan(new String[] { "com.coinblesk.server" });

		JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
		em.setJpaVendorAdapter(vendorAdapter);
		em.setJpaProperties(additionalProperties());

		return em;
	}

	@Bean
	public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
		JpaTransactionManager transactionManager = new JpaTransactionManager();
		transactionManager.setEntityManagerFactory(emf);

		return transactionManager;
	}

	@Bean
	public PersistenceExceptionTranslationPostProcessor exceptionTranslation() {
		return new PersistenceExceptionTranslationPostProcessor();
	}

	private Properties additionalProperties() {
		Properties properties = new Properties();
		properties.setProperty("hibernate.hbm2ddl.auto", hbm2ddlAuto);
		properties.setProperty("hibernate.dialect", dialect);
		// connection pool

		properties.setProperty("hibernate.c3p0.min_size", "5");
		properties.setProperty("hibernate.c3p0.max_size", "20");
		properties.setProperty("hibernate.c3p0.timeout", "300");
		properties.setProperty("hibernate.c3p0.max_statements", "50");
		properties.setProperty("hibernate.c3p0.idle_test_period", "3000");

		return properties;
	}
}
