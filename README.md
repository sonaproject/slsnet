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
  - consider Network Config Host Provider
  - consider Network Config Link Provider


## Features

### Leaf Switch [LSn]
1. L2 Unicast Handling for Hnm (Output to Learned Port or Flood)
2. L2 Broadcast Handling
3. ARP Learning for Hnm MAC and IP Learning
4. Handle ARP Request/Response for LSn's IP as Hnm's Subnet Gateway; consider Proxy ARP/NDP App
5. on dst_mac=LSn, L3 Route to Hnm, (src_mac<-LSn, dst_mac<=Hnm)
6. on dst_mac=LSn, L3 Route to SSm for non-Subnet IPs (src_mac<-LSn, dst_mac=SSm)
   with Load Balancing on SS's

### Spine Switch [SSn]
1. Do ARP Request on EH1 IP and Learn EH1 Mac and IP from ARP response
2. Handle ARP Request/Response for SSn's IP for EH1's Request
3. on dst_mac=SSn, L3 Route to LSm for each subnets (src_mac<-SSn,dst_mac<-LSm)
4. on dst_mac=SSn, L3 Route to EH1 as defaut route (src_mac<-SSn,dst_mac<-EHn)

### High Avaliablility [LSn and SSn]
1. on SSn-LSm link failed, SSn forward to other SS with SSm link available via LSx
2. on SSn-LSm link failed, LSx forward to other SS with SSm link available
3. on SSn-EH1 link failed, SSn forward to other SS EH1 link available via LSx
4. on SSn-EH1 link failed, LSx forward to other SS EH1 link available



## OpenFlow Flow Entries and Controller Actions 
for `Cisco Nexus 3172PQ` as Leaf Switch for ECMP and HA

### Leaf Switch [LSn]

- for ARP handling and Learning
  - 1. on ethertype=arp copy to Controller, go ahead
   (Controller learns Hnm's mac or do arp response for request on LSn)

- for L3 Routing to Hnm
  - 2. on dst_mac=LSn, dst_ip=LSn, output to Controler, end
   (for LSn router action like icmp echo)
  - 3. on dst_mac=LSn, dst_ip=Hnm, output to Hnm port, dst_mac<-Hnm, src_mac<-LSn, end
  - 4. on dst_mac=LSn, dst_ip=Hn/net, output to Controller, end
   (for Controller to trigger ARP request on ths Hnm_ip unknown)
  - 5. !!! on dst_mac=LSn, dst_ip=unknown, output to SSm (NEED TO SELECT SS) !!!,
   with dst_mac<-SSm, src_mac<-LSn update, end

- for L2 Switching
  - 6. on dst_mac=Hnm, output to Hnm.port, end 
  - 7. on dst_mac=broadcast and port=SSm, drop, end
     to ignore possible flooding from LS-SS port
  - 8. on dst_mac=broadcast, flooding (maybe Hnm.ports only), end

- default
  - 9. drop by default
  - 10. on link up/down event noti to Controller
     if LSn-SSm link down, change LSn rule 4, not to use SSm


### Spine Switch [SSn] (no self subnet and host)

for ARP handling and Learning
1. on ethertype=arp copy to Controller, go ahead
   (Controller learns EHn's mac or do arp response for request on SSn)

for L3 Routing to LSn
2. on dst_mac=SSn, dst_ip=SSn, output to Controler, end
   (for SSn router action like icmp echo)
3. on dst_mac=SSn, dst_ip=Hmx/net, output to LSm port, dst_mac<-LSm, src_mac<-SSn, end
3b. (?if LSm port is down may icmp host unreachable response)

for L3 Routing to External Network
4. on dst_mac=SSn, src_mac!=EHn, output to EHn, dst_mac<-EHn, src_mac<-SSn, end
    (default route to external network)

default
- drop by default
- on link up/down event send noti to Controller
   (?? do not send to linked downed LSn by using LSm alive)


### Assume:
- LSn's Hnm IP range is known by configuration
- LSn's and SSm's mac are known by configuration
  or use Device Info's port MACs for each link


## Configuration

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


## Reference ONOS App

### Critical Applications
- Default device drivers
- Flow Space Analysis App
- Flow specification Device Drivers
- Flowspec API
- Intent Synchronizer
- Host Location Provider (for auto Regi/Deregi Host may be from ARP)
- Network Config Link Provider (for auto Regi/Deregi Links)
- OpenFlow Agent App 
- OpenFlow Provider (for OpenFlow Switch Connect)
- Optical information model (for OpenFlow privider)

### Reference for SLSNET Developement
- Path Visualization App (might be omitted)
- FIB installler App
- Fault Managemnet App
- Proxy ARP/NDP App
- Reactive Forwarding App
- Virtual Router App
- Link Dicovery Provider
- Host Location Provider
- Network Config Host Provider
- LLDB Link Provider
- Network Config Host Provider
- OpenFlow Provider
