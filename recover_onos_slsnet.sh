#!/bin/bash
# recover_onos_slsnet.sh - reinstall onos and slsnet cluster to recover from unresolved onos failure


echo 'This script reinstalls all ONOS/SLSNET cluster instances'
echo -n 'Go ahead? ("yes" to go, else to quit): '
read -t 60 input
RET=$?
if [ "$input" != yes ]
then
    if [ $RET -gt 128 ]
    then
       echo
       echo "quit by input timeout"
    else
       echo "quited"
    fi
    exit 0
fi

echo "Change working directory to `dirname $0`"
cd `dirname $0`

echo "Re-installing ONOS instances..."
psh ./install_onos.sh /home/sdn/onos/onos-1.11.0.root.tar.gz
echo "(waiting 20 seconds for onos instances initialized...)"
sleep 20 

echo "Forming ONOS Cluster..."
onos-form-cluster 192.168.1.5 192.168.0.3 192.168.1.2
echo "(waiting 30 seconds for onos instances restart...)"
sleep 30

echo "Install and Activate SLSNET Application..."
./install_slsnet.sh --no-build
echo "(waiting 20 seconds for slsnet application starts...)"
sleep 20 

echo "Reinstall ONOS Authentication Keys for SLSNET Watchd..."
watchd/ssh_key_setup.py

echo "ONOS/SLSNET Cluster Recovered"


echo
echo -n 'Run checknet/SlsNetCheckNet.py? ("yes" to run, else to quit): '
read -t 60 input
RET=$?
if [ "$input" != yes ]
then
    if [ $RET -gt 128 ]
    then
        echo
        echo "skip network check by input timeout"
    else
        echo "skip network check"
    fi
    exit 0
fi

exec checknet/SlsNetCheckNet.py -l 3


