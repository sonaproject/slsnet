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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.EthType;
import org.onlab.packet.Ethernet;
import org.onlab.packet.ICMP;
import org.onlab.packet.ICMP6;
import org.onlab.packet.IPv4;
import org.onlab.packet.IPv6;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.incubator.net.intf.InterfaceService;
import org.onosproject.incubator.net.routing.Route;
import org.onosproject.incubator.net.routing.RouteService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Device;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.Host;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intent.Constraint;
import org.onosproject.net.intent.constraint.PartialFailureConstraint;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.Key;
import org.onosproject.net.intent.MultiPointToSinglePointIntent;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketPriority;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;



/**
 * This is reactive routing to handle 3 cases:
 * (1) one host wants to talk to another host, both two hosts are in
 * SDN network.
 * (2) one host in SDN network wants to talk to another host in Internet.
 * (3) one host from Internet wants to talk to another host in SDN network.
 */
@Component(immediate = true, enabled = false)
public class SlsNetReactiveRouting {

    private static final String REACT_APP_ID = "org.onosproject.slsnet.react";

    private final Logger log = LoggerFactory.getLogger(getClass());
    private ApplicationId reactAppId;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected RouteService routeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected InterfaceService interfaceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected SlsNetService slsnet;

    private static final ImmutableList<Constraint> CONSTRAINTS
            = ImmutableList.of(new PartialFailureConstraint());
           // = ImmutableList.of(new PartialFailureConstraint(),
           //                    new HashedPathSelectionConstraint());

    private final Map<IpPrefix, MultiPointToSinglePointIntent> routeIntents
            = Maps.newConcurrentMap();

    private final InternalDeviceListener deviceListener = new InternalDeviceListener();
    private final InternalSlsNetListener slsnetListener = new InternalSlsNetListener();
    private ReactiveRoutingProcessor processor = new ReactiveRoutingProcessor();

    @Activate
    public void activate() {
        reactAppId = coreService.registerApplication(REACT_APP_ID);
        log.info("slsnet reactive routing starting with react app id {}", reactAppId.toString());

        slsnet.addListener(slsnetListener);
        packetService.addProcessor(processor, PacketProcessor.director(2));
        registerIntercepts();

        deviceService.addListener(deviceListener);
        refreshIntercepts();

        log.info("slsnet reactive routing started");
    }

    @Deactivate
    public void deactivate() {
        log.info("slsnet reactive routing stopping");

        deviceService.removeListener(deviceListener);
        withdrawIntercepts();

        packetService.removeProcessor(processor);
        slsnet.removeListener(slsnetListener);
        processor = null;

        flowRuleService.removeFlowRulesById(reactAppId);

        log.info("slsnet reactive routing stopped");
    }

    /**
     * Request packet in via the PacketService.
     */
    private void registerIntercepts() {
        // register default intercepts on packetService
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.requestPackets(selector.build(), PacketPriority.REACTIVE, slsnet.getAppId());
        log.info("slsnet reactive routing IPV4 intercepts packet started");
        // NEED TO REQUEST IPV6 PACKETS
    }

    /**
     * Cancel request for packet in via PacketService.
     */
    private void withdrawIntercepts() {
        // unregister default intercepts on packetService
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(Ethernet.TYPE_IPV4);
        packetService.cancelPackets(selector.build(), PacketPriority.REACTIVE, slsnet.getAppId());
        log.info("slsnet reactive routing IPV4 intercepts packet stopped");
        // NEED TO CANCEL IPV6 PACKETS
    }

    /**
     * Refresh device flow rules for reative intercepts on local ipSubnets.
     */
    private void refreshIntercepts() {
        if (slsnet.getVirtualGatewayMacAddress() == null) {
            log.warn("slsnet reactive routing refresh intercepts skipped "
                     + "for virtual gateway mac address unknown");
        }
        // clean all previous flow rules
        flowRuleService.removeFlowRulesById(reactAppId);
        for (Device device : deviceService.getAvailableDevices()) {
            // install new flow rules for local subnet
            for (IpSubnet ipSubnet : slsnet.getIp4Subnets()) {
                int priority = slsnet.PRI_REACTIVE_BASE +
                               ipSubnet.ipPrefix().prefixLength() * slsnet.PRI_REACTIVE_STEP +
                               slsnet.PRI_REACTIVE_INTERCEPT;
                TrafficSelector selector = DefaultTrafficSelector.builder()
                        .matchEthType(Ethernet.TYPE_IPV4)
                        .matchEthDst(slsnet.getVirtualGatewayMacAddress())
                        .matchIPDst(ipSubnet.ipPrefix()).build();
                TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                        .punt().build();
                FlowRule rule = DefaultFlowRule.builder()
                        .forDevice(device.id())
                        .withPriority(priority)
                        .withSelector(selector)
                        .withTreatment(treatment)
                        .fromApp(reactAppId)
                        .makePermanent()
                        .forTable(0).build();
                flowRuleService.applyFlowRules(rule);
                log.debug("slsnet reactive routing install FlowRule: deviceId={} {}",
                          device.id(), rule);
            }
            // install new flow rules for border routes
            // MAY NEED TO ADD IPv6 CASE
        }
    }

    /**
     * Reactive Packet Handling.
     */
    private class ReactiveRoutingProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            InboundPacket pkt = context.inPacket();
            Ethernet ethPkt = pkt.parsed();
            if (ethPkt == null) {
                return;
            }
            ConnectPoint srcCp = pkt.receivedFrom();
            IpAddress srcIp;
            IpAddress dstIp;

            switch (EthType.EtherType.lookup(ethPkt.getEtherType())) {
            case IPV4:
                IPv4 ipv4Packet = (IPv4) ethPkt.getPayload();
                srcIp = IpAddress.valueOf(ipv4Packet.getSourceAddress());
                dstIp = IpAddress.valueOf(ipv4Packet.getDestinationAddress());
                break;
            case IPV6:
                IPv6 ipv6Packet = (IPv6) ethPkt.getPayload();
                srcIp = IpAddress.valueOf(IpAddress.Version.INET6, ipv6Packet.getSourceAddress());
                dstIp = IpAddress.valueOf(IpAddress.Version.INET6, ipv6Packet.getDestinationAddress());
                break;
            default:
                return;  // ignore unknow ether type packets
            }

            if (checkVirtualGatewayIpPacket(pkt, srcIp, dstIp)) {
                // handled as packet for virtual gateway inself
            } else if (ipPacketReactiveProcessor(ethPkt, srcCp, srcIp, dstIp)) {
                forwardPacketToDstIp(context, dstIp);
            }
        }
    }

    /**
     * handle Packet with dstIp=virtualGatewayIpAddresses.
     * returns true(handled) or false(not for virtual gateway)
     */
    private boolean checkVirtualGatewayIpPacket(InboundPacket pkt, IpAddress srcIp, IpAddress dstIp) {
        Ethernet ethPkt = pkt.parsed();  // assume valid

        if (!ethPkt.getDestinationMAC().equals(slsnet.getVirtualGatewayMacAddress())
            || !slsnet.isVirtualGatewayIpAddress(dstIp)) {
            return false;

         } else if (dstIp.isIp4()) {
            IPv4 ipv4Packet = (IPv4) ethPkt.getPayload();
            if (ipv4Packet.getProtocol() == IPv4.PROTOCOL_ICMP) {
                ICMP icmpPacket = (ICMP) ipv4Packet.getPayload();

                if (icmpPacket.getIcmpType() == ICMP.TYPE_ECHO_REQUEST) {
                    log.info("slsnet reactive routing IPV4 ICMP ECHO request to virtual gateway: "
                              + "srcIp={} dstIp={} proto={}", srcIp, dstIp, ipv4Packet.getProtocol());
                    TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                                .setOutput(pkt.receivedFrom().port()).build();
                    OutboundPacket packet =
                        new DefaultOutboundPacket(pkt.receivedFrom().deviceId(), treatment,
                                ByteBuffer.wrap(icmpPacket.buildIcmpReply(pkt.parsed()).serialize()));
                    packetService.emit(packet);
                    return true;
                }
            }
            log.warn("slsnet reactive routing IPV4 packet to virtual gateway dropped: "
                     + "srcIp={} dstIp={} proto={}", srcIp, dstIp, ipv4Packet.getProtocol());
            return true;

         } else if (dstIp.isIp6()) {
            // TODO: not tested yet (2017-07-20)
            IPv6 ipv6Packet = (IPv6) ethPkt.getPayload();
            if (ipv6Packet.getNextHeader() == IPv6.PROTOCOL_ICMP6) {
                ICMP6 icmp6Packet = (ICMP6) ipv6Packet.getPayload();

                if (icmp6Packet.getIcmpType() == ICMP6.ECHO_REQUEST) {
                    log.info("slsnet reactive routing IPV6 ICMP6 ECHO request to virtual gateway: "
                              + "srcIp={} dstIp={} nextHeader={}", srcIp, dstIp, ipv6Packet.getNextHeader());
                    TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                                .setOutput(pkt.receivedFrom().port()).build();
                    OutboundPacket packet =
                        new DefaultOutboundPacket(pkt.receivedFrom().deviceId(), treatment,
                                ByteBuffer.wrap(icmp6Packet.buildIcmp6Reply(pkt.parsed()).serialize()));
                    packetService.emit(packet);
                    return true;
                }
            }
            log.warn("slsnet reactive routing IPV6 packet to virtual gateway dropped: "
                     + "srcIp={} dstIp={} nextHeader={}", srcIp, dstIp, ipv6Packet.getNextHeader());
            return true;

        }
        return false;  // unknown traffic
    }

    /**
     * Routes packet reactively.
     */
    private boolean ipPacketReactiveProcessor(Ethernet ethPkt, ConnectPoint srcCp, IpAddress srcIp, IpAddress dstIp) {
        log.trace("slsnet reactive routing ip packet: srcCp={} srcIp={} dstIp={} srcCp={}", srcCp, srcIp, dstIp);
        // NOTE: do not check source ip for source is recognized as ConnectPoint only
        if (slsnet.isIpAddressLocal(dstIp)) {
            setUpConnectivity(srcCp, dstIp.toIpPrefix(), dstIp);
        } else {
            Route route = routeService.longestPrefixMatch(dstIp);
            if (route == null) {
                log.warn("slsnet reactive routing route unknown: dstIp={}", dstIp);
                return false;
            }
            setUpConnectivity(srcCp, route.prefix(), route.nextHop());
        }
        return true;
    }

    /**
     * Emits the specified packet onto the network.
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
            slsnet.requestMac(dstIp);
            return;
        }
        log.info("slsnet reactive routing forward packet: dstHost={} packet={}", dstHost, context);
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .setOutput(dstHost.location().port()).build();
        // NOTE: eth address update treatment is NOT effective
        OutboundPacket packet =
                new DefaultOutboundPacket(dstHost.location().deviceId(), treatment,
                                          ByteBuffer.wrap(context.inPacket().parsed()
                                              .setSourceMACAddress(slsnet.getVirtualGatewayMacAddress())
                                              .setDestinationMACAddress(dstHost.mac()).serialize()));
        packetService.emit(packet);
    }

    /**
     * Update intents for connectivity.
     *
     * ToHost: prefix = destHostIp.toIpPrefix(), nextHopIp = destHostIp
     * ToInternet: prefix = route.prefix(), nextHopIp = route.nextHopIp
     */
    private void setUpConnectivity(ConnectPoint srcCp, IpPrefix prefix, IpAddress nextHopIp) {
        MacAddress nextHopMac = null;
        ConnectPoint egressPoint = null;
        for (Host host : hostService.getHostsByIp(nextHopIp)) {
            if (host.mac() != null) {
                nextHopMac = host.mac();
                egressPoint = host.location();
                break;
            }
        }
        if (nextHopMac == null) {
            log.trace("slsnet reactive intent nextHopMac unknown: prefix={} nextHopIp={}", prefix, nextHopIp);
            hostService.startMonitoringIp(nextHopIp);
            slsnet.requestMac(nextHopIp);
            return;
        }

        MultiPointToSinglePointIntent existingIntent = routeIntents.get(prefix);
        if (existingIntent != null) {
            log.trace("slsnet reactive intent update mp2p intent: prefix={} srcCp={}", prefix, srcCp);
            Set<ConnectPoint> ingressPoints = existingIntent.ingressPoints();
            if (ingressPoints.contains(srcCp) || ingressPoints.add(srcCp)) {
                MultiPointToSinglePointIntent updatedIntent =
                        MultiPointToSinglePointIntent.builder()
                                .appId(reactAppId)
                                .key(existingIntent.key())
                                .selector(existingIntent.selector())
                                .treatment(existingIntent.treatment())
                                .ingressPoints(ingressPoints)
                                .egressPoint(existingIntent.egressPoint())
                                .priority(existingIntent.priority())
                                .constraints(CONSTRAINTS)
                                .build();

                log.trace("slsnet reactive intent update mp2p intent: prefix={} srcCp={} updatedIntent={}",
                          prefix, srcCp, updatedIntent);
                routeIntents.put(prefix, updatedIntent);
                intentService.submit(updatedIntent);
            }
            // If adding ingressConnectPoint to ingressPoints failed, it
            // because between the time interval from checking existing intent
            // to generating new intent, onos updated this intent due to other
            // packet-in and the new intent also includes the
            // ingressConnectPoint. This will not affect reactive routing.
        } else {
            Key key = Key.of(prefix.toString(), reactAppId);
            int priority = slsnet.PRI_REACTIVE_BASE
                           + prefix.prefixLength() * slsnet.PRI_REACTIVE_STEP
                           + slsnet.PRI_REACTIVE_ACTION;
            TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
            // select.matchEthDst(slsnet.getVirtualGatewayMacAddress());
            if (prefix.isIp4()) {
                selector.matchEthType(Ethernet.TYPE_IPV4);
                if (prefix.prefixLength() > 0) {
                    selector.matchIPDst(prefix);
                }
            } else {
                selector.matchEthType(Ethernet.TYPE_IPV6);
                if (prefix.prefixLength() > 0) {
                    selector.matchIPv6Dst(prefix);
                }
            }
            TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder()
                    .setEthDst(nextHopMac)
                    .setEthSrc(slsnet.getVirtualGatewayMacAddress());
            Set<ConnectPoint> ingressPoints = new HashSet<>();
            ingressPoints.add(srcCp);
            MultiPointToSinglePointIntent newIntent = MultiPointToSinglePointIntent.builder()
                    .appId(reactAppId)
                    .key(key)
                    .selector(selector.build())
                    .treatment(treatment.build())
                    .ingressPoints(ingressPoints)
                    .egressPoint(egressPoint)
                    .priority(priority)
                    .constraints(CONSTRAINTS)
                    .build();

           log.trace("slsnet reactive intent generate mp2p intent: prefix={} srcCp={} newIntent={}",
                     prefix, srcCp, newIntent);
           routeIntents.put(prefix, newIntent);
           intentService.submit(newIntent);
       }
    }

    // Service Listeners

    private class InternalDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
            switch (event.type()) {
            case DEVICE_ADDED:
            case DEVICE_AVAILABILITY_CHANGED:
            case DEVICE_REMOVED:
            case DEVICE_SUSPENDED:
            case DEVICE_UPDATED:
                refreshIntercepts();
                break;
            default:
                break;
            }
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

