# Simple Leaf-Spine Network Application
Lee Yongjae, 2017-06-15,08-10.



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

Switch: Nexus 9000 Series
- Spine Switch: N9K-C9332PQ 
- Leaf Switch: N9K-C9372PX-E


Configuration
```txt
feature openflow

no cdp enable

hardware access-list tcam region racl 0
hardware access-list tcam region e-racl 0
hardware access-list tcam region span 0
hardware access-list tcam region redirect 0
hardware access-list tcam region ns-qos 0
hardware access-list tcam region ns-vqos 0
hardware access-list tcam region ns-l3qos 0
hardware access-list tcam region vpc-convergence 0
hardware access-list tcam region rp-qos 0
hardware access-list tcam region rp-ipv6-qos 0
hardware access-list tcam region rp-mac-qos 0
hardware access-list tcam region openflow 512 double-wide

openflow
  switch 1 pipeline 203
    statistics collection-interval 10
    datapath-id 0x123 4
    controller ipv4 10.10.108.140 port 6653 vrf management security none
    of-port interface Ethernet1/1
    of-port interface Ethernet1/2
    of-port interface Ethernet1/3
    of-port interface Ethernet1/4
    protocol-version 1.3
```

N9K-C9332PQ 의 40G Port는 switchport 를 지정해야 vlan 1에 소속되어 정상처리됨
```txt
  interface Ethernet1/31
  switchport
  mode openflow
  no shutdown

  interface Ethernet1/32
  switchport
  mode openflow
  no shutdown
```

Hardware Features
```txt
leaf2# show openflow hardware capabilities pipeline 201

  Max Interfaces: 1000
  Aggregated Statistics: NO

  Pipeline ID: 201
    Pipeline Max Flows: 3001
    Max Flow Batch Size: 300
    Statistics Max Polling Rate (flows/sec): 1024
    Pipeline Default Statistics Collect Interval: 7

    Flow table ID: 0

    Max Flow Batch Size: 300
    Max Flows: 3001
    Bind Subintfs: FALSE                              
    Primary Table: TRUE                               
    Table Programmable: TRUE                               
    Miss Programmable: TRUE                               
    Number of goto tables: 0                                  
    goto table id:      
    Stats collection time for full table (sec): 3

    Match Capabilities                                  Match Types
    ------------------                                  -----------
    ethernet mac destination                            optional    
    ethernet mac source                                 optional    
    ethernet type                                       optional    
    VLAN ID                                             optional    
    VLAN priority code point                            optional    
    IP DSCP                                             optional    
    IP protocol                                         optional    
    IPv4 source address                                 lengthmask  
    IPv4 destination address                            lengthmask  
    source port                                         optional    
    destination port                                    optional    
    in port (virtual or physical)                       optional    
    wildcard all matches                                optional    

    Actions                                             Count Limit             Order
    specified interface                                     64                    20
    controller                                               1                    20
    divert a copy of pkt to application                      1                    20

    set eth source mac                                       1                    10
    set eth destination mac                                  1                    10
    set vlan id                                              1                    10

    pop vlan tag                                             1                    10

    drop packet                                              1                    20


    Miss actions                                        Count Limit             Order
    use normal forwarding                                    1                     0
    controller                                               1                    20

    drop packet                                              1                    20
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
- BUILD:
   - `mvn clean compile install`
- INSTALL TO ONOS AND ACTIVATE APP:
   - `onos-app localhost install target/onos-app-slsnet-1.11.0-SNAPSHOT.oar`
   - `onos-app localhost activate org.onosproject.slsnet`

Or use [install_slsnet.sh](install.slsnet.sh) script in `slsnet` directory
- Assuming
   - onos sources are located in ../onos
   - onos is installed at /opt/onos-1.11.0-SNAPSHOT and /opt/onos is symbolic link to it
   - system is Redhat or CentOS and controllable with `service onos [start|stop]` command
- `./install_slsnet.sh -r` to reinistall ONOS from `../onos/buck-out/gen/tools/package/onos-package/onos.tar.gz`
- `./install_slsnet.sh [netcfg-json-file]` to 
   - rebuild SLSNET app
   - install and activate SLSNET on onos
   - install the network config json file (default: network-cfg.json) to /opt/onos/config/ 
   - restart ONOS to apply new SLSNET app and network config

Following app are auto activated by SLSNET app's dependency
- OpenFlow Provider (for OpenFlow Controller) --> Optical inforamtion model
- LLDP Link Provider (for auto Regi/Deregi Links)
- Host Location Provider (for auto regi host from ARP)

If onos is updated, apply update for external app maven build, at onos/ source directory
- `onos-buck-publish-local`
- ~~`mcis` or `mvn clean install -DskipTests -Dcheckstyle.skip`(2017-08-16 버전 ONOS에서는 필요 없음)~~

### ONOS Network Configuration

설정 파일
- Mininet Test: [network-cfg.json](network-cfg.json)
- 분당 Testbed: [bundang-cfg.json](bundang-cfg.json)

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

- Instruction 에서 ClearDiffered 를 사용할 수 없음
   - FlowObjective 를 사용하는 경우에 문제가 되는데, 기본적으 PacketService 등록시 해당 기능이 사용됨
   - OpenFlow Pineline Driver 에서 해당 Operation을 빼도록 Driver를 수정해야 함.

- Selecttor 에서 Switch 단위로 IPv4 또는 IPv6 중 한가지만 사용할 수 있음
   - 스위치 설정 중 `hardware access-list tcam region openflow 1024`
     대신 `hardware access-list tcam region openflow-ipv6 1024`
     를 사용하며 IPv6 로만 동작함

- Selector 에 L2 Src/Dst MAC 을 사용할 수 없음 (삭제: 2017-08-07)
   - L2 Forwarding 을 구성할 수 없고, IP 조건식만 사용해야함
   - 기존적으로 사용되는 IntentCompiler 인 LinkCollectionCompiler 에서 
     각 Hop 단계에서의 Treatment 를 기준으로 다음 단계에서 Selector를 사용하는데, 문제가됨
   - --> tcam 설정시 double-wide 를 설정하면 됨: `hardware access-list tcam region openflow 1024 double-wide`

- Instruction 에서 PushVlan 을 사용할 수 없음
   - Intents 에서 EncapsulationType.VLAN 을 사용할 수 없음
   - L2Mac 및 PushVlan 문제로, 기본 IntentCompiler LinkCollectopnCompiler 를 수정하여
     최초의 Selector를 모든 단계에서 사용하도록 코드 수정이 필요 (1줄)
   - --> Selector Mac 제약이 없어졌으므로, 관련 코드 수정 불필요 (2017-08-07)

- Single Table
   - Cisco Pipeline 202 에서는 테이블을 분리하여 사용할 수 있는 것 처럼 나와 있으나, 안됨.
   - --> tcam double-wide 설정 관련하여 변동 사항이 있는지 확인 필요 (2017-08-07)



### 구현된 기능

- L2 Network Forwarding
  - L2 Network 내의 Broadcast 메시지 전송
  - L2 Network 내의 Dst Mac 기준 메시지 전송

- Neighbour Message Handling
  - Host간 ARP 전달 및 Virtual Gateway IP 에 대한 ARP 응답 처리
  - Virtual Gateway IP 에 대한 ICMP ECHO (ping) 요청에 대한 응답 처리

- L3 Reactive Routing
  - Subnet 내부 IP 통신 (L2 Network Forwarding 에서 처리되는 경우 비활성화)
  - Local Subnet 간 IP 통신
  - Local Subnet - External Router 가 Route 에 따른 IP 통신을 모두 Reactive 방식으로 처리


### 분당 TB 에서의 증상 (Cisco 스위치 적용시의 증상)


- Link 장애 테스트 (cleared)
  - Leaf switch 에서 spine switch 방향 port를 down 시키면, 다른쪽 spine switch 쪽으로 즉시 우회됨
     - down 시켰던 port 를 up 시키면 십수초(Cisco Switch에서의 Link Up 시간인 듯) 후 원래 방향쪽으로 돌아옴
     - 우회로가 있는 상태에서는 intents framework 이 잘 동작하는 것으로 보임
  - Leaf switch 에서 spine switch 뱡향 port 2개를 모두 down 시키면, 관련 intents들이 withdrawn 으로 전환됨
     - link가 죽어도 Reactive Routing 기능의 packet forwarding 에 의해, 트래픽 전달이 수행됨 (RRT=3ms 이상)
     - link를 다시 살려놓아도 installed 상태로 돌아오지 않음 !!!
     - slsnet 에서 routeIntents 로 관리하던 항목이 withdrawn 으로 바뀌면, routeIntents 에서 삭제하고 purge 시키는 기능 필요
       - 해당 기능을 추가하고, SlsNetManager에서 idle event 시 refresh() 먼저 확인하고,
       - 각 sub 모듈의 의 idle event 처리시에도 refresh를 수행하도록 변경
     - --> FAIL 된 intents는 remove 되고, 이후 요청 처리시 정상적으로 복구됨 (2017-08-16)


- subnet간 통신이 안됨 (cleared)
  - flow rule 까지 적용된 것으로 보이나, 통신은 안되는 듯
  - Leaf->Spine 은 되나, Spine->Leaf 전송이 안됨
  - Spine Switch (N9K-C9332PQ) Port에 switchport 를 지정해야 vlan 1에 소속되고 정상 처리됨 (2017-08-11)
    (참고: 아마도 "An ALE 40G trunk port sends tagged packets on the native VLAN of the port. Normally, untagged packets are sent on the native VLAN" 관련일 듯;  https://www.cisco.com/c/en/us/td/docs/switches/datacenter/nexus9000/sw/ale_ports/b_Limitations_for_ALE_Uplink_Ports_on_Cisco_Nexus_9000_Series_Switches.html)

```txt
  interface Ethernet1/31
  switchport
  mode openflow
  no shutdown

  interface Ethernet1/32
  switchport
  mode openflow
  no shutdown
```

- CONTROLLER 로의 패킷 Forwarding 이 매우 느리게 나타남 (cleared)
  - virtual gateway ip 로의 ping의 지연이 심함 (200ms~2000ms, hosts unreachable)
  - 이와 관련하여, Host의 ARP 메시지 발생시 관련 전송에 심한 지연이 나타남 (700~1700ms)
  - 지연이 있거나 drop 이 있는 듯
  - ** --> rate-limit 을 꺼야 함 ** (2017-08-10)

