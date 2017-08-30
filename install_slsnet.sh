#!/bin/sh
# install_slsnet.sh - to rebuild and reinstall slsnet app 
# USAGE: install_slsnet.sh [-r] [network-cfg.json]
# PREPARE: run "onos-buck-publish-local" in ONOS source directory
# ASSUME: onos source is at ../onos

TARGET=/opt/onos

# to reinstall ONOS, call this script with "-r" argument
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
(
cd onos-app-slsnet/
mvn clean compile install || exit 1
onos-app localhost reinstall! org.onosproject.slsnet target/onos-app-slsnet-1.11.0-SNAPSHOT.oar
onos-app localhost activate org.onosproject.slsnet
)

# reinstall network config
# if argument exists, use it as config file
# else if env SLSNET_NETCFG is defined use it
# else use network-cfg.json file
NETCFG_FILE=${1:-${SLSNET_NETCFG:-network-cfg.json}}
echo copy "$NETCFG_FILE" to $TARGET/config/network-cfg.json
sudo cp "$NETCFG_FILE" $TARGET/config/network-cfg.json

# restart onos service
sudo service onos restart

