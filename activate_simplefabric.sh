#!/bin/sh
# USAGE: activate_simplefabric.sh [network-cfg.json]

TARGET=/opt/onos

NETCFG_FILE=${1:-${SIMPLEFABRIC_NETCFG:-network-cfg.json}}
if [ ! -r "$NETCFG_FILE" ]
then
    echo "cannot open network config file for read: $NETCFG_FILE"
    exit 1
fi

# reinistall and reactivate simplefabric app
echo deactivating simplefabric app
onos-app localhost deactivate org.onosproject.simplefabric
sleep 5
echo activating simplefabric app
onos-app localhost activate org.onosproject.openflow-base
onos-app localhost activate org.onosproject.lldpprovider
onos-app localhost activate org.onosproject.hostprovider
onos-app localhost activate org.onosproject.simplefabric

# reinstall network config
# if argument exists, use it as config file
# else if env SIMPLEFABRIC_NETCFG is defined use it
# else use network-cfg.json file
echo "reinstall network config file: $NETCFG_FILE" 
onos-netcfg localhost delete
onos-netcfg localhost $NETCFG_FILE
# do not install network-cfg.json on cluster case
sudo cp "$NETCFG_FILE" $TARGET/config/network-cfg.json

