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

<table>
<tr><td>
Network Diagram
</td><td>
Mininet topology model: <a href="slsnet.py"><code>slsnet.py</code></a>
</td></tr>
<tr><td>
<pre>
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
</pre></td>
<td><pre>
   h31(10.0.0.31/24)    h32(10.0.0.32/24)
           |                    |
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
</pre></td></tr>
</table>

- LSn acts as L2 switch for Hnm and L3 Subnet Router for Hnm  
- SSn acts as inter-Subnet L3 Router for LSns and Use EH1 as Default Router


<br/>

## [TBD] SLSNET Application


### SLSNET App Build, Install and Activate

in `onos-app-slsnet` directory

BUILD:
- `mvn clean compile install`

INSTALL TO ONOS AND ACTIVATE APP:
- `onos-app localhost install target/onos-app-slsnet-1.11.0-SNAPSHOT.oar`
- `onos-app localhost activate org.onosproject.slsnet`

Folling app are auto activated by SLSNET app's dependency
- Intent Synchronizer
- OpenFlow Provider (for OpenFlow Controller) --> Optical inforamtion model
- Host Location Provider (for auto regi host from ARP)
- Network Config Link Provider (for auto Regi/Deregi Links)


### ONOS Network Configuration

- SEE: [network-cfg.json](network-cfg.json)
- to update: `onos-netcfg localhost network-cfg.json`
  - each call updates loaded network config (onos netcfg to see loaded config)
  - updated values are immediately applied to existing entries
- to clean: `onos-netcfg localhost delete`
- to be applied at onos restarts, copy `network-cfg.json` to `${ONOS_HOME}/config/`


<!-- EXPECTED FUTURE CONIFUGRATION TO-BE
```none

  "devices":{
    "of:0000000000000001":{ "basic":{ "name":"SS1", "latitude":40, "longitude":-100 } },
    "of:0000000000000002":{ "basic":{ "name":"SS2", "latitude":40, "longitude":-90  } },
    "of:000000000000000a":{ "basic":{ "name":"LS1", "latitude":35, "longitude":-100 } },
    "of:0000000000000014":{ "basic":{ "name":"LS2", "latitude":35, "longitude":-90  } }
  },

  "ports" : {
    "of:0000000000000001/1" : { "interfaces" : [ { "name" : "SS1_LS1" } ] },
    "of:0000000000000001/2" : { "interfaces" : [ { "name" : "SS1_LS2" } ] },
    "of:0000000000000001/3" : { "interfaces" : [ { "name" : "SS1_h31" } ] },
    
    "of:0000000000000002/1" : { "interfaces" : [ { "name" : "SS2_LS1" } ] },
    "of:0000000000000002/2" : { "interfaces" : [ { "name" : "SS2_LS2" } ] },
    "of:0000000000000002/3" : { "interfaces" : [ { "name" : "SS2_h32" } ] },

    "of:000000000000000a/1" : { "interfaces" : [ { "name" : "h11" } ] },
    "of:000000000000000a/2" : { "interfaces" : [ { "name" : "h12" } ] },
    "of:000000000000000a/3" : { "interfaces" : [ { "name" : "h13" } ] },
    "of:000000000000000a/4" : { "interfaces" : [ { "name" : "h14" } ] },
    "of:000000000000000a/5" : { "interfaces" : [ { "name" : "d11" } ] },
    "of:000000000000000a/6" : { "interfaces" : [ { "name" : "d12" } ] },
    "of:000000000000000a/7" : { "interfaces" : [ { "name" : "LS1_SS1" } ] },
    "of:000000000000000a/8" : { "interfaces" : [ { "name" : "LS1_SS2" } ] },
  
    "of:0000000000000014/1" : { "interfaces" : [ { "name" : "h21" } ] },
    "of:0000000000000014/2" : { "interfaces" : [ { "name" : "h22" } ] },
    "of:0000000000000014/3" : { "interfaces" : [ { "name" : "h23" } ] },
    "of:0000000000000014/4" : { "interfaces" : [ { "name" : "h24" } ] },
    "of:0000000000000014/5" : { "interfaces" : [ { "name" : "d21" } ] },
    "of:0000000000000014/6" : { "interfaces" : [ { "name" : "d22" } ] },
    "of:0000000000000014/7" : { "interfaces" : [ { "name" : "LS2_SS1" } ] },
    "of:0000000000000014/8" : { "interfaces" : [ { "name" : "LS2_SS2" } ] }
  },

  "apps" : {
    "org.onosproject.slsnet" : {
      "subnetList" : [
        {
          "interfaces" : ["h11", "h12", "h13", "h14", "d11", "d12" ],
          "ipv4" : { "prefix" : "10.0.1.0/24", "gatewayIp" : "10.0.1.1", "gatewayMac" : "00:00:00:00:01:01" }
        },
        {
          "interfaces" : ["h21", "h22", "h23", "h24", "d21", "d22" ],
          "ipv4" : { "prefix" : "10.0.2.0/24", "gatewayIp" : "10.0.2.1", "gatewayMac" : "00:00:00:00:02:01" }
        }
      ]
      "ipv4GatewayList" : [
        { "prefix" : "0.0.0.0/0", "gatewayIp" : "10.0.0.31" }
      ]
    }
  }
  
```
-->


## Roles of SLSNET App's Sub Features

SLSNET VLAN L2 Broadcast Network App (VPLS)
- https://wiki.onosproject.org/display/ONOS/Virtual+Private+LAN+Service+-+VPLS
- to handle L2 switch local broadcast/unicast
- may configure to include multiple switches

SLSNET SDN-IP Reactive Forwarding App
- https://wiki.onosproject.org/display/ONOS/SDN-IP+Reactive+Routing
- handles inter switch (hnx--hmx) routing by adding host intents
- port.{device_id}.interfaces must be set for all host ports
  with valid route ip configed as interfaces value
- reactiveRoutings ip4LocalPrefixes of type PRIVATE only
- handle ARP on virtual router ip
- NO hanndling on ICMP on router ip  
- ** TO CHECK: ECMP handling for SL-SS allocation per host intents compile **
- ** ISSUE SDN-IP Installed Intents's Host MAC is not updated when Host's MAC value is changed (ex. restart Mininet)
  - The related flow seem not working for DST MAC is updated as old MAC, then receiving host DROPs IT!!! **

SLSNET SDN-IP + Incubator Routing API
- affect SND-IP Intents for local<->external traffic
- register default route by netcfg "routes" subject within org.onosproject.slsnet app configuration
- onos cli command: `routes`
- registers MultiPointToSinglePointIntent for source={all edge ports with named interface} to target={port for next hop}
  (seems auto probe for the next hop host)

