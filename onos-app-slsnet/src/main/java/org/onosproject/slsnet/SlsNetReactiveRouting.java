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
import org.onlab.packet.VlanId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.incubator.net.intf.InterfaceService;
import org.onosproject.incubator.net.routing.Route;
import org.onosproject.incubator.net.routing.RouteService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
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
import org.onosproject.net.intent.constraint.HashedPathSelectionConstraint;
import org.onosproject.net.intent.Intent;
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
import java.util.Map.Entry;
import java.util.Set;


/**
 * SlsNetReactiveRouting handles L3 Reactive Routing.
 */
@Component(immediate = true, enabled = false)
public class SlsNetReactiveRouting {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private ApplicationId routeAppId;
    private ApplicationId interceptAppId;

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
            = ImmutableList.of(new PartialFailureConstraint(),
                               new HashedPathSelectionConstraint());

    private Set<FlowRule> interceptFlowRules = new HashSet<>();
    private Map<IpPrefix, MultiPointToSinglePointIntent> routeIntents = Maps.newConcurrentMap();
    private Set<Key> toBePurgedIntentKeys = new HashSet<>();
            // NOTE: manage purged intents by key for intentService.getIntent() supports key only

    private final InternalSlsNetListener slsnetListener = new InternalSlsNetListener();
    private ReactiveRoutingProcessor processor = new ReactiveRoutingProcessor();

    @Activate
    public void activate() {
        routeAppId = coreService.registerApplication(slsnet.REACTIVE_ROUTE_APP_ID);
        interceptAppId = coreService.registerApplication(slsnet.REACTIVE_INTERCEPT_APP_ID);
        log.info("slsnet reactive routing starting with app id {} and {}",
                 routeAppId.toString(), interceptAppId.toString());

        processor = new ReactiveRoutingProcessor();
        packetService.addProcessor(processor, PacketProcessor.director(2));
        slsnet.addListener(slsnetListener);

        registerIntercepts();
        refreshIntercepts();

        log.info("slsnet reactive routing started");
    }

    @Deactivate
    public void deactivate() {
        log.info("slsnet reactive routing stopping");

        packetService.removeProcessor(processor);
        slsnet.removeListener(slsnetListener);

        withdrawIntercepts();
        flowRuleService.removeFlowRulesById(routeAppId);
        flowRuleService.removeFlowRulesById(interceptAppId);

        routeIntents.clear();
        interceptFlowRules.clear();
        processor = null;

        log.info("slsnet reactive routing stopped");
    }

    /**
     * Request packet in via the PacketService.
     */
    private void registerIntercepts() {
        // register default intercepts on packetService for broder routing intercepts

        packetService.requestPackets(
            DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build(),
            PacketPriority.REACTIVE, interceptAppId);

        packetService.requestPackets(
            DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV6).build(),
            PacketPriority.REACTIVE, interceptAppId);

        log.info("slsnet reactive routing ip packet intercepts started");
    }

    /**
     * Cancel request for packet in via PacketService.
     */
    private void withdrawIntercepts() {
        // unregister default intercepts on packetService

        packetService.cancelPackets(
            DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV4).build(),
            PacketPriority.REACTIVE, interceptAppId);

        packetService.cancelPackets(
            DefaultTrafficSelector.builder().matchEthType(Ethernet.TYPE_IPV6).build(),
            PacketPriority.REACTIVE, interceptAppId);

        log.info("slsnet reactive routing ip packet intercepts stopped");
    }

    /**
     * Refresh device flow rules for reative intercepts on local ipSubnets.
     */
    private void refreshIntercepts() {
        if (slsnet.getVirtualGatewayMacAddress() == null) {
            log.warn("slsnet reactive routing refresh intercepts skipped "
                     + "for virtual gateway mac address unknown");
        }

        Set<FlowRule> newInterceptFlowRules = new HashSet<>();
        for (Device device : deviceService.getAvailableDevices()) {
            for (IpSubnet subnet : slsnet.getIpSubnets()) {
                newInterceptFlowRules.add(generateInterceptFlowRule(device.id(), subnet.ipPrefix()));
            }
            for (Route route : slsnet.getBorderRoutes()) {
                newInterceptFlowRules.add(generateInterceptFlowRule(device.id(), route.prefix()));
            }
        }

        if (!newInterceptFlowRules.equals(interceptFlowRules)) {
            interceptFlowRules.stream()
                .filter(rule -> !newInterceptFlowRules.contains(rule))
                .forEach(rule -> {
                    flowRuleService.removeFlowRules(rule);
                    log.info("slsnet reactive routing remove intercept flow rule: {}", rule);
                });
            newInterceptFlowRules.stream()
                .filter(rule -> !interceptFlowRules.contains(rule))
                .forEach(rule -> {
                    flowRuleService.applyFlowRules(rule);
                    log.info("slsnet reactive routing apply intercept flow rule: {}", rule);
                });
            interceptFlowRules = newInterceptFlowRules;
        }
    }

    private FlowRule generateInterceptFlowRule(DeviceId deviceId, IpPrefix prefix) {
        int priority = slsnet.PRI_REACTIVE_BASE +
                       prefix.prefixLength() * slsnet.PRI_REACTIVE_STEP +
                       slsnet.PRI_REACTIVE_INTERCEPT;
        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthType(prefix.isIp4() ? Ethernet.TYPE_IPV4 : Ethernet.TYPE_IPV6)
                .matchEthDst(slsnet.getVirtualGatewayMacAddress())
                .matchIPDst(prefix).build();
        TrafficTreatment treatment = DefaultTrafficTreatment.builder()
                .punt().build();
        FlowRule rule = DefaultFlowRule.builder()
                .forDevice(deviceId)
                .withPriority(priority)
                .withSelector(selector)
                .withTreatment(treatment)
                .fromApp(interceptAppId)
                .makePermanent()
                .forTable(0).build();
        return rule;
    }

    /**
     * Refresh routes by examining network resource status.
     */
    private void refreshRouteIntents() {
        Set<IpPrefix> prefixToRemove = new HashSet<>();

        for (Map.Entry<IpPrefix, MultiPointToSinglePointIntent> entry : routeIntents.entrySet()) {
            MultiPointToSinglePointIntent intent = entry.getValue();
            if (!deviceService.isAvailable(intent.egressPoint().deviceId())) {
                log.info("slsnet reactive routing refresh route intents; remove intent for no device: key={}",
                         intent.key());
                prefixToRemove.add(entry.getKey());
                toBePurgedIntentKeys.add(intent.key());
                intentService.withdraw(intent);
            } else {
                Set<ConnectPoint> newIngressPoints = new HashSet<>();
                for (ConnectPoint cp : intent.ingressPoints()) {
                    if (slsnet.findL2Network(cp, VlanId.NONE) != null) {
                        newIngressPoints.add(cp);
                    }
                }
                if (!newIngressPoints.equals(intent.ingressPoints())) {
                    MultiPointToSinglePointIntent updatedIntent =
                            MultiPointToSinglePointIntent.builder()
                                .appId(routeAppId)
                                .key(intent.key())
                                .selector(intent.selector())
                                .treatment(intent.treatment())
                                .ingressPoints(intent.ingressPoints())
                                .egressPoint(intent.egressPoint())
                                .priority(intent.priority())
                                .constraints(CONSTRAINTS)
                                .build();
                    log.trace("slsnet reactive routing refresh route update intent: key={} updatedIntent={}",
                              intent.key(), updatedIntent);
                    routeIntents.put(entry.getKey(), updatedIntent);
                    toBePurgedIntentKeys.remove(updatedIntent.key());   // may remove from old purged entry
                    intentService.submit(updatedIntent);
                }
            }
        }
        /* clean up intents */
        for (IpPrefix prefix : prefixToRemove) {
            routeIntents.remove(prefix);
        }
        checkIntentsPurge();
    }

    public void checkIntentsPurge() {
        // check intents to be purge
        if (!toBePurgedIntentKeys.isEmpty()) {
            Set<Key> purgedKeys = new HashSet<>();
            for (Key key : toBePurgedIntentKeys) {
                Intent intentToPurge = intentService.getIntent(key);
                if (intentToPurge == null) {
                    log.info("slsnet reactive routing purged intent: key={}", key);
                    purgedKeys.add(key);
                } else {
                    log.info("slsnet reactive routing try to purge intent: key={}", key);
                    intentService.purge(intentToPurge);
                }
            }
            toBePurgedIntentKeys.removeAll(purgedKeys);
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
            if (slsnet.findIpSubnet(dstIp).equals(slsnet.findIpSubnet(srcIp))) {
                // within same subnet; to be handled by l2NetworkRouting
                // no reactive route action but try to forward packet
                return true;
            } else {
                setUpConnectivity(srcCp, dstIp.toIpPrefix(), dstIp);
            }
        } else {
            Route route = routeService.longestPrefixMatch(dstIp);
            if (route == null) {
                log.warn("slsnet reactive routing route unknown in routeServce: dstIp={}", dstIp);
                route = slsnet.findBorderRoute(dstIp);
                if (route == null) {
                    log.warn("slsnet reactive routing route unknown in slsnet.findBorderRoute(): dstIp={}", dstIp);
                    return false;
                }
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
            log.trace("slsnet reactive routing nextHopMac unknown: prefix={} nextHopIp={}", prefix, nextHopIp);
            hostService.startMonitoringIp(nextHopIp);
            slsnet.requestMac(nextHopIp);
            return;
        }

        MultiPointToSinglePointIntent existingIntent = routeIntents.get(prefix);
        if (existingIntent != null) {
            log.trace("slsnet reactive routing update mp2p intent: prefix={} srcCp={}", prefix, srcCp);
            Set<ConnectPoint> ingressPoints = existingIntent.ingressPoints();
            if (ingressPoints.contains(srcCp) || ingressPoints.add(srcCp)) {
                MultiPointToSinglePointIntent updatedIntent =
                        MultiPointToSinglePointIntent.builder()
                                .appId(routeAppId)
                                .key(existingIntent.key())
                                .selector(existingIntent.selector())
                                .treatment(existingIntent.treatment())
                                .ingressPoints(ingressPoints)
                                .egressPoint(existingIntent.egressPoint())
                                .priority(existingIntent.priority())
                                .constraints(CONSTRAINTS)
                                .build();

                log.trace("slsnet reactive routing update mp2p intent: prefix={} srcCp={} updatedIntent={}",
                          prefix, srcCp, updatedIntent);
                routeIntents.put(prefix, updatedIntent);
                toBePurgedIntentKeys.remove(updatedIntent.key());
                intentService.submit(updatedIntent);
            }
            // If adding ingressConnectPoint to ingressPoints failed, it
            // because between the time interval from checking existing intent
            // to generating new intent, onos updated this intent due to other
            // packet-in and the new intent also includes the
            // ingressConnectPoint. This will not affect reactive routing.
        } else {
            Key key = Key.of(prefix.toString(), routeAppId);
            int priority = slsnet.PRI_REACTIVE_BASE
                           + prefix.prefixLength() * slsnet.PRI_REACTIVE_STEP
                           + slsnet.PRI_REACTIVE_ROUTE;
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
                    .appId(routeAppId)
                    .key(key)
                    .selector(selector.build())
                    .treatment(treatment.build())
                    .ingressPoints(ingressPoints)
                    .egressPoint(egressPoint)
                    .priority(priority)
                    .constraints(CONSTRAINTS)
                    .build();

           log.trace("slsnet reactive routing generate mp2p intent: prefix={} srcCp={} newIntent={}",
                     prefix, srcCp, newIntent);
           routeIntents.put(prefix, newIntent);
           toBePurgedIntentKeys.remove(newIntent.key());
           intentService.submit(newIntent);
       }
    }

    // monitor border peers for routeService lookup to be effective
    private void monitorBorderPeers() {
        for (Route route : slsnet.getBorderRoutes()) {
            hostService.startMonitoringIp(route.nextHop());
            slsnet.requestMac(route.nextHop());
        }
    }

    // Dump Cli Handler
    private void dump(String subject) {
        if (subject == "intents") {
            System.out.println("Reactive Routing Route Intents:\n");
            for (Map.Entry<IpPrefix, MultiPointToSinglePointIntent> entry: routeIntents.entrySet()) {
                System.out.println("    " + entry.getKey().toString() + ": " + entry.getValue().toString());
            }
            System.out.println("");

            System.out.println("Reactive Routing Intercept Flow Rules:\n");
            for (FlowRule rule : interceptFlowRules) {
                System.out.println("    " + rule.toString());
            }
            System.out.println("");
        }
    }

    // Listener
    private class InternalSlsNetListener implements SlsNetListener {
        @Override
        public void event(SlsNetEvent event) {
            switch (event.type()) {
            case SLSNET_UPDATED:
                refreshIntercepts();
                refreshRouteIntents();
                break;
            case SLSNET_IDLE:
                checkIntentsPurge();
                monitorBorderPeers();
                break;
            case SLSNET_DUMP:
                dump(event.subject());
                break;
            default:
                break;
            }
        }
    }

}

