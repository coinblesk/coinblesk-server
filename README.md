## coinblesk-server

A mobile Bitcoin payment solution with NFC support.

### Installation

gradle clean install

The following instructions are for Debian based systems.

In order to install and run the CoinBlesk server, the following dependencies have to be installed:

* Java 7 or higher
* Tomcat 7 or higher
* Maven
* PostgreSQL Server & Client

##### Install the dependencies

```bash
sudo apt-get install openjdk-8-jdk tomcat8 tomcat8-admin maven postgresql postgresql-client
```

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

##### Server Settings

All server settings are loaded from the context.xml file of the Tomcat directory. A sample context.xml file is included in this project (SAMPLE.context.xml).

##### Compilation

Create a directory for coinblesk and enter it:

```bash
mkdir coinblesk && cd -
```

Clone the projects "coinblesk-shared-resources" and "coinblesk-server":
```bash
git clone https://github.com/coinblesk/coinblesk-shared-resources.git
git clone https://github.com/coinblesk/coinblesk-server.git
```

Install the modules in your local maven repository:

```bash
mvn -f coinblesk-shared-resources/pom.xml clean install
mvn -f coinblesk-server/pom.xml clean install
```

##### Deployment

Deploy the CoinBlesk server by running:

```bash
mvn -f coinblesk-server/pom.xml tomcat7:deploy-only
```

After that, the CoinBlesk server should be running at http://localhost:8080/coinblesk


