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
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip6Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onosproject.incubator.net.intf.Interface;
import org.onosproject.incubator.net.intf.InterfaceService;
import org.onosproject.incubator.net.routing.Route;
import org.onosproject.incubator.net.routing.RouteService;
import org.onosproject.net.ConnectPoint;
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
import org.onosproject.intentsync.IntentSynchronizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.onlab.packet.Ethernet.TYPE_ARP;
import static org.onlab.packet.Ethernet.TYPE_IPV4;
import static org.onosproject.net.packet.PacketPriority.REACTIVE;


/**
 * Specifies the type of an IP address or an IP prefix location.
 */
enum LocationType {
    /**
     * The location of an IP address or an IP prefix is in local SDN network.
     */
    LOCAL,
    /**
     * The location of an IP address or an IP prefix is outside local SDN network.
     */
    INTERNET,
    /**
     * There is no route for this IP address or IP prefix.
     */
    NO_ROUTE
}


/**
 * Specifies the type of traffic.
 * <p>
 * We classify traffic by the first packet of each traffic.
 * </p>
 */
enum TrafficType {
    /**
     * Traffic from a host located in local SDN network wants to
     * communicate with destination host located in Internet (outside
     * local SDN network).
     */
    HOST_TO_INTERNET,
    /**
     * Traffic from Internet wants to communicate with a host located
     * in local SDN network.
     */
    INTERNET_TO_HOST,
    /**
     * Both the source host and destination host of a traffic are in
     * local SDN network.
     */
    HOST_TO_HOST,
    /**
     * Traffic from Internet wants to traverse local SDN network.
     */
    INTERNET_TO_INTERNET,
    /**
     * Any traffic wants to communicate with a destination which has
     * no route, or traffic from Internet wants to access a local private
     * IP address.
     */
    DROP,
    /**
     * Traffic does not belong to the types above.
     */
    UNKNOWN
}

/**
 * This is reactive routing to handle 3 cases:
 * (1) one host wants to talk to another host, both two hosts are in
 * SDN network.
 * (2) one host in SDN network wants to talk to another host in Internet.
 * (3) one host from Internet wants to talk to another host in SDN network.
 */
@Component(immediate = true)
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
    protected HostService hostService;

    private SlsNetReactiveRoutingIntent intentRequestListener;

    private ReactiveRoutingProcessor processor =
            new ReactiveRoutingProcessor();

    @Activate
    public void activate() {
        intentRequestListener = new SlsNetReactiveRoutingIntent(slsnet.getAppId(), hostService,
                interfaceService, intentSynchronizer);
        packetService.addProcessor(processor, PacketProcessor.director(2));
        requestIntercepts();
        log.info("slsnet reactive routing started");
    }

    @Deactivate
    public void deactivate() {
        withdrawIntercepts();
        packetService.removeProcessor(processor);
        processor = null;
        log.info("slsnet reactive routing stopped");
    }

    /**
     * Request packet in via the PacketService.
     */
    private void requestIntercepts() {
        //TODO: to support IPv6 later

        // local ipSubnet intercepts
        /*
        for (IpSubnet subnet : slsnet.getIpSubnets()) {
            int p = slsnet.PRI_PREFIX_BASE + slsnet.PRI_PREFIX_REACT
                    + subnet.ipPrefix().prefixLength() * slsnet.PRI_PREFIX_STEP;
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
            selector.matchEthType(TYPE_IPV4);
            //selector.matchEthDst(slsnet.getVirtualGatewayMacAddress());
            selector.matchIPDst(subnet.ipPrefix());
            packetService.requestPackets(selector.build(), PacketPriority(p), slsnet.getAppId());
        }
        */

        log.info("slsnet reactive routing intercepts packet started");

        // default intercepts
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(TYPE_IPV4);
        //selector.matchEthDst(slsnet.getVirtualGatewayMacAddress());
        packetService.requestPackets(selector.build(), REACTIVE, slsnet.getAppId());
        selector.matchEthType(TYPE_ARP);
        packetService.requestPackets(selector.build(), REACTIVE, slsnet.getAppId());
    }

    /**
     * Cancel request for packet in via PacketService.
     */
    private void withdrawIntercepts() {
        // local ipSubnet intercepts
        /*
        for (IpSubnet subnet : slsnet.getIp4Subnets()) {
            int priority = slsnet.PRI_PREFIX_BASE + slsnet.PRI_PREFIX_REACT
                           + subnet.ipPrefix().prefixLength() * slsnet.PRI_PREFIX_STEP;
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
            selector.matchEthType(TYPE_IPV4);
            //selector.matchEthDst(slsnet.getVirtualGatewayMacAddress());
            selector.matchIPDst(subnet.ipPrefix());
            packetService.cancelPackets(selector.build(), PacketPriority(priority), slsnet.getAppId());
        }
        */

        // default intercepts
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(TYPE_IPV4);
        //selector.matchEthDst(slsnet.getVirtualGatewayMacAddress());
        packetService.cancelPackets(selector.build(), REACTIVE, slsnet.getAppId());
        selector = DefaultTrafficSelector.builder();
        selector.matchEthType(TYPE_ARP);
        packetService.cancelPackets(selector.build(), REACTIVE, slsnet.getAppId());

        log.info("slsnet reactive routing intercepts packet stopped");
    }

    private class ReactiveRoutingProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {

            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();
            if (ethPkt == null) {
                return;
            }
            ConnectPoint srcConnectPoint = pkt.receivedFrom();

            switch (EthType.EtherType.lookup(ethPkt.getEtherType())) {
            case ARP:
                ARP arpPacket = (ARP) ethPkt.getPayload();
                Ip4Address targetIpAddress = Ip4Address
                        .valueOf(arpPacket.getTargetProtocolAddress());
                // Only when it is an ARP request packet and the target IP
                // address is a virtual gateway IP address, then it will be
                // processed.
                if (arpPacket.getOpCode() == ARP.OP_REQUEST
                    && slsnet.isVirtualGatewayIpAddress(targetIpAddress)) {
                    MacAddress gatewayMacAddress =
                            slsnet.getVirtualGatewayMacAddress();
                    if (gatewayMacAddress == null) {
                        break;
                    }
                    Ethernet eth = ARP.buildArpReply(targetIpAddress,
                                                     gatewayMacAddress,
                                                     ethPkt);

                    TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();
                    builder.setOutput(srcConnectPoint.port());
                    packetService.emit(new DefaultOutboundPacket(
                            srcConnectPoint.deviceId(),
                            builder.build(),
                            ByteBuffer.wrap(eth.serialize())));
                }
                break;
            case IPV4:
                // Parse packet
                IPv4 ipv4Packet = (IPv4) ethPkt.getPayload();
                IpAddress dstIp =
                        IpAddress.valueOf(ipv4Packet.getDestinationAddress());
                IpAddress srcIp =
                        IpAddress.valueOf(ipv4Packet.getSourceAddress());
                MacAddress srcMac = ethPkt.getSourceMAC();
                packetReactiveProcessor(dstIp, srcIp, srcConnectPoint, srcMac);

                // TODO emit packet first or packetReactiveProcessor first
                ConnectPoint egressConnectPoint = null;
                egressConnectPoint = getEgressConnectPoint(dstIp);
                if (egressConnectPoint != null) {
                    forwardPacketToDst(context, egressConnectPoint);
                }
                break;
            default:
                break;
            }
        }
    }

    /**
     * Routes packet reactively.
     *
     * @param dstIpAddress the destination IP address of a packet
     * @param srcIpAddress the source IP address of a packet
     * @param srcConnectPoint the connect point where a packet comes from
     * @param srcMacAddress the source MAC address of a packet
     */
    private void packetReactiveProcessor(IpAddress dstIpAddress,
                                        IpAddress srcIpAddress,
                                        ConnectPoint srcConnectPoint,
                                        MacAddress srcMacAddress) {
        checkNotNull(dstIpAddress);
        checkNotNull(srcIpAddress);
        checkNotNull(srcConnectPoint);
        checkNotNull(srcMacAddress);

        //
        // Step1: Try to update the existing intent first if it exists.
        //
        IpPrefix ipPrefix = null;
        Route route = null;
        if (slsnet.isIpAddressLocal(dstIpAddress)) {
            if (dstIpAddress.isIp4()) {
                ipPrefix = IpPrefix.valueOf(dstIpAddress,
                        Ip4Address.BIT_LENGTH);
            } else {
                ipPrefix = IpPrefix.valueOf(dstIpAddress,
                        Ip6Address.BIT_LENGTH);
            }
        } else {
            // This Should not happen for SlsNetRoute should already register intents for this case.
            // Get IP prefix from route table
            route = routeService.longestPrefixMatch(dstIpAddress);
            if (route != null) {
                ipPrefix = route.prefix();
            }
        }
        if (ipPrefix != null
                && intentRequestListener.mp2pIntentExists(ipPrefix)) {
            intentRequestListener.updateExistingMp2pIntent(ipPrefix,
                    srcConnectPoint);
            return;
        }

        //
        // Step2: There is no existing intent for the destination IP address.
        // Check whether it is necessary to create a new one. If necessary then
        // create a new one.
        //
        TrafficType trafficType =
                trafficTypeClassifier(srcConnectPoint, dstIpAddress);

        switch (trafficType) {
        case HOST_TO_INTERNET:
            // If the destination IP address is outside the local SDN network.
            // The Step 1 has already handled it. We do not need to do anything here.
            intentRequestListener.setUpConnectivityHostToInternet(srcIpAddress,
                    ipPrefix, route.nextHop());
            break;
        case INTERNET_TO_HOST:
            intentRequestListener.setUpConnectivityInternetToHost(dstIpAddress);
            break;
        case HOST_TO_HOST:
            intentRequestListener.setUpConnectivityHostToHost(dstIpAddress,
                    srcIpAddress, srcMacAddress, srcConnectPoint);
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
     * @param srcConnectPoint the connect point where the packet comes from
     * @param dstIp the destination IP address in packet
     * @return the traffic type which this packet belongs to
     */
    private TrafficType trafficTypeClassifier(ConnectPoint srcConnectPoint,
                                              IpAddress dstIp) {
        LocationType dstIpLocationType = getLocationType(dstIp);
        Optional<Interface> srcInterface =
                interfaceService.getInterfacesByPort(srcConnectPoint).stream().findFirst();

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
     * @param ipAddress the IP address to evaluate
     * @return the IP address location type
     */
    private LocationType getLocationType(IpAddress ipAddress) {
        if (slsnet.isIpAddressLocal(ipAddress)) {
            return LocationType.LOCAL;
        } else if (routeService.longestPrefixMatch(ipAddress) != null) {
            return LocationType.INTERNET;
        } else {
            return LocationType.NO_ROUTE;
        }
    }

    public ConnectPoint getEgressConnectPoint(IpAddress dstIpAddress) {
        LocationType type = getLocationType(dstIpAddress);
        if (type == LocationType.LOCAL) {
            Set<Host> hosts = hostService.getHostsByIp(dstIpAddress);
            if (!hosts.isEmpty()) {
                return hosts.iterator().next().location();
            } else {
                hostService.startMonitoringIp(dstIpAddress);
                return null;
            }
        } else if (type == LocationType.INTERNET) {
            IpAddress nextHopIpAddress = null;
            Route route = routeService.longestPrefixMatch(dstIpAddress);
            if (route != null) {
                nextHopIpAddress = route.nextHop();
                Interface it = interfaceService.getMatchingInterface(nextHopIpAddress);
                if (it != null) {
                    return it.connectPoint();
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Emits the specified packet onto the network.
     *
     * @param context      the packet context
     * @param connectPoint the connect point where the packet should be
     *                     sent out
     */
    private void forwardPacketToDst(PacketContext context,
                                    ConnectPoint connectPoint) {
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(connectPoint.port()).build();
        OutboundPacket packet =
                new DefaultOutboundPacket(connectPoint.deviceId(), treatment,
                                          context.inPacket().unparsed());
        packetService.emit(packet);
        log.trace("sending packet: {}", packet);
    }
}

