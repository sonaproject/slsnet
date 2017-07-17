/*
 * Copyright 2015-present Open Networking Laboratory
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

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.ARP;
import org.onlab.packet.EthType;
import org.onlab.packet.Ethernet;
import org.onlab.packet.ICMP;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.IPv6;
import org.onlab.packet.Ip6Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onlab.packet.ndp.NeighborSolicitation;
import org.onosproject.incubator.net.intf.Interface;
import org.onosproject.incubator.net.intf.InterfaceService;
import org.onosproject.incubator.net.routing.Route;
import org.onosproject.incubator.net.routing.RouteService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.Host;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.host.HostService;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
//import org.onosproject.net.packet.PacketPriority;
import org.onosproject.intentsync.IntentSynchronizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.nio.ByteBuffer;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.onlab.packet.Ethernet.TYPE_IPV4;
import static org.onosproject.net.packet.PacketPriority.REACTIVE;


/**
 * Specifies the type of an IP address or an IP prefix location.
 */
enum LocationType {
    LOCAL,    // The location of an IP address or an IP prefix is in local SDN network.
    INTERNET, // The location of an IP address or an IP prefix is outside local SDN network.
    NO_ROUTE  // There is no route for this IP address or IP prefix.
}

/**
 * Specifies the type of traffic.
 * <p>
 * We classify traffic by the first packet of each traffic.
 * </p>
 */
enum TrafficType {
    HOST_TO_INTERNET,
    INTERNET_TO_HOST,
    HOST_TO_HOST,
    INTERNET_TO_INTERNET,
    DROP,    // Any traffic wants to communicate with a destination which has no route
    UNKNOWN  // Traffic does not belong to the types above.
}

/**
 * This is reactive routing to handle 3 cases:
 * (1) one host wants to talk to another host, both two hosts are in
 * SDN network.
 * (2) one host in SDN network wants to talk to another host in Internet.
 * (3) one host from Internet wants to talk to another host in SDN network.
 */
@Component(immediate = true, enabled = false)
public class SlsNetReactiveRouting {

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected RouteService routeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected IntentSynchronizationService intentSynchronizer;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected SlsNetService slsnet;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected InterfaceService interfaceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    private final InternalSlsNetListener slsnetListener =
            new InternalSlsNetListener();

    private SlsNetReactiveRoutingIntent intentRequestListener;

    private ReactiveRoutingProcessor processor =
            new ReactiveRoutingProcessor();

    // last EthDst MAC selector for intercept selector
    private MacAddress selectorDstMacAddress = null;


    @Activate
    public void activate() {
        log.info("slsnet reactive routing starting");

        slsnet.addListener(slsnetListener);

        intentRequestListener = new SlsNetReactiveRoutingIntent(slsnet.getAppId(), hostService,
                interfaceService, intentSynchronizer);

        packetService.addProcessor(processor, PacketProcessor.director(2));
        refreshIntercepts();

        log.info("slsnet reactive routing started");
    }

    @Deactivate
    public void deactivate() {
        log.info("slsnet reactive routing stopping");

        slsnet.removeListener(slsnetListener);
        withdrawIntercepts();

        packetService.removeProcessor(processor);
        processor = null;

        log.info("slsnet reactive routing stopped");
    }

    /**
     * Request packet in via the PacketService.
     */
    private void refreshIntercepts() {
        // TODO: local ipSubnet intercepts
        // TODO: to support IPv6 later
        // default intercepts
        MacAddress newSelectorDstMacAddress = slsnet.getVirtualGatewayMacAddress();
        if (selectorDstMacAddress == null || !selectorDstMacAddress.equals(newSelectorDstMacAddress)) {
            if (selectorDstMacAddress != null) {
                withdrawIntercepts();
            }
            if (newSelectorDstMacAddress != null) {
                TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
                selector.matchEthType(TYPE_IPV4);
                selector.matchEthDst(newSelectorDstMacAddress);
                packetService.requestPackets(selector.build(), REACTIVE, slsnet.getAppId());
                log.info("slsnet reactive routing IPV4 intercepts packet started: EthDst={}",
                         newSelectorDstMacAddress);
            }
            selectorDstMacAddress = newSelectorDstMacAddress;
        }
    }

    /**
     * Cancel request for packet in via PacketService.
     */
    private void withdrawIntercepts() {
        // TODO: local ipSubnet intercepts
        // default intercepts
        if (selectorDstMacAddress != null) {
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
            selector.matchEthType(TYPE_IPV4);
            selector.matchEthDst(selectorDstMacAddress);
            packetService.cancelPackets(selector.build(), REACTIVE, slsnet.getAppId());
            log.info("slsnet reactive routing IPV4 intercepts packet stopped: EthDst={}", selectorDstMacAddress);
            selectorDstMacAddress = null;
        }
    }

    private class ReactiveRoutingProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();
            if (ethPkt == null) {
                return;
            }
            ConnectPoint srcCp = pkt.receivedFrom();

            switch (EthType.EtherType.lookup(ethPkt.getEtherType())) {
            case IPV4:
                // Parse packet
                IPv4 ipv4Packet = (IPv4) ethPkt.getPayload();
                IpAddress dstIp = IpAddress.valueOf(ipv4Packet.getDestinationAddress());
                IpAddress srcIp = IpAddress.valueOf(ipv4Packet.getSourceAddress());
                MacAddress srcMac = ethPkt.getSourceMAC();

                // check and do icmp echo response on virtual gateway ip
                if (ethPkt.getDestinationMAC().equals(slsnet.getVirtualGatewayMacAddress())
                    && slsnet.isVirtualGatewayIpAddress(dstIp)) {
                    if (ipv4Packet.getProtocol() == IPv4.PROTOCOL_ICMP) {
                       ICMP icmpPacket = (ICMP) ipv4Packet.getPayload();
                       if (icmpPacket.getIcmpType() == ICMP.TYPE_ECHO_REQUEST) {
                           log.info("slsnet reactive routing IPV4 ICMP ECHO request to virtual gateway: "
                                    + "srcIp={} dstIp={} proto={}", srcIp, dstIp, ipv4Packet.getProtocol());
                           TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                                   .setOutput(pkt.receivedFrom().port()).build();
                           OutboundPacket packet =
                           new DefaultOutboundPacket(pkt.receivedFrom().deviceId(), treatment,
                                   ByteBuffer.wrap(icmpPacket.buildIcmpReply(ethPkt).serialize()));
                           packetService.emit(packet);
                       }
                    } else {
                       log.warn("slsnet reactive routing IPV4 packet to virtual gateway dropped: "
                                + "srcIp={} dstIp={} proto={}", srcIp, dstIp, ipv4Packet.getProtocol());
                    }
                    break;
                }

                log.info("slsnet reactive routing IPV4 packet detected: srcIp={} dstIp={}", srcIp, dstIp);
                packetReactiveProcessor(dstIp, srcIp, srcCp, srcMac);

                forwardPacketToDstIp(context, dstIp);
                break;
            default:
                break;
            }
        }
    }

    /**
     * Routes packet reactively.
     *
     * @param dstIp the destination IP address of a packet
     * @param srcIp the source IP address of a packet
     * @param srcCp the connect point where a packet comes from
     * @param srcMacAddress the source MAC address of a packet
     */
    private void packetReactiveProcessor(IpAddress dstIp, IpAddress srcIp,
                                        ConnectPoint srcCp,
                                        MacAddress srcMacAddress) {
        checkNotNull(dstIp);
        checkNotNull(srcIp);
        checkNotNull(srcCp);
        checkNotNull(srcMacAddress);

        // Step1: Try to update the existing intent first if it exists.
        IpPrefix ipPrefix = null;
        Route route = null;
        if (slsnet.isIpAddressLocal(dstIp)) {
            if (dstIp.isIp4()) {
                ipPrefix = IpPrefix.valueOf(dstIp, Ip4Address.BIT_LENGTH);
            } else {
                ipPrefix = IpPrefix.valueOf(dstIp, Ip6Address.BIT_LENGTH);
            }
        } else {
            // This Should not happen for SlsNetRoute should already register intents for this case.
            // Get IP prefix from route table
            log.warn("slsnet reactive routing incorrect case for route to non-local: srcCp={} srcIp={} dstIp={}",
                     srcCp, srcIp, dstIp);
            route = routeService.longestPrefixMatch(dstIp);
            if (route != null) {
                ipPrefix = route.prefix();
            }
        }
        if (ipPrefix != null && intentRequestListener.mp2pIntentExists(ipPrefix)) {
            log.info("slsnet reactive routing update mp2p intent: dstIp={} srcCp={}", ipPrefix, srcCp);
            intentRequestListener.updateExistingMp2pIntent(ipPrefix, srcCp);
            return;
        }

        //
        // Step2: There is no existing intent for the destination IP address.
        // Check whether it is necessary to create a new one. If necessary then
        // create a new one.
        //
        TrafficType trafficType = trafficTypeClassifier(srcCp, dstIp);

        switch (trafficType) {
        case HOST_TO_INTERNET:
            // If the destination IP address is outside the local SDN network.
            // The Step 1 has already handled it. We do not need to do anything here.
            intentRequestListener.setUpConnectivityHostToInternet(srcIp,
                    ipPrefix, route.nextHop());
            break;
        case INTERNET_TO_HOST:
            intentRequestListener.setUpConnectivityInternetToHost(dstIp);
            break;
        case HOST_TO_HOST:
            intentRequestListener.setUpConnectivityHostToHost(dstIp,
                    srcIp, srcMacAddress, srcCp);
            break;
        case INTERNET_TO_INTERNET:
            log.trace("This is transit traffic, "
                    + "the intent should be preinstalled already");
            break;
        case DROP:
            // TODO here should setUpDropPacketIntent(...);
            // We need a new type of intent here.
            break;
        case UNKNOWN:
            log.trace("This is unknown traffic, so we do nothing");
            break;
        default:
            break;
        }
    }

    /**
     * Classifies the traffic and return the traffic type.
     *
     * @param srcCp the connect point where the packet comes from
     * @param dstIp the destination IP address in packet
     * @return the traffic type which this packet belongs to
     */
    private TrafficType trafficTypeClassifier(ConnectPoint srcCp, IpAddress dstIp) {
        LocationType dstIpLocationType = getLocationType(dstIp);

        Optional<Interface> srcInterface =
                interfaceService.getInterfacesByPort(srcCp).stream().findFirst();

        Set<String> borderInterfaces = slsnet.getBorderInterfaces();

        switch (dstIpLocationType) {
        case INTERNET:
            if (srcInterface.isPresent() &&
                    (!borderInterfaces.contains(srcInterface.get().name()))) {
                return TrafficType.HOST_TO_INTERNET;
            } else {
                return TrafficType.INTERNET_TO_INTERNET;
            }
        case LOCAL:
            if (srcInterface.isPresent() &&
                    (!borderInterfaces.contains(srcInterface.get().name()))) {
                return TrafficType.HOST_TO_HOST;
            } else {
                // TODO Currently we only consider local public prefixes.
                // In the future, we will consider the local private prefixes.
                // If dstIpLocationType is a local private, we should return
                // TrafficType.DROP.
                return TrafficType.INTERNET_TO_HOST;
            }
        case NO_ROUTE:
            return TrafficType.DROP;
        default:
            return TrafficType.UNKNOWN;
        }
    }

    /**
     * Evaluates the location of an IP address and returns the location type.
     *
     * @param ip the IP address to evaluate
     * @return the IP address location type
     */
    private LocationType getLocationType(IpAddress ip) {
        if (slsnet.isIpAddressLocal(ip)) {
            return LocationType.LOCAL;
        } else if (routeService.longestPrefixMatch(ip) != null) {
            return LocationType.INTERNET;
        } else {
            return LocationType.NO_ROUTE;
        }
    }

    /**
     * Emits the specified packet onto the network.
     *
     * @param context      the packet context
     * @param dstIp the destination IP address to evaluate
     */
    private void forwardPacketToDstIp(PacketContext context, IpAddress dstIp) {
        if (!slsnet.isIpAddressLocal(dstIp)) {
            Route route = routeService.longestPrefixMatch(dstIp);
            if (route == null) {
                log.warn("slsnet reactive routing forward packet route to dstIp unknown: dstIp={}", dstIp);
                return;
            }
            dstIp = route.nextHop();
        }
        Set<Host> hosts = hostService.getHostsByIp(dstIp);
        Host dstHost;
        if (!hosts.isEmpty()) {
            dstHost = hosts.iterator().next();
        } else {
            // NOTE: hostService.requestMac(dstIp); NOT IMPLEMENTED in ONOS HostManager.java
            log.warn("slsnet reactive routing forward packet dstIp host_mac unknown: dstIp={}", dstIp);
            hostService.startMonitoringIp(dstIp);
            requestMac(dstIp);
            return;
        }
        log.info("slsnet reactive routing forward packet: dstHost={} packet={}", dstHost, context);
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setEthDst(dstHost.mac())
                .setOutput(dstHost.location().port()).build();
        OutboundPacket packet =
                new DefaultOutboundPacket(dstHost.location().deviceId(), treatment,
                                          context.inPacket().unparsed());
        packetService.emit(packet);
    }

    /**
     * Send Neighbour Query to Find Host Location.
     *
     * @param ip    the ip address to resolve
     */
    private void requestMac(IpAddress ip) {
        IpSubnet ipSubnet = slsnet.findIpSubnet(ip);
        if (ipSubnet == null) {
            log.warn("slsnet reactive routing request mac failed for unknown IpSubnet: {}", ip);
            return;
        }
        L2Network l2Network = slsnet.findL2Network(ipSubnet.l2NetworkName());
        if (l2Network == null) {
            log.warn("slsnet reactive routing request mac failed for unknown l2Network name {}: {}",
                     ipSubnet.l2NetworkName(), ip);
            return;
        }
        log.info("slsnet reactive routing send neighbor mac requests to L2Network {}: {}", l2Network.name(), ip);
        for (Interface iface : l2Network.interfaces()) {
            Ethernet neighbourReq;
            if (ip.isIp4()) {
                neighbourReq = ARP.buildArpRequest(slsnet.getVirtualGatewayMacAddress().toBytes(),
                                                   ipSubnet.gatewayIp().toOctets(),
                                                   ip.toOctets(),
                                                   iface.vlan().toShort());
            } else {
                byte[] soliciteIp = IPv6.getSolicitNodeAddress(ip.toOctets());
                neighbourReq = NeighborSolicitation.buildNdpSolicit(
                                                   ip.toOctets(),
                                                   ipSubnet.gatewayIp().toOctets(),
                                                   soliciteIp,
                                                   slsnet.getVirtualGatewayMacAddress().toBytes(),
                                                   IPv6.getMCastMacAddress(soliciteIp),
                                                   iface.vlan());
            }
            TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                                               .setOutput(iface.connectPoint().port()).build();
            OutboundPacket packet = new DefaultOutboundPacket(iface.connectPoint().deviceId(),
                                               treatment, ByteBuffer.wrap(neighbourReq.serialize()));
            packetService.emit(packet);
        }
    }

    private class InternalSlsNetListener implements SlsNetListener {
        @Override
        public void event(SlsNetEvent event) {
            switch (event.type()) {
            case SLSNET_UPDATED:
                refreshIntercepts();
                break;
            default:
                break;
            }
        }
    }

}

