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
 
```
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

```
       EH1
      /   \
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

1. LSn and HnN are in same subnet Nn
2. LSn acts as L2 switch for Hnm and L3 Subnet Router for Hnm  
3. SSn acts as inter-Subnet L3 Router for LSns and Use EH1 as Default Router


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


## ONOS Application Activation

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
- SDN-IP Reactive Forwarding App --> SDN-IP, Intent Synchronizer
  - https://wiki.onosproject.org/display/ONOS/SDN-IP+Reactive+Routing
  - handle cases at least one host is with Local SDN
  - handle ARP on virtual router ip
  - NO hanndling on ICMP on router ip  


## ONOS Configuration

ONOS SDN-IP Network Configuration Service: network-cfg.json 
- to clean: onos-netcfg localhost delete
- to update: onos-netcfg localhost network-cfg.json
  - each call updates loaded network config (onos netcfg to see loaded config)
  - updated values are immediately applied to existing entries
- hosts.basic.location value is not allowed
- port.{device_id}.interfaces must be set for all host ports
  with valid route ip configed as interfaces value

### 0. Lock down links by Network Config
Network Config Link Provider 
  may lock down topology and prevent unexpected link usage:

```
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

### 2. Inter Leaf Switch Forwarding (via Spine Switch)
SDN-IP Reactive Forwarding App
- handles inter switch (hnx--hmx) routing by adding host intents
- reactiveRoutings ip4LocalPrefixes of type PRIVATE only
- TO CHECK: ECMP handling for SL-SS allocation per host intents compile

```
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
NOT CHECKED YET


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

