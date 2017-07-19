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
import org.onlab.packet.EthType;
import org.onlab.packet.Ethernet;
import org.onlab.packet.ICMP;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip6Address;
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
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.Set;

import static org.onlab.packet.Ethernet.TYPE_IPV4;
import static org.onosproject.net.packet.PacketPriority.REACTIVE;


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
    protected ApplicationId reactAppId;

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

    private final InternalDeviceListener deviceListener = new InternalDeviceListener();
    private final InternalSlsNetListener slsnetListener = new InternalSlsNetListener();
    private ReactiveRoutingProcessor processor = new ReactiveRoutingProcessor();
    private SlsNetReactiveRoutingIntent intentRequest;

    @Activate
    public void activate() {
        reactAppId = coreService.registerApplication(REACT_APP_ID);
        log.info("slsnet reactive routing starting with react app id {}", reactAppId.toString());

        intentRequest = new SlsNetReactiveRoutingIntent(slsnet, hostService,
                                                        interfaceService, intentService);
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

        withdrawIntercepts();
        flowRuleService.removeFlowRulesById(reactAppId);

        packetService.removeProcessor(processor);
        slsnet.removeListener(slsnetListener);
        processor = null;

        log.info("slsnet reactive routing stopped");
    }

    /**
     * Request packet in via the PacketService.
     */
    private void registerIntercepts() {
        // register default intercepts on packetService
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(TYPE_IPV4);
        packetService.requestPackets(selector.build(), REACTIVE, slsnet.getAppId());
        log.info("slsnet reactive routing IPV4 intercepts packet started");
    }

    /**
     * Cancel request for packet in via PacketService.
     */
    private void withdrawIntercepts() {
        // unregister default intercepts on packetService
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();
        selector.matchEthType(TYPE_IPV4);
        packetService.cancelPackets(selector.build(), REACTIVE, slsnet.getAppId());
        log.info("slsnet reactive routing IPV4 intercepts packet stopped");
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
                        .matchEthType(TYPE_IPV4)
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

            switch (EthType.EtherType.lookup(ethPkt.getEtherType())) {
            case IPV4:
                IPv4 ipv4Packet = (IPv4) ethPkt.getPayload();
                IpAddress dstIp = IpAddress.valueOf(ipv4Packet.getDestinationAddress());

                if (!checkVirtualGatewayPacket(pkt)) {
                    // not for virtual gateway; do reactive routing
                    if (packetIp4ReactiveProcessor(ethPkt, srcCp)) {
                        forwardPacketToDstIp(context, dstIp);
                    }
                }
                break;
            default:
                break;
            }
        }
    }

    /**
     * Routes packet reactively.
     */
    private boolean packetIp4ReactiveProcessor(Ethernet ethPkt, ConnectPoint srcCp) {
        MacAddress srcMac = ethPkt.getSourceMAC();
        IPv4 ipv4Packet = (IPv4) ethPkt.getPayload();
        IpAddress srcIp = IpAddress.valueOf(ipv4Packet.getSourceAddress());
        IpAddress dstIp = IpAddress.valueOf(ipv4Packet.getDestinationAddress());

        log.info("slsnet reactive routing IPV4 packet: srcIp={} dstIp={} srcCp={}", srcIp, dstIp, srcCp);

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
        if (ipPrefix != null && intentRequest.mp2pIntentExists(ipPrefix)) {
            log.info("slsnet reactive routing update mp2p intent: dstIp={} srcCp={}", ipPrefix, srcCp);
            intentRequest.updateExistingMp2pIntent(ipPrefix, srcCp);
            return true;
        }

        // Step2: There is no existing intent for the destination IP address.
        // Check whether it is necessary to create a new one. If necessary then create a new one.
        if (slsnet.isIpAddressLocal(srcIp)) {
            if (slsnet.isIpAddressLocal(dstIp)) {
                intentRequest.setUpConnectivityHostToHost(dstIp, srcIp, srcMac, srcCp);
            } else {
                intentRequest.setUpConnectivityHostToInternet(srcIp, ipPrefix, route.nextHop());
            }
        } else {
            if (slsnet.isIpAddressLocal(dstIp)) {
                intentRequest.setUpConnectivityInternetToHost(dstIp, srcCp);
            } else {
                log.warn("slsnet external traffic; ignore: srcCp={} srcIp={} dstIp={}",
                         srcCp, srcIp, dstIp);
                return false;
            }
        }
        return true;
    }

    /**
     * handle Packet with dstIp=virtualGatewayIpAddresses.
     * returns true(handled) or false(not for virtual gateway)
     */
    private boolean checkVirtualGatewayPacket(InboundPacket pkt) {
        Ethernet ethPkt = pkt.parsed();  // assume valid

        switch (EthType.EtherType.lookup(ethPkt.getEtherType())) {
        case IPV4:
            IPv4 ipv4Packet = (IPv4) ethPkt.getPayload();
            IpAddress srcIp = IpAddress.valueOf(ipv4Packet.getSourceAddress());
            IpAddress dstIp = IpAddress.valueOf(ipv4Packet.getDestinationAddress());

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
                                    ByteBuffer.wrap(icmpPacket.buildIcmpReply(pkt.parsed()).serialize()));
                        packetService.emit(packet);
                        break;
                    }
                }
                log.warn("slsnet reactive routing IPV4 packet to virtual gateway dropped: "
                         + "srcIp={} dstIp={} proto={}", srcIp, dstIp, ipv4Packet.getProtocol());
                return true;
            }
            break;

        // NEED TO HANDLE IPv6 Case

        default:
            break;
        }
        return false;
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

