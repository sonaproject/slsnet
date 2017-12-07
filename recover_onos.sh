#!/bin/bash
# recover_onos.sh - reinstall onos and SimpleFabric cluster to recover from unresolved onos failure

echo 'This script reinstalls ONOS/SimpleFabric'
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
./install_onos.sh /home/sdn/onos/onos-1.13.0-SNAPSHOT.tar.gz
echo "(waiting 15 seconds for onos instances initialized...)"
sleep 15

echo "Install and Activate SimpleFabric Application..."
./install_simplefabric.sh --no-build
echo "(waiting 15 seconds for simplefabric application starts...)"
sleep 15

echo "Reinstall ONOS Authentication Keys for SimpleFabric Watchd..."
#watchd/ssh_key_setup.py
onos-user-key onos AAAAB3NzaC1yc2EAAAADAQABAAABAQC/mnMp9Qyqan2EOV45189NGK+NAc5iw48aguEHQWIa18kbAtWiVzZhDpj5qDP0esPXKPlwcoLi6mbZcvmTV3Xndi2FQQatdRds5yOa46a8lPc8qGau/920tMvRwODSspwEqQAzn6Heg4MbT8Nm6StLC2Wi6IRg/TizZblWxLbsmPXhr7gJH/+G/xE1jsCndPUREg0XGmFqwfPN4RrtJiFvvayRH9lctdudaElr1LEil5uvg+04Q6Ylr246IrZm5g8Wej+BcdcF13/NLEXus5sw3LME+CTzSTU9JFWACNKsn3kCNXDLqWjTEQWF67mXzX4OJNnQ2qloYVwr7T5CzOIR

echo "ONOS/SimpleFabric Recovered"

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


