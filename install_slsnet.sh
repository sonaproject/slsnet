#!/bin/sh
# install_slsnet.sh - to rebuild and reinstall slsnet and restart onos service

# goto slsnet base directory
cd /home/yjlee/onos/slsnet/onos-app-slsnet/


# to rebuild ONOS
# ASSUME: onos source is at /home/yjlee/onos/onos/
#sudo service onos stop; sudo pkill java; sudo rm -rf /opt/onos-1.11.0-SNAPSHOT/; sudo tar -xzf /home/yjlee/onos/onos/buck-out/gen/tools/package/onos-package/onos.tar.gz -C /opt; sudo service onos start


# build slsnet app
mvn clean compile install || exit 1

# reinstall oar packet
onos-app localhost reinstall! org.onosproject.slsnet target/onos-app-slsnet-1.11.0-SNAPSHOT.oar

# reactivate slsnet app
onos-app localhost activate org.onosproject.slsnet

# reinstall network config
sudo cp ../network-cfg.json /opt/onos/config/
#sudo cp ../cisco-cfg.json /opt/onos/config/

# restart onos service
sudo service onos restart

