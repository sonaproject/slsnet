# OBSOLETE Texts Archive #


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

