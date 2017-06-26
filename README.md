# Simple Leaf-Spine Network Application
Lee Yongjae, 2017-06-15.



## SONA Fabric 설계
from: 정종식

### 최종 목표
- Leaf-Spine Network을 자동으로 설정한다.
- East-West Traffic 상태에 따라 효율적인 경로 설정을 제공한다. (ECMP인 경우에도 해당?)
- Leaf-Spine Network의 상태를 쉽게 파악할 수 있다.
- 스위치 또는 포트 장애에 끊김없이 대처할 수 있다.

### 필요한 기능
- L2 Unicast: intra-rack (intra-subnet) untagged communication when the destination is known.
- L2 Broadcast: intra-rack (intra-subnet) untagged communication when the destination host is unknown.
- ARP: ARP packets will be sent to the controller and App will do proxy ARP on L3 Router IP’s
- L3 Unicast: inter-rack (inter-subnet) untagged communication when the destination is known.
- VLAN Cross Connect: ?? 

### 예상동작
- Leaf Switch:
  - L2 Unicast: destMac이 leaf switch와 일치하지 않으면, 학습한 MAC 주소를 찾아 적절한 포트로 보낸다.
  - L2 Broadcast: destMAC이 (학습하지 않아) 없으면, 패킷을 모든 포트로 보낸다.
  - ARP: 라우터 IP응 요청하면 router MAC을 보내고, 알고 있는 호스트를 요청하면 그 호스트를 대산하여 proxy ARP를 보내며, 모르는 호스트를 요청하는 경우 같은 서브넷의 모든 포트로 패킷을 보낸다.
  - L3 Unicast: destMAC이 leaf switch와 일치하고 IPv4 패킷이므로 
- Spine Switch
  - L3 Unicast

### 가정
- 시스코 스위치가 OpenFlow 1.3을 지원하지만 multi-table은 지원하지 않고 single table만 지원할 가능성이 높음
- 이 경우 ECMP 지원은 불가능함
 
```txt
Device# show openflow switch 1

Logical Switch Context
  Id: 1
  Switch type: Forwarding
  Pipeline id: 201
  Signal version: Openflow 1.0
  Data plane: secure
  Table-Miss default: NONE
  Config state: no-shutdown
  Working state: enabled
  Rate limit (packet per second): 0
  Burst limit: 0
  Max backoff (sec): 8
  Probe interval (sec): 5
  TLS local trustpoint name: not configured
  TLS remote trustpoint name: not configured
  Stats coll. period (sec): 5
  Logging flow changes: Disabled
  OFA Description:
    Manufacturer: Cisco Systems, Inc.
    Hardware: N3K-C3064PQ V01
    Software: 6.0(2)U2(1) of_agent 1.1.0_fc1
    Serial Num: SSI15200QD8
    DP Description: n3k-200-141-3:sw1
 FLOW_ OF Features:
    DPID:0001547fee00c2a0
    Number of tables:1
    Number of buffers:256
    Capabilities: STATS TABLE_STATS PORT_STATS
    Actions: OUTPUT SET_VLAN_VID STRIP_VLAN SET_DL_SRC SET_DL_DST
  Controllers:
    1.1.1.1:6653, Protocol: TLS, VRF: s
  Interfaces:
    Ethernet1/1
    Ethernet1/7
```


## Info from SKT

- 10G Server x 9
- 40G Storage x 4
- Leaf Switch: 10Gs + 40G x 6 (2 for Spine and 4 for Storage): [may be Cisco Nexus 3172PQ](http://www.cisco.com/c/en/us/td/docs/switches/datacenter/nexus/openflow/b_openflow_agent_nxos_1_3/Cisco_Plug_in_for_OpenFlow.html)
- Spine Switch: Maybe 40G
- Do ECMP and Link Failover



## Topology

```txt
       EH1
      /   \
     /     \
  [SS1]   [SS2]
    |  \ /  |
    |   X   |
    |  / \  |
  [LS1]   [LS2]
   +- H11  +- H21
   +- H12  +- H22
   +- H13  +- H23
   +- H14  +- H24
   +- D11  +- D21
   +- D12  +- D22
```

Mininet topology model: [`slsnet.py`](slsnet.py)
```txt
   h31(10.0.0.31/24)    h32(10.0.0.32/24)
           |                    |
  [ss1(10.0.0.10/24)]  [ss2(10.0.0.20/24)]
           |        \ /         |
           |         X          |
           |        / \         |
  [s10(10.0.1.1/24)]   [s20(10.0.2.1/24)]
   +- h11(10.0.1.11)    +- h21(10.0.2.21)
   +- h12(10.0.1.12)    +- h22(10.0.2.22)
   +- h13(10.0.1.13)    +- h23(10.0.2.23)
   +- h14(10.0.1.14)    +- h24(10.0.2.24)
   +- d11(10.0.1.111)   +- d21(10.0.2.221)
   +- d12(10.0.1.112)   +- d22(10.0.2.222)
```

- LSn acts as L2 switch for Hnm and L3 Subnet Router for Hnm  
- SSn acts as inter-Subnet L3 Router for LSns and Use EH1 as Default Router


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

**ONOS Network Configuration** [`network-cfg.json`](network-cfg.json)
- to clean: `onos-netcfg localhost delete`
- to update: `onos-netcfg localhost network-cfg.json`
  - each call updates loaded network config (onos netcfg to see loaded config)
  - updated values are immediately applied to existing entries
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

