#!/bin/sh
# install_slsnet.sh - to rebuild and reinstall slsnet and restart onos service

# build slsnet app
cd /home/yjlee/onos/slsnet/onos-app-slsnet/
mvn clean compile install || exit 1

# reinstall oar packet
onos-app localhost reinstall! org.onosproject.slsnet target/onos-app-slsnet-1.11.0-SNAPSHOT.oar

# reactivate slsnet app
onos-app localhost activate org.onosproject.slsnet

# reinstall network config
#sudo cp ../network-cfg.json /opt/onos/config/
sudo cp ../cisco-cfg.json /opt/onos/config/

# restart onos service
sudo service onos restart

