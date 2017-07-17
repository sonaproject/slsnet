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
import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.incubator.net.intf.Interface;
import org.onosproject.incubator.net.intf.InterfaceEvent;
import org.onosproject.incubator.net.intf.InterfaceListener;
import org.onosproject.incubator.net.intf.InterfaceService;
import org.onosproject.incubator.net.routing.ResolvedRoute;
import org.onosproject.incubator.net.routing.RouteEvent;
import org.onosproject.incubator.net.routing.RouteListener;
import org.onosproject.incubator.net.routing.RouteService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.EncapsulationType;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.intent.ConnectivityIntent;
import org.onosproject.net.intent.Constraint;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.Key;
import org.onosproject.net.intent.MultiPointToSinglePointIntent;
import org.onosproject.net.intent.constraint.EncapsulationConstraint;
import org.onosproject.net.intent.constraint.PartialFailureConstraint;
import org.onosproject.net.intent.constraint.HashedPathSelectionConstraint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.onosproject.net.EncapsulationType.NONE;

/**
 * FIB component of SDN-IP.
 */
@Component(immediate = true, enabled = false)
public class SlsNetBorderRouting {

    private Logger log = LoggerFactory.getLogger(getClass());
    protected ApplicationId appId;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected InterfaceService interfaceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected RouteService routeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected SlsNetService slsnet;

    private final InternalSlsNetListener slsnetListener =
            new InternalSlsNetListener();

    private final InternalRouteListener routeListener =
            new InternalRouteListener();

    private final InternalInterfaceListener interfaceListener =
            new InternalInterfaceListener();

    protected static final ImmutableList<Constraint> CONSTRAINTS
            = ImmutableList.of(new PartialFailureConstraint(),
                               new HashedPathSelectionConstraint());

    private final Map<IpPrefix, MultiPointToSinglePointIntent> routeIntents
            = new ConcurrentHashMap<>();

    @Activate
    public void activate() {
        appId = slsnet.getAppId();
        slsnet.addListener(slsnetListener);
        interfaceService.addListener(interfaceListener);
        routeService.addListener(routeListener);
        log.info("slsnet route started");
    }

    @Deactivate
    public void deactivate() {
        slsnet.removeListener(slsnetListener);
        interfaceService.removeListener(interfaceListener);
        routeService.removeListener(routeListener);
        log.info("slsnet route stopped");
    }

    private void update(ResolvedRoute route) {
        synchronized (this) {
            IpPrefix prefix = route.prefix();
            EncapsulationType encap = encap();
            MultiPointToSinglePointIntent intent =
                    generateRouteIntent(prefix,
                                        route.nextHop(),
                                        route.nextHopMac(),
                                        encap);

            if (intent == null) {
                log.debug("No interface found for route {}", route);
                return;
            }

            routeIntents.put(prefix, intent);
            intentService.submit(intent);
        }
    }

    private void withdraw(ResolvedRoute route) {
        synchronized (this) {
            IpPrefix prefix = route.prefix();
            MultiPointToSinglePointIntent intent = routeIntents.remove(prefix);
            if (intent == null) {
                log.trace("No intent in routeIntents to delete for prefix: {}",
                          prefix);
                return;
            }
            intentService.withdraw(intent);
        }
    }

    /**
     * Generates a route intent for a prefix, the next hop IP address, and
     * the next hop MAC address.
     * <p/>
     * This method will find the egress interface for the intent.
     * Intent will match dst IP prefix and rewrite dst MAC address at all other
     * border switches, then forward packets according to dst MAC address.
     *
     * @param prefix            the IP prefix of the route to add
     * @param nextHopIpAddress  the IP address of the next hop
     * @param nextHopMacAddress the MAC address of the next hop
     * @param encap             the encapsulation type in use
     * @return the generated intent, or null if no intent should be submitted
     */
    private MultiPointToSinglePointIntent generateRouteIntent(
            IpPrefix prefix,
            IpAddress nextHopIpAddress,
            MacAddress nextHopMacAddress,
            EncapsulationType encap) {

        // Find the attachment point (egress interface) of the next hop
        Interface egressInterface =
                interfaceService.getMatchingInterface(nextHopIpAddress);
        if (egressInterface == null) {
            log.warn("No outgoing interface found for {}",
                    nextHopIpAddress);
            return null;
        }
        ConnectPoint egressPort = egressInterface.connectPoint();

        log.debug("Generating intent for prefix {}, next hop mac {}",
                prefix, nextHopMacAddress);

        Set<FilteredConnectPoint> ingressFilteredCPs = Sets.newHashSet();

        // TODO: To be checked with L2Network interfaces
        interfaceService.getInterfaces().forEach(intf -> {
            // Get ony ingress interfaces with IPs configured
            if (validIngressIntf(intf, egressInterface)) {
                TrafficSelector.Builder selector =
                        buildIngressTrafficSelector(intf, prefix);
                FilteredConnectPoint ingressFilteredCP =
                        new FilteredConnectPoint(intf.connectPoint(), selector.build());
                ingressFilteredCPs.add(ingressFilteredCP);
            }
        });

        // Build treatment: rewrite the destination MAC address
        TrafficTreatment.Builder treatment = DefaultTrafficTreatment.builder()
                .setEthDst(nextHopMacAddress);

        // Build the egress selector for VLAN Id
        TrafficSelector.Builder selector =
                buildTrafficSelector(egressInterface);
        FilteredConnectPoint egressFilteredCP =
                new FilteredConnectPoint(egressPort, selector.build());

        // Set priority
        int priority = slsnet.PRI_PREFIX_BASE + slsnet.PRI_PREFIX_ROUTE
                       + prefix.prefixLength() * slsnet.PRI_PREFIX_STEP;

        // Set key
        Key key = Key.of(prefix.toString(), appId);

        MultiPointToSinglePointIntent.Builder intentBuilder =
                MultiPointToSinglePointIntent.builder()
                                             .appId(appId)
                                             .key(key)
                                             .filteredIngressPoints(ingressFilteredCPs)
                                             .filteredEgressPoint(egressFilteredCP)
                                             .treatment(treatment.punt().build())
                                             .priority(priority)
                                             .constraints(CONSTRAINTS);

        setEncap(intentBuilder, CONSTRAINTS, encap);

        return intentBuilder.build();
    }

    private void addInterface(Interface intf) {
        synchronized (this) {
            for (Map.Entry<IpPrefix, MultiPointToSinglePointIntent> entry : routeIntents.entrySet()) {
                // Retrieve the IP prefix and affected intent
                IpPrefix prefix = entry.getKey();
                MultiPointToSinglePointIntent intent = entry.getValue();

                // Add new ingress FilteredConnectPoint
                Set<FilteredConnectPoint> ingressFilteredCPs =
                        Sets.newHashSet(intent.filteredIngressPoints());

                // Create the new traffic selector
                TrafficSelector.Builder selector =
                        buildIngressTrafficSelector(intf, prefix);

                // Create the Filtered ConnectPoint and add it to the existing set
                FilteredConnectPoint newIngressFilteredCP =
                        new FilteredConnectPoint(intf.connectPoint(), selector.build());
                ingressFilteredCPs.add(newIngressFilteredCP);

                // Create new intent
                MultiPointToSinglePointIntent newIntent =
                        MultiPointToSinglePointIntent.builder(intent)
                                .filteredIngressPoints(ingressFilteredCPs)
                                .build();

                routeIntents.put(entry.getKey(), newIntent);
                intentService.submit(newIntent);
            }
        }
    }

    /*
     * Handles the case in which an existing interface gets removed.
     */
    private void removeInterface(Interface intf) {
        synchronized (this) {
            for (Map.Entry<IpPrefix, MultiPointToSinglePointIntent> entry : routeIntents.entrySet()) {
                // Retrieve the IP prefix and intent possibly affected
                IpPrefix prefix = entry.getKey();
                MultiPointToSinglePointIntent intent = entry.getValue();

                // The interface removed might be an ingress interface, so the
                // selector needs to match on the interface tagging params and
                // on the prefix
                TrafficSelector.Builder ingressSelector =
                        buildIngressTrafficSelector(intf, prefix);
                FilteredConnectPoint removedIngressFilteredCP =
                        new FilteredConnectPoint(intf.connectPoint(),
                                                 ingressSelector.build());

                // The interface removed might be an egress interface, so the
                // selector needs to match only on the interface tagging params
                TrafficSelector.Builder selector = buildTrafficSelector(intf);
                FilteredConnectPoint removedEgressFilteredCP =
                        new FilteredConnectPoint(intf.connectPoint(), selector.build());

                if (intent.filteredEgressPoint().equals(removedEgressFilteredCP)) {
                     // The interface is an egress interface for the intent.
                     // This intent just lost its head. Remove it and let higher
                     // layer routing reroute
                    intentService.withdraw(routeIntents.remove(entry.getKey()));
                } else {
                    if (intent.filteredIngressPoints().contains(removedIngressFilteredCP)) {
                         // The FilteredConnectPoint is an ingress
                         // FilteredConnectPoint for the intent
                        Set<FilteredConnectPoint> ingressFilteredCPs =
                                Sets.newHashSet(intent.filteredIngressPoints());

                        // Remove FilteredConnectPoint from the existing set
                        ingressFilteredCPs.remove(removedIngressFilteredCP);

                        if (!ingressFilteredCPs.isEmpty()) {
                             // There are still ingress points. Create a new
                             // intent and resubmit
                            MultiPointToSinglePointIntent newIntent =
                                    MultiPointToSinglePointIntent.builder(intent)
                                            .filteredIngressPoints(ingressFilteredCPs)
                                            .build();

                            routeIntents.put(entry.getKey(), newIntent);
                            intentService.submit(newIntent);
                        } else {
                             // No more ingress FilteredConnectPoint. Withdraw
                             //the intent
                            intentService.withdraw(routeIntents.remove(entry.getKey()));
                        }
                    }
                }
            }
        }
    }

    /*
     * Builds an ingress traffic selector builder given an ingress interface and
     * the IP prefix to be reached.
     */
    private TrafficSelector.Builder buildIngressTrafficSelector(Interface intf, IpPrefix prefix) {
        TrafficSelector.Builder selector = buildTrafficSelector(intf);

        // TODO: to be merged with reactive routing

        // Match the destination IP prefix at the first hop
        if (prefix.isIp4()) {
            selector.matchEthType(Ethernet.TYPE_IPV4);
            selector.matchEthDst(slsnet.getVirtualGatewayMacAddress());
            // if it is default route, then we do not need match destination IP address
            if (prefix.prefixLength() != 0) {
                selector.matchIPDst(prefix);
            }
        } else {
            selector.matchEthType(Ethernet.TYPE_IPV6);
            selector.matchEthDst(slsnet.getVirtualGatewayMacAddress());
            // if it is default route, then we do not need match destination IP address
            if (prefix.prefixLength() != 0) {
                selector.matchIPv6Dst(prefix);
            }
        }
        return selector;
    }

    /*
     * Builds a traffic selector builder based on interface tagging settings.
     */
    private TrafficSelector.Builder buildTrafficSelector(Interface intf) {
        TrafficSelector.Builder selector = DefaultTrafficSelector.builder();

        // TODO: Consider other tag types
        // Match the VlanId if specified in the network interface configuration
        VlanId vlanId = intf.vlan();
        if (!vlanId.equals(VlanId.NONE)) {
            selector.matchVlanId(vlanId);
        }

        // Assume EthDst = Virtual Gateway MAC
        MacAddress dstMac = slsnet.getVirtualGatewayMacAddress();
        if (dstMac != null) {
          selector.matchEthDst(dstMac);
        }

        return selector;
    }

    // Check if the interface is an ingress interface with IPs configured
    private boolean validIngressIntf(Interface intf, Interface egressInterface) {
        if (!intf.equals(egressInterface) &&
                !intf.ipAddressesList().isEmpty() &&
                // TODO: An egress point might have two routers connected on different interfaces
                !intf.connectPoint().equals(egressInterface.connectPoint())) {
            return true;
        }
        return false;
    }

    private void refresh() {
        // TODO NEED TO HANDLE UPDATE CASES
        synchronized (this) {
            // Get the encapsulation type just set from the configuration
            EncapsulationType encap = encap();

            log.info("slsnet border routing refresh");

            for (Map.Entry<IpPrefix, MultiPointToSinglePointIntent> entry : routeIntents.entrySet()) {
                 // Get each intent currently registered by SDN-IP
                 MultiPointToSinglePointIntent intent = entry.getValue();

                 // intent constraints
                 List<Constraint> constraints = intent.constraints();
                 if (!constraints.stream()
                                .filter(c -> c instanceof EncapsulationConstraint &&
                                        new EncapsulationConstraint(encap).equals(c))
                                .findAny()
                                .isPresent()) {
                    MultiPointToSinglePointIntent.Builder intentBuilder =
                            MultiPointToSinglePointIntent.builder(intent);

                    // Set the new encapsulation constraint
                    setEncap(intentBuilder, constraints, encap);

                    // Build and submit the new intent
                    MultiPointToSinglePointIntent newIntent =
                            intentBuilder.build();

                    routeIntents.put(entry.getKey(), newIntent);
                    intentService.submit(newIntent);
                }
            }
        }
    }

    /**
     * Sets an encapsulation constraint to the intent builder given.
     *
     * @param builder the intent builder
     * @param constraints the existing intent constraints
     * @param encap the encapsulation type to be set
     */
    private static void setEncap(ConnectivityIntent.Builder builder,
                                 List<Constraint> constraints,
                                 EncapsulationType encap) {

        // Constraints might be an immutable list, so a new modifiable list
        // is created
        List<Constraint> newConstraints = new ArrayList<>(constraints);

        // Remove any encapsulation constraint if already in the list
        constraints.stream()
                .filter(c -> c instanceof EncapsulationConstraint)
                .forEach(c -> newConstraints.remove(c));

        // if the new encapsulation is different from NONE, a new encapsulation
        // constraint should be added to the list
        if (!encap.equals(NONE)) {
            newConstraints.add(new EncapsulationConstraint(encap));
        }

        // Submit new constraint list as immutable list
        builder.constraints(ImmutableList.copyOf(newConstraints));
    }

    private EncapsulationType encap() {
        return NONE;
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

    private class InternalRouteListener implements RouteListener {
        @Override
        public void event(RouteEvent event) {
            switch (event.type()) {
            case ROUTE_ADDED:
            case ROUTE_UPDATED:
                update(event.subject());
                break;
            case ROUTE_REMOVED:
                withdraw(event.subject());
                break;
            default:
                break;
            }
        }
    }

    private class InternalInterfaceListener implements InterfaceListener {
        @Override
        public void event(InterfaceEvent event) {
            switch (event.type()) {
            case INTERFACE_ADDED:
                addInterface(event.subject());
                break;
            case INTERFACE_UPDATED:
                removeInterface(event.prevSubject());
                addInterface(event.subject());
                break;
            case INTERFACE_REMOVED:
                removeInterface(event.subject());
                break;
            default:
                break;
            }
        }
    }

}
