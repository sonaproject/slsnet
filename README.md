# Simple Leaf-Spine Network Application
Lee Yongjae, 2017-06-15,08-03.



## SONA Fabric 설계

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
- 시스코 스위치가 OpenFlow 1.3을 지원하지만 multi-table은 지원하지 않고 single table만 지원할 가능성이 높음 -- 이 경우 ECMP 지원은 불가능함
 

## Cisco Switch

Switch: Nexus 9000 Series C9372PX
- Leaf Switch: 10Gs + 40G x 6 (2 for Spine and 4 for Storage)
- Spine Switch: Maybe 40G

Configuration
```txt
!Command: show running-config
!Time: Thu Aug  3 07:37:51 2017

version 7.0(3)I6(1)
switchname LEAF-1
vdc LEAF-1 id 1
  limit-resource vlan minimum 16 maximum 4094
  limit-resource vrf minimum 2 maximum 4096
  limit-resource port-channel minimum 0 maximum 511
  limit-resource u4route-mem minimum 248 maximum 248
  limit-resource u6route-mem minimum 96 maximum 96
  limit-resource m4route-mem minimum 58 maximum 58
  limit-resource m6route-mem minimum 8 maximum 8

onep
feature openflow

username admin password 5 $5$yzg6Ajmu$kSFxPBlRxABu2D5IwPaSyGAWIeo5gIDRQUex4PBXCc8  role network-admin
ip domain-lookup
spanning-tree mode mst
copp profile strict
snmp-server user admin network-admin auth md5 0xa35b241e98ebe6c85b124d37048ac87e priv 0xa35b241e98ebe6c85b124d37048ac87e localizedkey
rmon event 1 description FATAL(1) owner PMON@FATAL
rmon event 2 description CRITICAL(2) owner PMON@CRITICAL
rmon event 3 description ERROR(3) owner PMON@ERROR
rmon event 4 description WARNING(4) owner PMON@WARNING
rmon event 5 description INFORMATION(5) owner PMON@INFO

vlan 1-2048

no cdp enable
vrf context management
  ip route 0.0.0.0/0 10.10.1.1
hardware access-list tcam region racl 0
hardware access-list tcam region e-racl 0
hardware access-list tcam region l3qos 0
hardware access-list tcam region span 0
hardware access-list tcam region redirect 0
hardware access-list tcam region vpc-convergence 0
hardware access-list tcam region openflow 1024
openflow
  switch 1 pipeline 202
    rate-limit packet_in 1 burst 4
    statistics collection-interval 10
    datapath-id 0x111111
    controller ipv4 10.10.108.140 port 6653 vrf management security none
    of-port interface Ethernet1/1
    of-port interface Ethernet1/2
    of-port interface Ethernet1/3
    of-port interface Ethernet1/4
    default-miss continue-normal
    protocol-version 1.3
    logging flow-mod
```


## Topology

<table>
<tr><td>
Network Diagram
</td><td>
Mininet Model: <a href="mininet-slsnet.py"><code>mininet-slsnet.py</code></a>
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
  [ss1(10.0.0.1/24)]   [ss2(10.0.0.2/24)]
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

## SLSNET Application


### ONOS Core Source Patch for Cisco Issue

apply [onos.patch](onos.patch) and rebuild onos


### SLSNET App Build, Install and Activate

in `onos-app-slsnet` directory

BUILD:
- `mvn clean compile install`

INSTALL TO ONOS AND ACTIVATE APP:
- `onos-app localhost install target/onos-app-slsnet-1.11.0-SNAPSHOT.oar`
- `onos-app localhost activate org.onosproject.slsnet`

Following app are auto activated by SLSNET app's dependency
- Intent Synchronizer
- OpenFlow Provider (for OpenFlow Controller) --> Optical inforamtion model
- Host Location Provider (for auto regi host from ARP)
- Network Config Link Provider (for auto Regi/Deregi Links)


### ONOS Network Configuration

SEE: [network-cfg.json](network-cfg.json)

설정 항목
- devices : 유효한 device 목록
- ports : 유효한 한 port 목록; interface name을 지정하여 l2Network 구성시 사용
- app : slsnet
  - l2Network : ipSubnet 을 할달할 물리적 L2 Network에 속하는 Interface 정보
     - interfaces : l2Network 에 속하는 ports의 interface name 들을 지정
     - l2Forward : false 로 지정하면 L2Forwarding 관련 Intents 생성을 차단 (Cisco용)
  - ipSubnet : Local IP Subnet 정보
     - gatewayIp : 해당 subnet에서의 virtual gateway ip 를 지정
     - l2NetworkName : 해단 subnet 이 속해 있는 l2Network 을 지정
  - borderRoute : 외부로 나가는 Route 정보
     - gatewayIp : 외부 peer측 gateway의 ip; 내부 peer측은 이 gatewayIp 가 속하는 ipSubnet의
       virtual gateway ip 가 사용됨
  - virtualGatewayMacAddress : virtual gateway의 공통 mac address

적용 방법
- to update: `onos-netcfg localhost network-cfg.json`
  - each call updates loaded network config (onos netcfg to see loaded config)
  - updated values are immediately applied to existing entries
- to clean: `onos-netcfg localhost delete`
- to be applied at onos restarts, copy `network-cfg.json` to `${ONOS_HOME}/config/`


### Cisco OpenFlow 기능의 제약

- Selector 에 L2 Src/Dst MAC 을 사용할 수 없음
   - L2 Forwarding 을 구성할 수 없고, IP 조건식만 사용해야함
   - 기존적으로 사용되는 IntentCompiler 인 LinkCollectionCompiler 에서 
     각 Hop 단계에서의 Treatment 를 기준으로 다음 단계에서 Selector를 사용하는데, 문제가됨

- Instruction 에서 PushVlan 을 사용할 수 없음
   - Intents 에서 EncapsulationType.VLAN 을 사용할 수 없음
   - L2Mac 및 PushVlan 문제로, 기본 IntentCompiler LinkCollectopnCompiler 를 수정하여
     최초의 Selector를 모든 단계에서 사용하도록 코드 수정이 필요 (1줄)

- Instruction 에서 ClearDiffered 를 사용할 수 없음
   - FlowObjective 를 사용하는 경우에 문제가 되는데, 기본적으 PacketService 등록시 해당 기능이 사용됨
   - OpenFlow Pineline Driver 에서 해당 Operation을 빼도록 Driver를 수정해야 함.

- Single Table
   - Cisco Pipeline 202 에서는 테이블을 분리하여 사용할 수 있는 것 처럼 나와 있으나, 안됨.

- Selecttor 에서 Switch 단위로 IPv4 또는 IPv6 중 한가지만 사용할 수 있음
   - 스위치 설정 중 `hardware access-list tcam region openflow 1024`
     대신 `hardware access-list tcam region openflow-ipv6 1024`
     를 사용하며 IPv6 로만 동작함


### 구현된 기능

- L2 Network Forwarding
  - Cisco 에서는 동작 불가

- Neighbour Message Handling
  - Host간 ARP 전달 및 Virtual Gateway IP 에 대한 ARP 응답 처리
  - Virtual Gateway IP 에 대한 ICMP ECHO (ping) 요청에 대한 응답 처리

- L3 Reactive Routing
  - Subnet 내부 IP 통신
  - Local Subnet 간 IP 통신
  - Local Subnet - External Router 가 Route 에 따른 IP 통신을 모두 Reactive 방식으로 처리
