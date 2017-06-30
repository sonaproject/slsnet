# README file for OBSLETE Tries


<br/>

# SLSNET with ONOS Bundle Application

- OBSOLETE at 2017-06-30
- works but network service is unstable - no use for real service


## ONOS Configuration

**ONOS Application Activation**

onos cli command:
```
app activate org.onosproject.openflow-base
app activate org.onosproject.hostprovider
app activate org.onosproject.netcfglinksprovider
app activate org.onosproject.vpls
app activate org.onosproject.reactive-routing
```

- Default device drivers (default Run)
- OpenFlow Provider (for OpenFlow Controller) --> Optical inforamtion model
- Host Location Provider (for auto regi host from ARP)

- Network Config Link Provider
  - https://wiki.onosproject.org/display/ONOS/Network+Config+Link+Provider
  - auto Regi/Deregi Links

- VLAN L2 Broadcast Network Appp (VPLS)
  - https://wiki.onosproject.org/display/ONOS/Virtual+Private+LAN+Service+-+VPLS
  - to handle L2 switch local broadcast/unicast
  - may configure to include multiple switches
OR
- Proxy ARP/NDP App
  - Forwards ARP/NDP Appropriately without intents installed for each host

- SDN-IP Reactive Forwarding App --> SDN-IP, Intent Synchronizer
  - https://wiki.onosproject.org/display/ONOS/SDN-IP+Reactive+Routing
  - handle cases at least one host is with Local SDN
  - handle ARP on virtual router ip
  - NO hanndling on ICMP on router ip  

**ONOS Network Configuration** [`network-cfg-base-app.json`](network-cfg-base-app.json)
- to update: `onos-netcfg localhost network-cfg-base-app.json`
  - each call updates loaded network config (onos netcfg to see loaded config)
  - updated values are immediately applied to existing entries
- to clean: `onos-netcfg localhost delete`
- hosts.basic.location value is not allowed


**CAUTION: SDN-IP with Host IP-MAC Updates**
- SDN-IP Installed Intents's Host MAC is not updated when Host's MAC value is changed (ex. restart Mininet)
  - The related flow seem not working for DST MAC is updated as old MAC, then receiving host DROPs IT!!!

### 0. Lock down links by Network Config
Network Config Link Provider 
  may lock down topology and prevent unexpected link usage:

```txt
  "links" : {
    "of:0000000000000001/1-of:000000000000000a/7" : { "basic" : {} },
    "of:0000000000000001/2-of:0000000000000014/7" : { "basic" : {} },
    "of:0000000000000002/1-of:000000000000000a/8" : { "basic" : {} },
    "of:0000000000000002/2-of:0000000000000014/8" : { "basic" : {} },

    "of:000000000000000a/7-of:0000000000000001/1" : { "basic" : {} },
    "of:0000000000000014/7-of:0000000000000001/2" : { "basic" : {} },
    "of:0000000000000001/8-of:0000000000000002/1" : { "basic" : {} },
    "of:0000000000000014/8-of:0000000000000002/2" : { "basic" : {} }
  },
  
  "apps" : {
    "org.onosproject.core" : {
      "core" : { "linkDiscoveryMode" : "STRICT" }
    }
  }
```

### 1. Intra Leaf Switch Forwarding
VLAN L2 Broadcast Network App (VPLS)
- add name-only interface per each ports to handle (should be separate interface to SDN-IP's)
- add config per vpls and it's port names in vpls app config
- **ISSUE: VPLS seems to try once when Network Config is loaded: devices must be alive at the netcfg loading time !!!**

```
  "ports" : {
    "of:000000000000000a/1" : { "interfaces" : [ { "name" : "h11" } ] },
    "of:000000000000000a/2" : { "interfaces" : [ { "name" : "h12" } ] },
    "of:000000000000000a/3" : { "interfaces" : [ { "name" : "h13" } ] },
    "of:000000000000000a/4" : { "interfaces" : [ { "name" : "h14" } ] },
    "of:000000000000000a/5" : { "interfaces" : [ { "name" : "d11" } ] },
    "of:000000000000000a/6" : { "interfaces" : [ { "name" : "d12" } ] },

    "of:0000000000000014/1" : { "interfaces" : [ { "name" : "h21" } ] },
    "of:0000000000000014/2" : { "interfaces" : [ { "name" : "h22" } ] },
    "of:0000000000000014/3" : { "interfaces" : [ { "name" : "h23" } ] },
    "of:0000000000000014/4" : { "interfaces" : [ { "name" : "h24" } ] },
    "of:0000000000000014/5" : { "interfaces" : [ { "name" : "d21" } ] },
    "of:0000000000000014/6" : { "interfaces" : [ { "name" : "d22" } ] },
  },
  "apps" : {
    "org.onosproject.vpls" : {
      "vpls" : {
        "vplsList" : [
          { "name" : "VPLS1", "interfaces" : ["h11", "h12", "h13", "h14", "d11", "d12" ] },
          { "name" : "VPLS2", "interfaces" : ["h21", "h22", "h23", "h24", "d21", "d22" ] }
        ]
      }
    }
  }
```

OR

Proxy ARP/NDP App
- Forwards and proxy host's ARP/NDP request
- No configuration needed
- Cannot handle broadcast IP traffics


### 2. Inter Leaf Switch Forwarding (via Spine Switch)
SDN-IP Reactive Forwarding App
- handles inter switch (hnx--hmx) routing by adding host intents
- port.{device_id}.interfaces must be set for all host ports
  with valid route ip configed as interfaces value
- reactiveRoutings ip4LocalPrefixes of type PRIVATE only
- TO CHECK: ECMP handling for SL-SS allocation per host intents compile
- **ISSUE: sometimes reactive forwarding seems not working; intents not installed**

```txt
  "devices":{
    "of:0000000000000001":{ "basic":{ "name":"SS1", "latitude":40, "longitude":-100 } },
    "of:0000000000000002":{ "basic":{ "name":"SS2", "latitude":40, "longitude":-90  } },
    "of:000000000000000a":{ "basic":{ "name":"LS1", "latitude":35, "longitude":-100 } },
    "of:0000000000000014":{ "basic":{ "name":"LS2", "latitude":35, "longitude":-90  } }
  },
  
  "ports" : {
    "of:0000000000000001/1" : { "interfaces" : [ { "name" : "SS1_LS1" } ] },
    "of:0000000000000001/2" : { "interfaces" : [ { "name" : "SS1_LS2" } ] },
    "of:0000000000000001/3" : { "interfaces" : [ { "name" : "SS1_h31" }, { "ips" : [ "10.0.0.10/24" ], "mac"  : "00:00:00:00:00:01" } ] },

    "of:0000000000000002/1" : { "interfaces" : [ { "name" : "SS2_LS1" } ] },
    "of:0000000000000002/2" : { "interfaces" : [ { "name" : "SS2_LS2" } ] },
    "of:0000000000000002/3" : { "interfaces" : [ { "name" : "SS2_h32" }, { "ips"  : [ "10.0.0.10/24" ], "mac"  : "00:00:00:00:00:01" } ] },

    "of:000000000000000a/1" : { "interfaces" : [ { "name" : "h11" }, { "ips" : [ "10.0.1.1/24" ], "mac" : "00:00:00:00:00:01" } ] },
    "of:000000000000000a/2" : { "interfaces" : [ { "name" : "h12" }, { "ips" : [ "10.0.1.1/24" ], "mac" : "00:00:00:00:00:01" } ] },
    "of:000000000000000a/3" : { "interfaces" : [ { "name" : "h13" }, { "ips" : [ "10.0.1.1/24" ], "mac" : "00:00:00:00:00:01" } ] },
    "of:000000000000000a/4" : { "interfaces" : [ { "name" : "h14" }, { "ips" : [ "10.0.1.1/24" ], "mac" : "00:00:00:00:00:01" } ] },
    "of:000000000000000a/5" : { "interfaces" : [ { "name" : "d11" }, { "ips" : [ "10.0.1.1/24" ], "mac" : "00:00:00:00:00:01" } ] },
    "of:000000000000000a/6" : { "interfaces" : [ { "name" : "d12" }, { "ips" : [ "10.0.1.1/24" ], "mac" : "00:00:00:00:00:01" } ] },
    "of:000000000000000a/7" : { "interfaces" : [ { "name" : "LS1_SS1" } ] },
    "of:000000000000000a/8" : { "interfaces" : [ { "name" : "LS1_SS2" } ] },

    "of:0000000000000014/1" : { "interfaces" : [ { "name" : "h21" }, { "ips" : [ "10.0.2.1/24" ], "mac" : "00:00:00:00:00:01" } ] },
    "of:0000000000000014/2" : { "interfaces" : [ { "name" : "h22" }, { "ips" : [ "10.0.2.1/24" ], "mac" : "00:00:00:00:00:01" } ] },
    "of:0000000000000014/3" : { "interfaces" : [ { "name" : "h23" }, { "ips" : [ "10.0.2.1/24" ], "mac" : "00:00:00:00:00:01" } ] },
    "of:0000000000000014/4" : { "interfaces" : [ { "name" : "h24" }, { "ips" : [ "10.0.2.1/24" ], "mac" : "00:00:00:00:00:01" } ] },
    "of:0000000000000014/5" : { "interfaces" : [ { "name" : "d21" }, { "ips" : [ "10.0.2.1/24" ], "mac" : "00:00:00:00:00:01" } ] },
    "of:0000000000000014/6" : { "interfaces" : [ { "name" : "d22" }, { "ips" : [ "10.0.2.1/24" ], "mac" : "00:00:00:00:00:01" } ] },
    "of:0000000000000014/7" : { "interfaces" : [ { "name" : "LS2_SS1" } ] },
    "of:0000000000000014/8" : { "interfaces" : [ { "name" : "LS2_SS2" } ] }
  },
  
  "apps" : {
      "org.onosproject.reactive.routing" : {
      "reactiveRouting" : {
        "ip4LocalPrefixes" : [
           { "ipPrefix" : "10.0.0.0/24", "type" : "PRIVATE", "gatewayIp" : "10.0.0.10" },
           { "ipPrefix" : "10.0.1.0/24", "type" : "PRIVATE", "gatewayIp" : "10.0.1.1"  },
           { "ipPrefix" : "10.0.2.0/24", "type" : "PRIVATE", "gatewayIp" : "10.0.1.14" }
        ],
        "ip6LocalPrefixes" : [
        ],
        "virtualGatewayMacAddress" : "00:00:00:00:00:01"
      }
    }
  }
```

### 3. External Forwarding (via Spine Switch and External Router)
Use ONOS Incubator API/Command routes/route-add which affect SND-IP Intents for local-external traffic
- register default route with onos cli route command: `onos -lonos 'route-add 0.0.0.0/0 10.0.0.31'`
  - to show route table: `onos -lonos routes`
  - Password authentication
- registers MultiPointToSinglePointIntent for source={all edge ports with named interface} to target={port for next hop}
  (seems auto probe for the next hop host)

```txt
Password: 
Table: ipv4
    Network            Next Hop        Source (Node)
>   0.0.0.0/0          10.0.0.31       STATIC (-)
   Total: 1

Table: ipv6
    Network            Next Hop        Source (Node)
   Total: 0
```

- - [SHOULD NOT] if ip4LocalPrifixes are used for default route, intents for every external hosts are installed
  - `{ "ipPrefix" : "0.0.0.0/0", "type" : "PRIVATE", "gatewayIp" : "10.0.0.31" }`


## Reference ONOS Apps

### Critical Applications
- Default device drivers
- Flow Space Analysis App
- Flow specification Device Drivers
- Flowspec API
- Intent Synchronizer
- Host Location Provider (for auto Regi/Deregi Host from ARP/NDP/DHCP)
- Network Config Link Provider (for auto Regi/Deregi Links)
- OpenFlow Agent App
- OpenFlow Provider (for OpenFlow Switch Connect)
- Optical information model (for OpenFlow privider)

### Reference for SLSNET Developement
- Path Visualization App (might be omitted)
- FIB installler App
- Fault Managemnet App
- Proxy ARP/NDP App (for all flag L2 model, edge ports)
- Reactive Forwarding App
- Virtual Router App
- Link Dicovery Provider
- Host Location Provider
- Network Config Host Provider
  --> seems not working good with SDN-IP Reactive Forwarding
- LLDB Link Provider
- OpenFlow Provider


## ISSUE
- How to cover host's MAC updates applied to SDN-IP's already-installed intents



<br/>

# OLD Design -- considering directly implement OpenFlow Rules

## Features

### Leaf Switch [LSn]
1. L2 Unicast Handling for Hnm (Output to Learned Port or Flood)
2. L2 Broadcast Handling
3. ARP Learning for Hnm MAC and IP Learning
   - Host Location Provider registeres Hosts Info from ARP, NDP or DHCP
4. Handle ARP Request/Response for LSn's IP as Hnm's Subnet Gateway
   - CONSIDER: Proxy ARP/NDP App
5. on dst_mac=LSn, L3 Route to Hnm, (src_mac<-LSn, dst_mac<=Hnm)
6. on dst_mac=LSn, L3 Route to SSm for non-Subnet IPs (src_mac<-LSn, dst_mac=SSm)
   with Load Balancing on SS's

### Spine Switch [SSn]
1. Do ARP Request on EH1 IP and Learn EH1 Mac and IP from ARP response
   - SEE: Host Location Provider's private sendProve()
2. Handle ARP Request/Response for SSn's IP for EH1's Request
   - CONSIDER: Proxy ARP/NDP App
3. on dst_mac=SSn, L3 Route to LSm for each subnets (src_mac<-SSn,dst_mac<-LSm)
4. on dst_mac=SSn, L3 Route to EH1 as defaut route (src_mac<-SSn,dst_mac<-EHn)

### High Avaliablility [LSn and SSn]
1. on SSn-LSm link failed, SSn forward to other SS with SSm link available via LSx
2. on SSn-LSm link failed, LSx forward to other SS with SSm link available
3. on SSn-EH1 link failed, SSn forward to other SS EH1 link available via LSx
4. on SSn-EH1 link failed, LSx forward to other SS EH1 link available




## OpenFlow Flow Entries and Controller Actions 
for `Cisco Nexus 3172PQ` as Leaf Switch for ECMP and HA


### Leaf Switch LSn

for ARP handling and Learning
- LF1. on ethertype=arp copy to Controller, go ahead
   (Controller learns Hnm's mac or do arp response for request on LSn)

for L3 Routing to Hnm
- LF2. on dst_mac=LSn, dst_ip=LSn, output to Controler, end
   (for LSn router action like icmp echo)
- LF3. on dst_mac=LSn, dst_ip=Hnm, output to Hnm port, dst_mac<-Hnm, src_mac<-LSn, end
- LF4. on dst_mac=LSn, dst_ip=Hn/net, output to Controller, end
   (for Controller to trigger ARP request on ths Hnm_ip unknown)
- LF5. !!! on dst_mac=LSn, dst_ip=unknown, output to SSm (NEED TO SELECT SS) !!!,
   with dst_mac<-SSm, src_mac<-LSn update, end

for L2 Switching
- LF6. on dst_mac=Hnm, output to Hnm.port, end 
- LF7. on dst_mac=broadcast and port=SSm, drop, end
     to ignore possible flooding from LS-SS port
- LF8. on dst_mac=broadcast, flooding (maybe Hnm.ports only), end

default
- LF9. drop by default
- LF10. on link up/down event noti to Controller
     if LSn-SSm link down, change LSn rule 4, not to use SSm


### Spine Switch SSn (no self subnet and host)

for ARP handling and Learning
- SF1. on ethertype=arp copy to Controller, go ahead
   (Controller learns EHn's mac or do arp response for request on SSn)

for L3 Routing to LSn
- SF2. on dst_mac=SSn, dst_ip=SSn, output to Controler, end
   (for SSn router action like icmp echo)
- SF3. on dst_mac=SSn, dst_ip=Hmx/net, output to LSm port, dst_mac<-LSm, src_mac<-SSn, end
- SF4. (?if LSm port is down may icmp host unreachable response)

for L3 Routing to External Network
- SF5. on dst_mac=SSn, src_mac!=EHn, output to EHn, dst_mac<-EHn, src_mac<-SSn, end
    (default route to external network)

default
- SF6. drop by default
- SF7. on link up/down event send noti to Controller
   (?? do not send to linked downed LSn by using LSm alive)


### Assume:
- LSn's Hnm IP range is known by configuration
- LSn's and SSm's mac are known by configuration
  or use Device Info's port MACs for each link

### Config Items
- per LS Device ID
    { vIP, vMAC, {ports for SSn}, ports for Hm }
- per SS Device ID
    { vIP, vMAC, {ports for LSn}, port for EHn }
- consider ports for SSn and LSm MAY BE auto detected using ONOS Application:
    Link Discovery Provider 
- LSn is mapped 1:1 to subnet 
    (do not consider 1:n or n:1 case for now)
- Assume OpenFlow switch with constraints:
    - 1 flow table only
    - no group table
    - Packet In/Out are available



## Usefull ONOS Console Commands

- command, arguments auto fill: TAB을 치면 해당 자리에 들어갈 수 있는 command 나 arugment 항목을 보여줌

- onos:devices
  - device 정보 출력

- onos:annotate-(type)
  - 해당 type의 entry에 key value 정보 항목을 추가; onos:devices, links, ports 에서 보여짐
  - annotate-device of:0000000000000001 name SS1
  - annotate-port of:0000000000000001/1 portName toLS1
  - ? annotate-link of:0000000000000001/1 of:000000000000000a/7 name SS1-LS1

- onos:routes
  - route-add
  - route-remove
