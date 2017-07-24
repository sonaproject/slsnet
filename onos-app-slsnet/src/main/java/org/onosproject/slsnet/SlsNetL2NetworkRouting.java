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
import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.incubator.net.intf.Interface;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.EncapsulationType;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.Host;
import org.onosproject.net.ResourceGroup;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.host.HostService;
import org.onosproject.net.intent.ConnectivityIntent;
import org.onosproject.net.intent.Constraint;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.IntentService;
import org.onosproject.net.intent.Key;
import org.onosproject.net.intent.MultiPointToSinglePointIntent;
import org.onosproject.net.intent.SinglePointToMultiPointIntent;
import org.onosproject.net.intent.constraint.EncapsulationConstraint;
import org.onosproject.net.intent.constraint.PartialFailureConstraint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * An implementation of L2NetworkOperationService.
 * Handles the execution order of the L2 Network operations generated by the
 * application.
 */
@Component(immediate = true, enabled = false)
public class SlsNetL2NetworkRouting {

    public static final String PREFIX_BROADCAST = "BCAST";
    public static final String PREFIX_UNICAST = "UNI";
    private static final String SEPARATOR = "-";

    private final Logger log = LoggerFactory.getLogger(getClass());
    protected ApplicationId l2NetAppId;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected IntentService intentService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected SlsNetService slsnet;

    public static final ImmutableList<Constraint> PARTIAL_FAILURE_CONSTRAINT =
            ImmutableList.of(new PartialFailureConstraint());

    private Set<Intent> l2NetworkIntents = new HashSet<>();
    private Set<Key> toBePurgedIntentKeys = new HashSet<>();

    private final InternalSlsNetListener slsnetListener = new InternalSlsNetListener();

    @Activate
    public void activate() {
        l2NetAppId = coreService.registerApplication(slsnet.L2NETWORK_APP_ID);
        log.info("slsnet l2network routing starting with l2net app id {}", l2NetAppId.toString());

        slsnet.addListener(slsnetListener);

        refresh();

        log.info("slsnet l2network started");
    }

    @Deactivate
    public void deactivate() {
        log.info("slsnet l2network routing stopping");

        slsnet.removeListener(slsnetListener);

        for (Intent intent : l2NetworkIntents) {
            intentService.withdraw(intent);
            toBePurgedIntentKeys.add(intent.key());
        }
        for (Key key : toBePurgedIntentKeys) {
            Intent intentToPurge = intentService.getIntent(key);
            if (intentToPurge != null) {
                intentService.purge(intentToPurge);
            }
        }
        l2NetworkIntents.clear();

        log.info("slsnet l2network routing stopped");
    }

    public void refresh() {
        log.info("slsnet l2network routing refresh");

        Set<Intent> newL2NetworkIntents = new HashSet<>();
        for (L2Network l2Network : slsnet.getL2Networks()) {
            // scans all l2network regardless of dirty flag
            newL2NetworkIntents.addAll(generateL2NetworkIntents(l2Network));
            if (l2Network.dirty()) {
                l2Network.setDirty(false);
            }
        }

        boolean updated = false;
        for (Intent intent : l2NetworkIntents) {
            if (!newL2NetworkIntents.contains(intent)) {
                log.info("slsnet l2network routing withdraw intent: {}", intent);
                intentService.withdraw(intent);
                toBePurgedIntentKeys.add(intent.key());
                updated = true;
            }
        }
        for (Intent intent : newL2NetworkIntents) {
            if (!l2NetworkIntents.contains(intent)) {
                log.info("slsnet l2network routing submit intent: {}", intent);
                intentService.submit(intent);
                // remove form purge list
                if (toBePurgedIntentKeys.contains(intent.key())) {
                    toBePurgedIntentKeys.remove(intent.key());
                }
                updated = true;
            }
        }
        if (updated) {
            l2NetworkIntents = newL2NetworkIntents;
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
                    log.info("slsnet l2network routing purged intent: key={}", key);
                    purgedKeys.add(key);
                } else {
                    log.info("slsnet l2network routing try to purge intent: key={}", key);
                    intentService.purge(intentToPurge);
                }
            }
            toBePurgedIntentKeys.removeAll(purgedKeys);
        }
    }

    // Generates Unicast Intents and broadcast Intents for the L2 Network.

    private Set<Intent> generateL2NetworkIntents(L2Network l2Network) {
        return new ImmutableSet.Builder<Intent>()
            .addAll(buildBrcIntents(l2Network, l2NetAppId))
            .addAll(buildUniIntents(l2Network, hostsFromL2Network(l2Network), l2NetAppId))
            .build();
    }

    private Set<Host> hostsFromL2Network(L2Network l2Network) {
        Set<Interface> interfaces = l2Network.interfaces();
        return interfaces.stream()
                .map(this::hostsFromInterface)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    private Set<Host> hostsFromInterface(Interface iface) {
        return hostService.getConnectedHosts(iface.connectPoint())
                .stream()
                .filter(host -> host.vlan().equals(iface.vlan()))
                .collect(Collectors.toSet());
    }

    // Build Boadcast Intents for a L2 Network.
    private Set<Intent> buildBrcIntents(L2Network l2Network, ApplicationId appId) {
        Set<Interface> interfaces = l2Network.interfaces();
        if (!l2Network.l2Forwarding() || interfaces.size() < 2) {
            return ImmutableSet.of();
        }
        Set<Intent> brcIntents = Sets.newHashSet();
        ResourceGroup resourceGroup = ResourceGroup.of(l2Network.name());

        // Generates broadcast Intents from any network interface to other
        // network interface from the L2 Network.
        interfaces
            .forEach(src -> {
            FilteredConnectPoint srcFcp = buildFilteredConnectedPoint(src);
            Set<FilteredConnectPoint> dstFcps = interfaces.stream()
                    .filter(iface -> !iface.equals(src))
                    .map(this::buildFilteredConnectedPoint)
                    .collect(Collectors.toSet());
            Key key = buildKey(PREFIX_BROADCAST, srcFcp.connectPoint(), l2Network.name(),
                               MacAddress.BROADCAST, appId);
            TrafficSelector selector = DefaultTrafficSelector.builder()
                    .matchEthDst(MacAddress.BROADCAST)
                    .build();
            SinglePointToMultiPointIntent.Builder intentBuilder = SinglePointToMultiPointIntent.builder()
                    .appId(appId)
                    .key(key)
                    .selector(selector)
                    .filteredIngressPoint(srcFcp)
                    .filteredEgressPoints(dstFcps)
                    .constraints(PARTIAL_FAILURE_CONSTRAINT)
                    .priority(SlsNetService.PRI_L2NETWORK_BROADCAST)
                    .resourceGroup(resourceGroup);
            setEncap(intentBuilder, PARTIAL_FAILURE_CONSTRAINT, l2Network.encapsulationType());
            brcIntents.add(intentBuilder.build());
        });
        return brcIntents;
    }

    // Builds unicast Intents for a L2 Network.
    private Set<Intent> buildUniIntents(L2Network l2Network, Set<Host> hosts, ApplicationId appId) {
        Set<Interface> interfaces = l2Network.interfaces();
        if (!l2Network.l2Forwarding() || interfaces.size() < 2) {
            return ImmutableSet.of();
        }
        Set<Intent> uniIntents = Sets.newHashSet();
        ResourceGroup resourceGroup = ResourceGroup.of(l2Network.name());
        hosts.forEach(host -> {
            FilteredConnectPoint hostFcp = buildFilteredConnectedPoint(host);
            Set<FilteredConnectPoint> srcFcps = interfaces.stream()
                    .map(this::buildFilteredConnectedPoint)
                    .filter(fcp -> !fcp.equals(hostFcp))
                    .collect(Collectors.toSet());
            Key key = buildKey(PREFIX_UNICAST, hostFcp.connectPoint(), l2Network.name(), host.mac(), appId);
            TrafficSelector selector = DefaultTrafficSelector.builder()
                    .matchEthDst(host.mac()).build();
            MultiPointToSinglePointIntent.Builder intentBuilder = MultiPointToSinglePointIntent.builder()
                    .appId(appId)
                    .key(key)
                    .selector(selector)
                    .filteredIngressPoints(srcFcps)
                    .filteredEgressPoint(hostFcp)
                    .constraints(PARTIAL_FAILURE_CONSTRAINT)
                    .priority(SlsNetService.PRI_L2NETWORK_UNICAST)
                    .resourceGroup(resourceGroup);
            setEncap(intentBuilder, PARTIAL_FAILURE_CONSTRAINT, l2Network.encapsulationType());
            uniIntents.add(intentBuilder.build());
        });

        return uniIntents;
    }

    // Intent generate utilities

    private Key buildKey(String prefix, ConnectPoint cPoint, String l2NetworkName,
                         MacAddress hostMac, ApplicationId appId) {
        return Key.of(l2NetworkName + SEPARATOR + prefix + SEPARATOR
                      + cPoint.deviceId() + SEPARATOR + cPoint.port() + SEPARATOR + hostMac,
                      appId);
    }

    private void setEncap(ConnectivityIntent.Builder builder,
                                 List<Constraint> constraints, EncapsulationType encap) {
        // Constraints might be an immutable list, so a new modifiable list is created
        List<Constraint> newConstraints = new ArrayList<>(constraints);
        constraints.stream()
                .filter(c -> c instanceof EncapsulationConstraint)
                .forEach(newConstraints::remove);
        if (!encap.equals(EncapsulationType.NONE)) {
            newConstraints.add(new EncapsulationConstraint(encap));
        }
        // Submit new constraint list as immutable list
        builder.constraints(ImmutableList.copyOf(newConstraints));
    }

    private FilteredConnectPoint buildFilteredConnectedPoint(Interface iface) {
        Objects.requireNonNull(iface);
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();

        if (iface.vlan() != null && !iface.vlan().equals(VlanId.NONE)) {
            trafficSelector.matchVlanId(iface.vlan());
        }
        return new FilteredConnectPoint(iface.connectPoint(), trafficSelector.build());
    }

    protected FilteredConnectPoint buildFilteredConnectedPoint(Host host) {
        Objects.requireNonNull(host);
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();

        if (host.vlan() != null && !host.vlan().equals(VlanId.NONE)) {
            trafficSelector.matchVlanId(host.vlan());
        }
        return new FilteredConnectPoint(host.location(), trafficSelector.build());
    }

    // Dump command handler
    private void dump(String subject) {
        if (subject == "intents") {
            System.out.println("l2NetworkIntents:\n");
            for (Intent intent: l2NetworkIntents) {
                System.out.println("    " + intent.key().toString());
            }
            System.out.println("");
            System.out.println("toBePurgedIntentKeys:\n");
            for (Key key: toBePurgedIntentKeys) {
                System.out.println("    " + key.toString());
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
                refresh();
                break;
            case SLSNET_IDLE:
                checkIntentsPurge();
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
