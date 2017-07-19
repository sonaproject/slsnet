/*
 * Copyright 2017-present Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onosproject.slsnet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultByteArrayNodeFactory;
import com.googlecode.concurrenttrees.radixinverted.ConcurrentInvertedRadixTree;
import com.googlecode.concurrenttrees.radixinverted.InvertedRadixTree;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.ARP;
import org.onlab.packet.Ethernet;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip6Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.IPv6;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onlab.packet.ndp.NeighborSolicitation;
import org.onosproject.app.ApplicationService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.ListenerRegistry;
import org.onosproject.incubator.component.ComponentService;
import org.onosproject.incubator.net.intf.Interface;
import org.onosproject.incubator.net.intf.InterfaceService;
import org.onosproject.incubator.net.intf.InterfaceListener;
import org.onosproject.incubator.net.intf.InterfaceEvent;
import org.onosproject.incubator.net.routing.Route;
import org.onosproject.incubator.net.routing.RouteAdminService;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.config.basics.SubjectFactories;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.Host;
import org.onosproject.net.host.HostService;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.onosproject.incubator.net.routing.RouteTools.createBinaryString;


/**
 * Reactive routing configuration manager.
 */
@Component(immediate = true)
@Service
public class SlsNetManager extends ListenerRegistry<SlsNetEvent, SlsNetListener>
        implements SlsNetService {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ApplicationService applicationService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigService configService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry registry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected InterfaceService interfaceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    // Route table
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private RouteAdminService routeService;

    // compoents to be activated within SlsNet
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ComponentService componentService;
    private final List<String> components = ImmutableList.of(
        SlsNetNeighbour.class.getName(),
        SlsNetL2NetworkRouting.class.getName(),
        SlsNetReactiveRouting.class.getName(),
        SlsNetBorderRouting.class.getName()
    );

    // SlsNet variables
    private ApplicationId appId = null;

    // l2 broadcast networks
    private Set<L2Network> l2Networks = new HashSet<>();
    private Set<Interface> l2NetworkInterfaces = new HashSet<>();

    // Subnet table
    private Set<IpSubnet> ip4Subnets = new HashSet<>();
    private Set<IpSubnet> ip6Subnets = new HashSet<>();
    private InvertedRadixTree<IpSubnet> ip4SubnetTable =
                 new ConcurrentInvertedRadixTree<>(new DefaultByteArrayNodeFactory());
    private InvertedRadixTree<IpSubnet> ip6SubnetTable =
                 new ConcurrentInvertedRadixTree<>(new DefaultByteArrayNodeFactory());

    // Border Route table
    private Set<Route> borderRoutes = new HashSet<>();

    // VirtialGateway
    private MacAddress virtualGatewayMacAddress;
    private Set<IpAddress> virtualGatewayIpAddresses = new HashSet<>();

    // Listener for Service Events
    private final InternalNetworkConfigListener configListener = new InternalNetworkConfigListener();
    private final InternalHostListener hostListener = new InternalHostListener();
    private final InternalInterfaceListener interfaceListener = new InternalInterfaceListener();

    private ConfigFactory<ApplicationId, SlsNetConfig>
            reactiveRoutingConfigFactory =
            new ConfigFactory<ApplicationId, SlsNetConfig>(
                    SubjectFactories.APP_SUBJECT_FACTORY,
                    SlsNetConfig.class, "slsnet") {
        @Override
        public SlsNetConfig createConfig() {
            return new SlsNetConfig();
        }
    };

    @Activate
    public void activate() {
        log.info("slsnet starting");

        if (appId == null) {
            appId = coreService.registerApplication(APP_ID);
        }

        refreshNetworkConfig(null);

        configService.addListener(configListener);
        registry.registerConfigFactory(reactiveRoutingConfigFactory);
        hostService.addListener(hostListener);

        // delay activation until first CONFIG_REGISTERED event */
        components.forEach(name -> componentService.activate(appId, name));

        log.info("slsnet started");
    }

    @Deactivate
    public void deactivate() {
        log.info("slsnet stopping");

        components.forEach(name -> componentService.deactivate(appId, name));

        hostService.removeListener(hostListener);
        registry.unregisterConfigFactory(reactiveRoutingConfigFactory);
        configService.removeListener(configListener);

        log.info("slsnet stopped");
    }

    // Set up from configuration
    private void refreshNetworkConfig(NetworkConfigEvent event) {
        log.info("slsnet refresh network config: {}", event);
        boolean dirty = false;

        SlsNetConfig config = configService.getConfig(coreService.registerApplication(APP_ID), SlsNetConfig.class);
        if (config == null) {
            //log.warn("No reactive routing config available!");
            return;
        }

        // l2Networks
        Set<L2Network> newL2Networks = new HashSet<>();
        Set<Interface> newL2NetworkInterfaces = new HashSet<>();
        newL2Networks = config.getL2Networks().stream()
                .map(l2NetworkConfig -> {
                    L2Network l2Network = L2Network.of(l2NetworkConfig.name(), l2NetworkConfig.encapsulationType());
                    l2Network.addInterfaceNames(l2NetworkConfig.interfaceNames());
                    // fill up interfaces and Hosts
                    for (String ifaceName : l2NetworkConfig.interfaceNames()) {
                         Interface iface = getInterfaceByName(ifaceName);
                         if (iface != null) {
                             l2Network.addInterface(iface);
                             newL2NetworkInterfaces.add(iface);
                         }
                    }
                    for (Host host : hostService.getHosts()) {
                         if (l2Network.contains((Interface) getHostInterface(host))) {
                             l2Network.addHost(host);
                         }
                    }
                    l2Network.setDirty(true);
                    // update l2Network's dirty flags if same entry already exists
                    for (L2Network prevL2Network : l2Networks) {
                        if (prevL2Network.equals(l2Network)) {
                            l2Network.setDirty(prevL2Network.dirty());
                            break;
                        }
                    }
                    return l2Network;
                }).collect(Collectors.toSet());
        if (!l2Networks.equals(newL2Networks)) {
            l2Networks = newL2Networks;
            dirty = true;
        }
        if (!l2NetworkInterfaces.equals(newL2NetworkInterfaces)) {
            l2NetworkInterfaces = newL2NetworkInterfaces;
            dirty = true;
        }

        // ipSubnets
        Set<IpSubnet> newIp4Subnets = config.ip4Subnets();
        Set<IpSubnet> newIp6Subnets = config.ip6Subnets();
        InvertedRadixTree<IpSubnet> newIp4SubnetTable =
                 new ConcurrentInvertedRadixTree<>(new DefaultByteArrayNodeFactory());
        InvertedRadixTree<IpSubnet> newIp6SubnetTable =
                 new ConcurrentInvertedRadixTree<>(new DefaultByteArrayNodeFactory());
        Set<IpAddress> newVirtualGatewayIpAddresses = new HashSet<>();
        for (IpSubnet subnet : newIp4Subnets) {
            newIp4SubnetTable.put(createBinaryString(subnet.ipPrefix()), subnet);
            newVirtualGatewayIpAddresses.add(subnet.gatewayIp());
        }
        for (IpSubnet subnet : newIp6Subnets) {
            newIp6SubnetTable.put(createBinaryString(subnet.ipPrefix()), subnet);
            newVirtualGatewayIpAddresses.add(subnet.gatewayIp());
        }
        if (!ip4Subnets.equals(newIp4Subnets)) {
            ip4Subnets = newIp4Subnets;
            ip4SubnetTable = newIp4SubnetTable;
            dirty = true;
        }
        if (!ip6Subnets.equals(newIp6Subnets)) {
            ip6Subnets = newIp6Subnets;
            ip6SubnetTable = newIp6SubnetTable;
            dirty = true;
        }
        if (!virtualGatewayIpAddresses.equals(newVirtualGatewayIpAddresses)) {
            virtualGatewayIpAddresses = newVirtualGatewayIpAddresses;
            dirty = true;
        }

        // borderRoutes config handling
        Set<Route> newBorderRoutes = config.borderRoutes();
        if (!borderRoutes.equals(newBorderRoutes)) {
            Set<Route> removeSet = new HashSet<>();
            Set<Route> updateSet = new HashSet<>();
            for (Route route : borderRoutes) {  // check old to be removed
                if (!newBorderRoutes.contains(route)) {
                    removeSet.add(route);
                }
            }
            for (Route route : newBorderRoutes) {  // check old to be removed
                if (!borderRoutes.contains(route)) {
                    updateSet.add(route);
                }
            }
            if (!removeSet.isEmpty()) {
                routeService.withdraw(removeSet);
            }
            if (!updateSet.isEmpty()) {
                routeService.update(updateSet);
            }
            borderRoutes = newBorderRoutes;
            dirty = true;
        }

        // virtual gateway MAC
        MacAddress newVirtualGatewayMacAddress = config.virtualGatewayMacAddress();
        if (virtualGatewayMacAddress == null
            || !virtualGatewayMacAddress.equals(newVirtualGatewayMacAddress)) {
            virtualGatewayMacAddress = newVirtualGatewayMacAddress;
            dirty = true;
        }

        // notify to SlsNet listeners
        if (dirty) {
            log.info("slsnet refresh; notify events");
            process(new SlsNetEvent(SlsNetEvent.Type.SLSNET_UPDATED, this));
        }
    }

    private Interface getInterfaceByName(String interfaceName) {
        Interface intf = interfaceService.getInterfaces().stream()
                          .filter(iface -> iface.name().equals(interfaceName))
                          .findFirst()
                          .orElse(null);
        if (intf == null) {
            log.warn("slsnet unknown interface name: {}", interfaceName);
        }
        return intf;
    }

    @Override
    public ApplicationId getAppId() {
        if (appId == null) {
            appId = coreService.registerApplication(APP_ID);
        }
        return appId;
    }

    @Override
    public Collection<L2Network> getL2Networks() {
        return ImmutableSet.copyOf(l2Networks);
    }

    @Override
    public Set<IpSubnet> getIp4Subnets() {
        return ImmutableSet.copyOf(ip4Subnets);
    }

    @Override
    public Set<IpSubnet> getIp6Subnets() {
        return ImmutableSet.copyOf(ip6Subnets);
    }

    @Override
    public Set<Route> getBorderRoutes() {
        return ImmutableSet.copyOf(borderRoutes);
    }

    @Override
    public MacAddress getVirtualGatewayMacAddress() {
        return virtualGatewayMacAddress;
    }

    @Override
    public Set<IpAddress> getVirtualGatewayIpAddresses() {
        return ImmutableSet.copyOf(virtualGatewayIpAddresses);
    }

    @Override
    public boolean isL2NetworkInterface(Interface intf) {
        return l2NetworkInterfaces.contains(intf);
    }

    @Override
    public L2Network findL2Network(ConnectPoint port, VlanId vlanId) {
        for (L2Network l2Network : l2Networks) {
            if (l2Network.contains(port, vlanId)) {
                return l2Network;
            }
        }
        return null;
    }

    @Override
    public L2Network findL2Network(String name) {
        for (L2Network l2Network : l2Networks) {
            if (l2Network.name().equals(name)) {
                return l2Network;
            }
        }
        return null;
    }

    @Override
    public IpSubnet findIpSubnet(IpAddress ipAddress) {
        if (ipAddress.isIp4()) {
            return (IpSubnet) ip4SubnetTable.getValuesForKeysPrefixing(
                                  createBinaryString(IpPrefix.valueOf(ipAddress, Ip4Address.BIT_LENGTH)))
                              .iterator().next();
        } else {
            return (IpSubnet) ip6SubnetTable.getValuesForKeysPrefixing(
                                  createBinaryString(IpPrefix.valueOf(ipAddress, Ip6Address.BIT_LENGTH)))
                              .iterator().next();
        }
    }

    @Override
    public Interface getHostInterface(Host host) {
        return interfaceService.getInterfaces().stream()
                .filter(iface -> iface.connectPoint().equals(host.location()) &&
                                 iface.vlan().equals(host.vlan()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean isIpAddressLocal(IpAddress ipAddress) {
        boolean result;
        if (ipAddress.isIp4()) {
            return ip4SubnetTable.getValuesForKeysPrefixing(
                     createBinaryString(IpPrefix.valueOf(ipAddress, Ip4Address.BIT_LENGTH)))
                     .iterator().hasNext();
        } else {
            return ip6SubnetTable.getValuesForKeysPrefixing(
                     createBinaryString(IpPrefix.valueOf(ipAddress, Ip6Address.BIT_LENGTH)))
                     .iterator().hasNext();
        }
    }

    @Override
    public boolean isIpPrefixLocal(IpPrefix ipPrefix) {
        if (ipPrefix.isIp4()) {
            return (ip4SubnetTable.getValueForExactKey(createBinaryString(ipPrefix)) != null);
        } else {
            return (ip6SubnetTable.getValueForExactKey(createBinaryString(ipPrefix)) != null);
        }
    }

    @Override
    public boolean isVirtualGatewayIpAddress(IpAddress ipAddress) {
        return virtualGatewayIpAddresses.contains(ipAddress);
    }

    @Override
    public boolean requestMac(IpAddress ip) {
        if (virtualGatewayMacAddress == null) {
            log.warn("slsnet request mac failed for unknown virtualGatewayMacAddress: {}", ip);
            return false;
        }
        IpSubnet ipSubnet = findIpSubnet(ip);
        if (ipSubnet == null) {
            log.warn("slsnet request mac failed for unknown IpSubnet: {}", ip);
            return false;
        }
        L2Network l2Network = findL2Network(ipSubnet.l2NetworkName());
        if (l2Network == null) {
            log.warn("slsnet request mac failed for unknown l2Network name {}: {}",
                     ipSubnet.l2NetworkName(), ip);
            return false;
        }
        log.info("slsnet send request mac L2Network {}: {}", l2Network.name(), ip);
        for (Interface iface : l2Network.interfaces()) {
            Ethernet neighbourReq;
            if (ip.isIp4()) {
                neighbourReq = ARP.buildArpRequest(virtualGatewayMacAddress.toBytes(),
                                                   ipSubnet.gatewayIp().toOctets(),
                                                   ip.toOctets(),
                                                   iface.vlan().toShort());
            } else {
                byte[] soliciteIp = IPv6.getSolicitNodeAddress(ip.toOctets());
                neighbourReq = NeighborSolicitation.buildNdpSolicit(
                                                   ip.toOctets(),
                                                   ipSubnet.gatewayIp().toOctets(),
                                                   soliciteIp,
                                                   virtualGatewayMacAddress.toBytes(),
                                                   IPv6.getMCastMacAddress(soliciteIp),
                                                   iface.vlan());
            }
            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                                               .setOutput(iface.connectPoint().port()).build();
            OutboundPacket packet = new DefaultOutboundPacket(iface.connectPoint().deviceId(),
                                               treatment, ByteBuffer.wrap(neighbourReq.serialize()));
            packetService.emit(packet);
        }
        return true;
    }

    // Service Listeners

    private class InternalNetworkConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            switch (event.type()) {
            case CONFIG_REGISTERED:
            case CONFIG_UNREGISTERED:
            case CONFIG_ADDED:
            case CONFIG_UPDATED:
            case CONFIG_REMOVED:
                if (event.configClass().equals(SlsNetConfig.class)) {
                    refreshNetworkConfig(event);
                }
                break;
            default:
                break;
            }
        }
    }

    private class InternalHostListener implements HostListener {
        @Override
        public void event(HostEvent event) {
            Host host = event.subject();
            Host prevHost = event.prevSubject();
            switch (event.type()) {
            case HOST_MOVED:
            case HOST_REMOVED:
            case HOST_ADDED:
            case HOST_UPDATED:
                refreshNetworkConfig(null);
                break;
            default:
                break;
            }
        }
    }

    private class InternalInterfaceListener implements InterfaceListener {
        @Override
        public void event(InterfaceEvent event) {
            Interface iface = event.subject();
            Interface prevIface = event.prevSubject();
            switch (event.type()) {
            case INTERFACE_ADDED:
            case INTERFACE_REMOVED:
            case INTERFACE_UPDATED:
                // target interfaces are static from netcfg
                //refreshNetworkConfig(null);
                break;
            default:
                break;
            }
        }
    }


}

