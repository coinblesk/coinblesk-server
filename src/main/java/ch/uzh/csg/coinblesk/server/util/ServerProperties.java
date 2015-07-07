package ch.uzh.csg.coinblesk.server.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.hibernate.PropertyNotFoundException;

public class ServerProperties {

    private static Logger LOGGER = Logger.getLogger(ServerProperties.class);
    
    private static final String FILENAME = "server.properties";
    private static Properties properties;
    
    static {
        properties = new Properties();
        try {
            InputStream inputStream = ServerProperties.class.getClassLoader().getResourceAsStream(FILENAME);
            properties.load(inputStream);
            inputStream.close();
        } catch (IOException e) {
            LOGGER.fatal("Could not read property file " + FILENAME + ".");
            e.printStackTrace();
        }
      }

    /**
     * Returns the server property for a specified key
     * @param key The property key
     * @return the property
     * @throws PropertyNotFoundException if the property was not found in the property file
     */
    public static String getProperty(String key) {
        String property = properties.getProperty(key);
        if (property == null) {
            LOGGER.fatal("Property " + key + " was not set in properties file " + FILENAME);
            throw new PropertyNotFoundException("Property " + key + " was not found.");
        }
        return property;
    }
    
    public static Properties getProperties() {
        return properties;
    }

}
