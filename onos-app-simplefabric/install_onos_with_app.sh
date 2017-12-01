#!/bin/sh


echo stop and remove onos...
sudo service onos stop
sleep 2
sudo pkill java
sleep 2
sudo rm -rf /opt/onos-1.12.0-SNAPSHOT


echo install and starting onos...
sudo tar -C /opt -xzf /tmp/onos-1.12.0.yjlee.tar.gz
sudo service onos start
sleep 15

echo activating apps...
onos-app localhost activate org.onosproject.openflow-base
onos-app localhost activate org.onosproject.lldpprovider
onos-app localhost activate org.onosproject.hostprovider
onos-app localhost activate org.onosproject.simplefabric
sleep 5

echo load network configuration...
onos-netcfg localhost network-cfg.json

