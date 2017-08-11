#!/bin/sh
# install_slsnet.sh - to rebuild and reinstall slsnet app 

# do build onos with maven for maven repository updates
# in onos source directory
# % mcis
# or % mvn clean install -DskipTests -Dcheckstyle.skip
# for sometimes onos-buck-publish-local might be NOT sufficently updates mvn repository

# to reinstall ONOS, call this script with "-r" argument
# ASSUME: onos source is at ../onos
REINISTALL_ONOS=no
if [ "$1" = '-r' ]
then
    echo reinistall ONOS from ../onos/
    sudo service onos stop
    sudo pkill java
    sudo rm -rf /opt/onos-1.11.0-SNAPSHOT/
    sudo tar -xzf ../onos/buck-out/gen/tools/package/onos-package/onos.tar.gz -C /opt
    sudo service onos start
    echo reinistall ONOS done
    exit 0
fi

# build, reinistall and reactivate slsnet app
cd onos-app-slsnet/
mvn clean compile install || exit 1
onos-app localhost reinstall! org.onosproject.slsnet target/onos-app-slsnet-1.11.0-SNAPSHOT.oar
onos-app localhost activate org.onosproject.slsnet
cd ..

# build, reinistall and reactivate slsnet driver
#cd onos-app-slsnet-driver/
#mvn clean compile install || exit 1
#onos-app localhost reinstall! org.onosproject.slsnet-driver target/onos-app-slsnet-driver-1.11.0-SNAPSHOT.oar
#onos-app localhost activate org.onosproject.slsnet-driver
#cd ..


# reinstall network config: if argument exists, use it as config file
if [ -n "$1" ]
then
    echo copy "$1" to /opt/onos/config/network-cfg.json
    sudo cp "$1" /opt/onos/config/network-cfg.json
else
    echo copy network-cfg.json to /opt/onos/config/
    sudo cp network-cfg.json /opt/onos/config/
fi

# restart onos service
sudo service onos restart

