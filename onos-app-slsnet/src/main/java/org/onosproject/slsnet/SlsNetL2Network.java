/*
 * Copyright 2016-present Open Networking Laboratory
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
import org.onlab.packet.MacAddress;
import org.onosproject.core.CoreService;
import org.onosproject.incubator.net.intf.InterfaceEvent;
import org.onosproject.incubator.net.intf.InterfaceListener;
import org.onosproject.incubator.net.intf.InterfaceService;
import org.onosproject.incubator.net.neighbour.NeighbourMessageContext;
import org.onosproject.incubator.net.neighbour.NeighbourMessageHandler;
import org.onosproject.incubator.net.neighbour.NeighbourResolutionService;
import org.onosproject.net.Host;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.host.HostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Handles neighbour messages for on behalf of the L2 Network application. Handlers
 * will be changed automatically by interface or network configuration events.
 */
@Component(immediate = true)
public class SlsNetL2Network {
    private static final String UNKNOWN_CONTEXT = "Unknown context type: {}";

    private static final String CAN_NOT_FIND_L2NETWORK =
            "Cannot find L2 Network for port {} with VLAN Id {}.";

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected InterfaceService interfaceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NeighbourResolutionService neighbourService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected SlsNetService slsnet;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigService configService;

    private L2NetworkInterfaceListener interfaceListener =
            new L2NetworkInterfaceListener();

    protected L2NetworkNeighbourMessageHandler neighbourHandler =
            new L2NetworkNeighbourMessageHandler();

    protected L2NetworkConfigListener configListener =
            new L2NetworkConfigListener();

    private final Logger log = LoggerFactory.getLogger(getClass());


    @Activate
    protected void activate() {
        log.info("slsnet l2network neighbour starting");
        configNeighbourHandler();
        interfaceService.addListener(interfaceListener);
        configService.addListener(configListener);
        log.info("slsnet l2network neighbour started");
    }

    @Deactivate
    protected void deactivate() {
        log.info("slsnet l2network neighbour stopping");
        configService.removeListener(configListener);
        interfaceService.removeListener(interfaceListener);
        neighbourService.unregisterNeighbourHandlers(slsnet.getAppId());
        log.info("slsnet l2network neighbour stopped");
    }

    /**
     * Registers neighbour handler to all available interfaces.
     */
    protected void configNeighbourHandler() {
        neighbourService.unregisterNeighbourHandlers(slsnet.getAppId());
        interfaceService
                .getInterfaces()
                .forEach(intf -> {
                    if (slsnet.isL2NetworkInterface(intf)) {
                        neighbourService.registerNeighbourHandler(intf,
                                         neighbourHandler, slsnet.getAppId());
                        log.info("slsnet l2network register neighbour handler: {}", intf);
                    }
                });
    }

    /**
     * Handles request messages.
     *
     * @param context the message context
     */
    protected void handleRequest(NeighbourMessageContext context) {
        // Find target L2 Network first, then broadcast to all interface of this L2 Network
        L2Network l2Network = slsnet.findL2Network(context.inPort(), context.vlan());
        if (l2Network != null) {
            // TODO: need to check and update slsnet.L2Network
            log.debug("slsnet handle neightbour request message: {} {}", context.inPort(), context.vlan());
            l2Network.interfaces().stream()
                    .filter(intf -> !context.inPort().equals(intf.connectPoint()))
                    .forEach(context::forward);
        } else {
            log.warn("slsnet handle neightbour request message: {} {} --> DROP FOR L2NETWORK UNKNOWN",
                     context.inPort(), context.vlan());
            context.drop();
        }
    }

    /**
     * Handles reply messages between VLAN tagged interfaces.
     *
     * @param context the message context
     * @param hostService the host service
     */
    protected void handleReply(NeighbourMessageContext context,
                               HostService hostService) {
        // Find target L2 Network, then reply to the host
        L2Network l2Network = slsnet.findL2Network(context.inPort(), context.vlan());
        if (l2Network != null) {
            // TODO: need to check and update slsnet.L2Network
            MacAddress dstMac = context.dstMac();
            Set<Host> hosts = hostService.getHostsByMac(dstMac);
            hosts = hosts.stream()
                    .filter(host -> l2Network.interfaces().contains(slsnet.getHostInterface(host)))
                    .collect(Collectors.toSet());
            // reply to all host in same L2 Network
            log.debug("slsnet handle neightbour response message: {} {} --> {}",
                      context.inPort(), context.vlan(), hosts);
            hosts.stream()
                    .map(host -> slsnet.getHostInterface(host))
                    .filter(Objects::nonNull)
                    .forEach(context::forward);
        } else {
            // this might be happened when we remove an interface from L2 Network
            // just ignore this message
            log.warn("slsnet handle neightbour response message: {} {} --> DROP FOR L2NETWORK UNKNOWN",
                     context.inPort(), context.vlan());
            context.drop();
        }
    }

    /**
     * Listener for interface configuration events.
     */
    private class L2NetworkInterfaceListener implements InterfaceListener {

        @Override
        public void event(InterfaceEvent event) {
            configNeighbourHandler();
        }
    }

    /**
     * Handler for neighbour messages.
     */
    private class L2NetworkNeighbourMessageHandler implements NeighbourMessageHandler {

        @Override
        public void handleMessage(NeighbourMessageContext context,
                                  HostService hostService) {
            switch (context.type()) {
                case REQUEST:
                    handleRequest(context);
                    break;
                case REPLY:
                    handleReply(context, hostService);
                    break;
                default:
                    log.warn(UNKNOWN_CONTEXT, context.type());
                    break;
            }
        }
    }

    /**
     * Listener for network configuration events.
     */
    private class L2NetworkConfigListener implements NetworkConfigListener {

        @Override
        public void event(NetworkConfigEvent event) {
            configNeighbourHandler();
        }
    }

}

