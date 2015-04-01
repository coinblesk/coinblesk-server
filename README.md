## coinblesk-server

A mobile Bitcoin payment solution with NFC support.

### Installation

The following instructions are for Debian based systems.

In order to run the CoinBlesk server, the following dependencies have to be installed:

* Java 7 or higher
* Tomcat 8 and Tomcat Manager
* Maven
* PostgreSQL Server & Client
* Bitcoin Core

##### Install the dependencies

```bash
sudo apt-get install openjdk-7-jdk tomcat8 tomcat8-admin maven postgresql postgresql-client
```

##### Install the Bitcoin Core

```bash
sudo add-apt-repository ppa:bitcoin/bitcoin
sudo apt-get update
sudo apt-get install bitcoind
```

Create a Bitcoin config file:

```bash
nano ~/.bitcoin/bitcoin.conf
```

and ther the following settings:

```
rpcuser=<someusername>
rpcpassword=<someverycomplicatedpassword>
```

After that, start the Bitcoin daemon and synchronize the blockchain:

```bash
bitcoind -daemoin
```

Synchronizing the blockchain may take several days to complete and currently requires ~30GB of storage.

##### Database Setup

Start the PostgreSQL server and create the coinblesk database:

```bash
sudo service postgresql start
sudo -u postgres createdb coinblesk
```

##### Tomcat Setup

In order to deploy CoinBlesk to tomcat, make sure you have a Tomcat user with username "admin" and no password, that has the role "manager-script":

```xml
<!-- {$TOMCAT_HOME}/tomcat-users.xml -->
<?xml version='1.0' encoding='utf-8'?>
<tomcat-users>
  <role rolename="manager-script"/>
  <user username="admin" password="" roles="manager-script"/>
</tomcat-users>
```

Make sure to restart tomcat after you changed the tomcat-users.xml file.

*Side note*: If you don't want to change your Tomcat users, you can also change the tomcat credentials in the pom.xml 
file of the coinblesk-server project, and edit the tomcat user credentials in the 
tomcat7-maven-plugin settings. The same applies for deploying on a remote Tomcat instance. 
The documentation for the tomcat7-maven-plugin can be found [here](http://tomcat.apache.org/maven-plugin-2.0/tomcat7-maven-plugin/deploy-mojo.html).

The database credentials, as well as all other credentials for the application, are loaded form the 
Tomcat instance's context.xml file. You can find a sample context.xml in the coinblesk-server project root directory (SAMPLE.context.xml). 
Simply edit the credentials and copy the file in your Tomcat instance's config directory and restart Tomcat afterwards. The
credentials of Bitcoind are not required. If not present, they will be read from your bitcoin config file (```~/.bitcoin/bitcoin.conf```).


##### CoinBlesk Compilation and Deployment

Create a directory for coinblesk and enter it:

```bash
mkdir coinblesk && cd -
```

Clone the projects "custom-serialization", "coinblesk-shared-resources" and "coinblesk-server":
```bash
git clone https://github.com/coinblesk/custom-serialization.git
git clone https://github.com/coinblesk/coinblesk-shared-resources.git
git clone https://github.com/coinblesk/coinblesk-server.git
```

Install the dependencies to your maven repository:

```bash
mvn -f custom-serialization/pom.xml clean install
mvn -f coinblesk-shared-resources/pom.xml clean install
```

Compile and deploy the CoinBlesk server:

```bash
mvn -f coinblesk-server/pom.xml tomcat7:deploy
```

After that, the CoinBlesk server should be running at http://yourtomcatserver.com/coinblesk











