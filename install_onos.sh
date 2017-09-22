#!/bin/sh
# USAGE: install_onos.sh [-r] [onos_tar_file]
#    -r  stop and remove only
# ASSUME: onos packet tar file is at ../onos/

INSTALL_ONOS='yes'
if [ "$1" = '-r' ]
then
    INSTALL_ONOS='no'
    shift   
fi

TARGET=/opt/onos
UNTARED_DIR=/opt/onos-1.11.0
ONOS_TAR=${1:-../onos/buck-out/gen/tools/package/onos-package/onos.tar.gz}

if [ $INSTALL_ONOS = 'yes' -a ! -r $ONOS_TAR ]
then
    echo cannot open ONOS_TAR file: $ONOS_TAR
    exit 1
fi 

echo stop and remove ONOS from $TARGET and $UNTARED_DIR
sudo service onos stop
ONOS_PROC=`ps -eaf | grep java | grep $TARGET | awk '{ print $2 }'`
[ -n "$ONOS_PROC" ] && sudo kill $ONOS_PROC
sudo rm -rf $TARGET $UNTARED_DIR

# clean up ssh known_hosts on onos console
ssh-keygen -f ~/.ssh/known_hosts -R localhost:8101

[ $INSTALL_ONOS = 'no' ] && exit 0


echo install ONOS from $ONOS_TAR to $TARGET
sudo tar -xzf $ONOS_TAR -C `dirname $UNTARED_DIR`
sudo ln -s $UNTARED_DIR $TARGET
sudo service onos start


