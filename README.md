### Simple Leaf-Spine Network Application
Lee Yongjae, 2017-06-15.


Info from SKT
--------
- 10G Server x 9
- 40G Storage x 4
- Leaf Switch: 10Gs + 40G x 6 (2 for Spine and 4 for Storage)
  ( may be Cisco Nexus 3172PQ
    http://www.cisco.com/c/en/us/td/docs/switches/datacenter/nexus/openflow/b_openflow_agent_nxos_1_3/Cisco_Plug_in_for_OpenFlow.html )
- Spine Switch: Maybe 40G
- Do ECMP and Link Failover

Topology
--------

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

1. LSn and Hn* are in same subnet Nn
2. LSn acts as L2 switch for Nn and L3 Subnet Router for Hn*  
3. SSn acts as inter-Subnet L3 Router for LSns and Use EH1 as Default Router
  - consider Network Config Host Provider
  - consider Network Config Link Provider


Features (OBSOLETE BY Simplified Features)
--------

Leaf Switch [LSn]
1. L2 Unicast Handling for Hn* (Output to Learned Port or Flood)
2. L2 Broadcast Handling
3. ARP Learning for Hn* MAC and IP Learning
4. Handle ARP Request/Response for LSn's IP as Hn*'s Subnet Gateway
   - consider Proxy ARP/NDP App
5. on dst_mac=LSn, L3 Route to Hn* (src_mac<-LSn, dst_mac<=Hn)
6. on dst_mac=LSn, L3 Route to SS* for non-Subnet IPs (src_mac<-LSn, dst_mac=SS*)
   with Load Balancing on SS*

Spine Switch [SSn]
1. Do ARP Request on EH1 IP and Learn EH1 Mac and IP from ARP response
2. Handle ARP Request/Response for SSn's IP for EH1's Request
3. on dst_mac=SSn, L3 Route to LS* for each subnets (src_mac<-SSn,dst_mac<-LS*)
4. on dst_mac=SSn, L3 Route to EH1 as defaut route (src_mac<-SSn,dst_mac<-EHn)

High Avaliablility [LSn and SSn]
1. on SSn-LSm link failed, SSn forward to other SS with SSm link available via LS*
2. on SSn-LSm link failed, LS* forward to other SS with SSm link available
3. on SSn-EH1 link failed, SSn forward to other SS EH1 link available via LS*
4. on SSn-EH1 link failed, LS* forward to other SS EH1 link available


Simplified Features for Cisco Nexus 3172PQ as Leaf Switch for ECMP and HA
--------

Leaf Switch [LSn]

1. on ethertype=arp copy to Controller, go ahead
   (Controller learns Hn*'s mac or do arp response for request on LSn)

2. on dst_mac=LSn, dst_ip=LSn, output to Controler, end
   (for LSn router action like icmp echo)
3. on dst_mac=LSn, dst_ip=Hn*, output to Hn* port, dst_mac<-Hn*, src_mac<-LSn, end
3b. on dst_mac=LSn, dst_ip=Hn/net, output to Controller, end
   (for Controller to trigger ARP request on ths Hn*_ip unknown)
4. !!! on dst_mac=LSn, dst_ip=unknown, output to SS* (NEED TO SELECT SS) !!!,
   with dst_mac<-SS*, src_mac<-LSn update, end

5. on dst_mac=Hn*, output to Hn*.port, end 
6. on dst_mac=broadcast and port=SS*, drop, end
   to ignore possible flooding from LS-SS port
7. on dst_mac=broadcast, flooding (maybe Hn*.ports only), end

- drop by default
- on link up/down event noti to Controller
   if LSn-SSm link down, change LSn rule 4, not to use SSm


Spine Switch [SSn] (no self subnet and host)

1. on ethertype=arp copy to Controller, go ahead
   (Controller learns EHn's mac or do arp response for request on SSn)

2. on dst_mac=SSn, dst_ip=SSn, output to Controler, end
   (for SSn router action like icmp echo)
3. on dst_mac=SSn, dst_ip=Hm*/net, output to LSm port, dst_mac<-LSm, src_mac<-SSn, end
3b. (?if LSm port is down may icmp host unreachable response)

4. on dst_mac=SSn, src_mac!=EH*, output to EHn, dst_mac<-EHn, src_mac<-SSn, end
    (default route to external network)

- drop by default
- on link up/down event send noti to Controller
   (?? do not send to linked downed LSn by using LSm alive)


Assume:
- LS* 's H* IP range is known by configuration
- LS* and SS*'s mac are known by configuration


Configuration
--------

- <per LS Device ID>
  { vIP, vMAC, <ports for SS*>, <ports for H*> }
- <per SS Device ID>
  { vIP, vMAC, <ports for LS*>, <port for EH*> }
- consider <ports for SS* and LS*> MAY BE auto detected using ONOS Application:
  Link Discovery Provider 


Reference ONOS App
--------

Critical Applications
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



Note
--------
1. LS* and SS* 's IP and MAC is set by Configuration
   (might be learned from Device info, but use config value for now)
2. LSn is mapped 1:1 to subnet 
   (do not consider 1:n or n:1 case for now)
3. Assume OpenFlow switch with constraints:
   - 1 flow table only
   - no group table
   - Packet In/Out are available


ONOS
--------
1. Application to RUN
  - Link Discovery Provider
  - OpenFlow Agent
  - ???





