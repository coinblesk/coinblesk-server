[![Build Status](https://travis-ci.org/coinblesk/coinblesk-server.svg?branch=master)](https://travis-ci.org/coinblesk/coinblesk-server)


# coinblesk-server

A mobile Bitcoin payment solution with NFC support.

## Install the dependencies

### Java 8 JDK
```bash
sudo apt-get install openjdk-8-jdk
```

OS X and Windows: [Oracle Java](http://www.oracle.com/technetwork/java/javase/downloads/index.html)

### Shared resources

```bash
git checkout git@github.com:coinblesk/coinblesk-shared-resources.git
cd coinblesk-shared-resources
./gradlew install
```

## Local development

Run 
```bash
./gradlew run
```

The service is available at [http://localhost:8080/](http://localhost:8080)
You can inspect the database during development at 
[http://localhost:8080/h2-console](http://localhost:8080/h2-console) and the REST endpoints at 
[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)

## Building

```bash
./gradlew assemble
```
Ready-to-run jar file is at `build/libs/coinblesk-server-2.1.SNAPSHOT.jar`

## Deployment

Run directly (jar is executable)
```bash
./coinblesk-server-2.1.SNAPSHOT.jar
```

or as jar with java
```
java -jar coinblesk-server-2.1.SNAPSHOT.jar
```

In production you probably want to use the production profile and set some additional sensitive configuration via environment variables:
```
SPRING_PROFILES_ACTIVE=prod \
BITCOIN_POTPRIVKEY=97324063353421115888582782536755703931560774174498831848725083330146537953701 \
SECURITY_JWT_SECRET=supersecret \
EMAIL_PASSWORD=hunter2 \
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost/coinblesk?user=fred&password=secret&ssl=true
java -jar coinblesk-server-2.1.SNAPSHOT.jar
```

## Configuration

The default configuration can be found with additional information in [application.properties](application.properties).

Some default settings for production environment is configured at [application-prod.properties](application-prod.properties).
To enable prod settings start the application with the environment variable `SPRING_PROFILES_ACTIVE=prod`

All configuration can be overruled by setting the equivalent environment variables at runtime. For example `bitcoin.net` can be set via a `BITCOIN_NET` variable.

The following settings can be configured:

| Variable                            | Description                                                                                                                                                              | Example                           |
|-------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------|
| SPRING_DATASOURCE_URL               | Database JDBC path. For in memory database use "jdbc:h2:mem:testdb"                                                                                                      | jdbc:h2:mem:testdb                |
| LOGGING_LEVEL_ROOT                  | Sets the root logging level (WARN, ERROR, DEBUG, OFF...)                                                                                                                 | INFO                              |
| COINBLESK_URL                       | The endpoint at which coinblesk api is available                                                                                                                         | https://coinblesk.org/            |
| COINBLESK_CONFIG_DIR                | The folder at which to store SPV chain and wallet files                                                                                                                  | /var/coinblesk                    |
| SECURITY_JWT_SECRET                 | The secret to use to sign JWTs. Should be long and complicated and always set via environment variable                                                                   | kI34jxqkrPxv8qYxaQpx98...         |
| SECURITY_JWT_VALIDITYINSECONDS      | Validity of JWT in seconds until expiration                                                                                                                              | 604800                            |
| SECURITY_JWT_ADMINVALIDITYINSECONDS | ... same for admin users                                                                                                                                                 | 3600                              |
| BITCOIN_NET                         | Which bitcoinnet to use: "mainnet", "testnet", "unittest"                                                                                                                | testnet                           |
| BITCOIN_FIRSTSEEDNODE               | Which server to try to connect first. In testnet mode: This is the only server we connect to.                                                                            | bitcoin4-fullnode.csg.uzh.ch      |
| BITCOIN_MINCONF                     | Number blocks for confirmations needed for a transaction                                                                                                                 | 1                                 |
| BITCOIN_POTPRIVKEY                  | Privatekey in number format for the pot of the server. Keep this secret and only set by environment variable. A new one can be generated with `new ECKey().getPrivKey()` | 973240633534211158885827803931... |
| BITCOIN_POTCREATIONTIME             | Creation time of the wallet pot in epoch seconds. Used for checkpointing optimization at initial chain download.                                                         | 1486638252                        |
| EMAIL_ENABLED                       | True / False. If the server should send out email.                                                                                                                       | True                              |
| EMAIL_HOST                          | `mail.smtp.host` SMTP host [see JavaMail API](https://javamail.java.net/nonav/docs/api/)                                                                                 | mail.office365.com                |
| EMAIL_PROTOCOL                      | `mail.transport.protocol` [see JavaMail API](https://javamail.java.net/nonav/docs/api/)                                                                                  | smtp                              |
| EMAIL_PORT                          | `mail.smtp.port` [see JavaMail API](https://javamail.java.net/nonav/docs/api/)                                                                                           | 587                               |
| EMAIL_AUTH                          | `mail.smtp.auth` Username/Password needed. [see JavaMail API](https://javamail.java.net/nonav/docs/api/)                                                                 | true                              |
| EMAIL_STARTTLS                      | `mail.smtp.starttls.enable` Use STARTTLS [see JavaMail API](https://javamail.java.net/nonav/docs/api/)                                                                   | true                              |
| EMAIL_DEBUG                         | `mail.debug` More email debug output. [see JavaMail API](https://javamail.java.net/nonav/docs/api/)                                                                      | false                             |
| EMAIL_TRUST                         | `mail.smtp.ssl.trust` Trust self signed mail servers. `mail.smtp.ssl.trust` [see JavaMail API](https://javamail.java.net/nonav/docs/api/)                                | false                             |
| EMAIL_USERNAME                      | Username for smtp server if auth enabled                                                                                                                                 | bob                               |
| EMAIL_PASSWORD                      | Password for smtp server if auth enabled                                                                                                                                 | supersecurepassword!              |
| EMAIL_ADMIN                         | Admin email address for warning related emails                                                                                                                           | admin@coinblesk.ch                |
| EMAIL_SENDFROM                      | Sender email for outgoing emails (account activation, password reset)                                                                                                    | info@coinblesk.ch                 |
