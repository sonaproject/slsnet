#!/bin/sh
# install_slsnet.sh - to rebuild and reinstall slsnet app 
# USAGE: install_slsnet.sh [--no-build|--build-only] [network-cfg.json]
# PREPARE: run "onos-buck-publish-local" in ONOS source directory
# ASSUME: onos source is at ../onos

TARGET=/opt/onos

SLSNET_VERSION=1.13.0-SNAPSHOT

BUILD=yes
INSTALL=yes

case "$1" in
  (--no-build)   BUILD=no;   shift; break;;
  (--build-only) INSTALL=no; shift; break;;
esac


if [ -n "$INSTALL" ]
then
    NETCFG_FILE=${1:-${SLSNET_NETCFG:-network-cfg.json}}
    if [ ! -r "$NETCFG_FILE" ]
    then
        echo "cannot open network config file for read: $NETCFG_FILE"
        exit 1
    fi
fi


# build slsnet app
if [ "$BUILD" = yes ]
then
    ( cd onos-app-slsnet/; mvn clean compile install || exit 1 )
fi

if [ "$INSTALL" = no ]
then
    exit 0
fi

# reinistall and reactivate slsnet app
onos-app localhost uninstall org.onosproject.slsnet
onos-app localhost install onos-app-slsnet/target/onos-app-slsnet-${SLSNET_VERSION}.oar
onos-app localhost activate org.onosproject.openflow-base
onos-app localhost activate org.onosproject.lldpprovider
onos-app localhost activate org.onosproject.hostprovider
onos-app localhost activate org.onosproject.slsnet

# reinstall network config
# if argument exists, use it as config file
# else if env SLSNET_NETCFG is defined use it
# else use network-cfg.json file
echo "install network config file: $NETCFG_FILE" 
onos-netcfg localhost delete
onos-netcfg localhost $NETCFG_FILE
# do not install network-cfg.json on cluster case
#sudo cp "$NETCFG_FILE" $TARGET/config/network-cfg.json

# restart onos service
#sudo service onos restart

