#!/bin/bash

SERVERS=( "bitcoin2-test.csg.uzh.ch" )

if [ "$#" -eq 1 ]; then
   PRIV="-i $1 "
fi

#Build and install of the frontend
if ! ./gradlew clean build -x test; then
    echo "gradle build failed"
    exit 1
fi

# Deployment
#
# Make sure to have a systemd init script as found in: https://docs.spring.io/spring-boot/docs/current/reference/html/deployment-install.html

for i in "${SERVERS[@]}"
do
    echo  scp -r "$PRIV" 'build/libs/coinblesk-*.jar' "$i":/var/lib/coinblesk/coinblesk.jar
    scp -r "$PRIV"build/libs/coinblesk-*.jar "$i":/var/lib/coinblesk/coinblesk.jar
    ssh "$PRIV$i" sudo systemctl restart coinblesk.service
done
