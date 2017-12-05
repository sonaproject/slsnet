#!/bin/sh
# install_simplefabric.sh - to rebuild and reinstall simplefabric app 
# USAGE: install_simplefabric.sh [--no-build|--build-only] [network-cfg.json]
# PREPARE: run "onos-buck-publish-local" in ONOS source directory
# ASSUME: onos source is at ../onos

TARGET=/opt/onos

SIMPLEFABRIC_VERSION=1.13.0-SNAPSHOT

BUILD=yes
INSTALL=yes

case "$1" in
  (--no-build)   BUILD=no;   shift; break;;
  (--build-only) INSTALL=no; shift; break;;
esac


if [ -n "$INSTALL" ]
then
    NETCFG_FILE=${1:-${SIMPLEFABRIC_NETCFG:-network-cfg.json}}
    if [ ! -r "$NETCFG_FILE" ]
    then
        echo "cannot open network config file for read: $NETCFG_FILE"
        exit 1
    fi
fi


# build simplefabric app
if [ "$BUILD" = yes ]
then
    ( cd onos-app-simplefabric/; mvn clean compile install || exit 1 )
fi

if [ "$INSTALL" = no ]
then
    exit 0
fi

# reinistall and reactivate simplefabric app
onos-app localhost uninstall org.onosproject.simplefabric
onos-app localhost install onos-app-simplefabric/target/onos-app-simplefabric-${SIMPLEFABRIC_VERSION}.oar
onos-app localhost activate org.onosproject.openflow-base
onos-app localhost activate org.onosproject.lldpprovider
onos-app localhost activate org.onosproject.hostprovider
onos-app localhost activate org.onosproject.simplefabric

# reinstall network config
# if argument exists, use it as config file
# else if env SIMPLEFABRIC_NETCFG is defined use it
# else use network-cfg.json file
echo "install network config file: $NETCFG_FILE" 
onos-netcfg localhost delete
onos-netcfg localhost $NETCFG_FILE
# do not install network-cfg.json on cluster case
sudo cp "$NETCFG_FILE" $TARGET/config/network-cfg.json

# restart onos service
#sudo service onos restart

