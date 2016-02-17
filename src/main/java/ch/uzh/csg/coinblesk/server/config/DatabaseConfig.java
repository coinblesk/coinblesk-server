package ch.uzh.csg.coinblesk.server.config;

import java.util.Properties;

import javax.persistence.EntityManagerFactory;

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
		em.setPackagesToScan(new String[] { "ch.uzh.csg.coinblesk.server" });

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

	Properties additionalProperties() {
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
