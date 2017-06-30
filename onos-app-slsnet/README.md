# Building Simple Leaf-Spine Network Applicatgion (onos-app-slsnet)

Build oar with Maven
- `mvn clean install`

built oar file
- [target/onos-app-slsnet-1.11.0-SNAPSHOT.oar]()

load oar file to ONOS
- `onos-app localhost install target/onos-app-slsnet-1.11.0-SNAPSHOT.oar`

reload oar file to ONOS
- `onos-app localhost reinstall org.onosproject.slsnet target/onos-app-slsnet-1.11.0-SNAPSHOT.oar`
 
