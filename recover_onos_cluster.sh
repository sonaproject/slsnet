#!/bin/bash
# recover_onos_cluster.sh - reinstall onos and SimpleFabric cluster to recover from unresolved onos failure


echo "This script disabled now"
exit 1


echo 'This script reinstalls all ONOS/SimpleFabric cluster instances'
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
echo "(waiting 15 seconds for onos instances initialized...)"
sleep 15

echo "Forming ONOS Cluster..."
onos-form-cluster 192.168.101.5 192.168.100.3 192.168.101.2
echo "(waiting 30 seconds for onos instances restart...)"
sleep 30

echo "Install and Activate SimpleFabric Application..."
./install_simplefabric.sh --no-build
echo "(waiting 15 seconds for simplefabric application starts...)"
sleep 15

echo "Reinstall ONOS Authentication Keys for SimpleFabric Watchd..."
watchd/ssh_key_setup.py

echo "ONOS/SimpleFabric Cluster Recovered"


echo
echo -n 'Run checknet/SimpleFabricCheckNet.py? ("yes" to run, else to quit): '
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

exec checknet/SimpleFabricCheckNet.py -l 3


