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
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.intf.Interface;
import org.onosproject.net.intf.InterfaceService;
import org.onosproject.net.neighbour.NeighbourMessageContext;
import org.onosproject.net.neighbour.NeighbourMessageHandler;
import org.onosproject.net.neighbour.NeighbourResolutionService;
import org.onosproject.net.Host;
import org.onosproject.net.host.HostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;


/**
 * Handles neighbour messages for on behalf of the L2 Network application. Handlers
 * will be changed automatically by interface or network configuration events.
 */
@Component(immediate = true, enabled = false)
public class SlsNetNeighbour {

    private final Logger log = LoggerFactory.getLogger(getClass());
    protected ApplicationId appId;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected InterfaceService interfaceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NeighbourResolutionService neighbourService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected SlsNetService slsnet;

    private final InternalSlsNetListener slsnetListener =
            new InternalSlsNetListener();

    private L2NetworkNeighbourMessageHandler neighbourHandler =
            new L2NetworkNeighbourMessageHandler();

    @Activate
    public void activate() {
        appId = slsnet.getAppId();
        slsnet.addListener(slsnetListener);
        refresh();
        log.info("slsnet neighbour started");
    }

    @Deactivate
    public void deactivate() {
        slsnet.removeListener(slsnetListener);
        unregister();
        log.info("slsnet neighbour stoped");
    }

    /**
     * Registers neighbour handler to all available interfaces.
     */
    protected void refresh() {
        neighbourService.unregisterNeighbourHandlers(appId);
        log.info("slsnet neighbour refresh");
        interfaceService
                .getInterfaces()
                .forEach(intf -> {
                    if (slsnet.isL2NetworkInterface(intf)) {
                        log.debug("slsnet neighbour register handler: {}", intf);
                        neighbourService.registerNeighbourHandler(intf,
                                         neighbourHandler, appId);
                    } else {
                        log.debug("slsnet neighobur unknown interface: {}", intf);
                    }
                });
    }

    /**
     * Unregisters neighbour handler to all available interfaces.
     */
    protected void unregister() {
        log.info("slsnet neighbour unregister handler");
        neighbourService.unregisterNeighbourHandlers(appId);
    }

    /**
     * Handles request messages.
     *
     * @param context the message context
     */
    protected void handleRequest(NeighbourMessageContext context) {
        if (slsnet.isVirtualGatewayIpAddress(context.target())) {
            // TODO: may need to check if from valid l2Network or border gateway
            log.info("slsnet neightbour request on virtualGatewayAddress {}; response to {} {}",
                     context.target(), context.inPort(), context.vlan());
            context.reply(slsnet.getVirtualGatewayMacAddress());
            return;
        }

        L2Network l2Network = slsnet.findL2Network(context.inPort(), context.vlan());
        if (l2Network != null) {
            int numForwards = 0;
            if (!context.dstMac().isBroadcast() && !context.dstMac().isMulticast()) {
                for (Host host : hostService.getHostsByMac(context.dstMac())) {
                    log.info("slsnet neightbour request forward unicast to {}", host.location());
                    context.forward(host.location());  // ASSUME: vlan is same
                    // TODO: may need to check host.location().time()
                    numForwards++;
                }
                if (numForwards > 0) {
                    return;
                }
            }
            // else do broadcast to all host in the same l2 network
            log.info("slsnet neightbour request forward broadcast: {} {}",
                     context.inPort(), context.vlan());
            for (Interface iface : l2Network.interfaces()) {
                if (!context.inPort().equals(iface.connectPoint())) {
                    log.info("slsnet forward neighbour request broadcast to {}", iface);
                    context.forward(iface);
                }
            }
        } else {
            log.warn("slsnet neightbour request drop: {} {}",
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
            if (dstMac.equals(slsnet.getVirtualGatewayMacAddress())) {
                log.debug("slsnet neightbour response message to virtual gateway; drop: {} {}",
                          context.inPort(), context.vlan());
                context.drop();
            } else {
                // reply to the hosts of the dstMac
                Set<Host> hosts = hostService.getHostsByMac(dstMac);
                log.debug("slsnet neightbour response message forward: {} {} --> {}",
                          context.inPort(), context.vlan(), hosts);
                hosts.stream()
                        .map(host -> slsnet.getHostInterface(host))
                        .filter(Objects::nonNull)
                        .forEach(context::forward);
            }
        } else {
            // this might be happened when we remove an interface from L2 Network
            // just ignore this message
            log.warn("slsnet neightbour response message drop for unknown l2Network: {} {}",
                     context.inPort(), context.vlan());
            context.drop();
        }
    }

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
                    log.warn("slsnet neightor unknown context type: {}", context.type());
                    break;
            }
        }
    }

    private class InternalSlsNetListener implements SlsNetListener {
        @Override
        public void event(SlsNetEvent event) {
            switch (event.type()) {
            case SLSNET_UPDATED:
                refresh();
                break;
            default:
                break;
            }
        }
    }

}

