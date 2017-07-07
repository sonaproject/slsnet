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
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onosproject.core.ApplicationId;
import org.onosproject.incubator.net.intf.Interface;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.EncapsulationType;
import org.onosproject.net.FilteredConnectPoint;
import org.onosproject.net.Host;
import org.onosproject.net.ResourceGroup;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.intent.ConnectivityIntent;
import org.onosproject.net.intent.Constraint;
import org.onosproject.net.intent.Intent;
import org.onosproject.net.intent.Key;
import org.onosproject.net.intent.MultiPointToSinglePointIntent;
import org.onosproject.net.intent.SinglePointToMultiPointIntent;
import org.onosproject.net.intent.constraint.EncapsulationConstraint;
import org.onosproject.net.intent.constraint.PartialFailureConstraint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.onosproject.net.EncapsulationType.*;

/**
 * Intent utilities for L2 Network.
 */
public final class SlsNetL2NetworkIntent {

    public static final String PREFIX_BROADCAST = "brc";
    public static final String PREFIX_UNICAST = "uni";
    private static final String SEPARATOR = "-";

    private static final Logger log = LoggerFactory.getLogger(SlsNetL2NetworkIntent.class);

    public static final ImmutableList<Constraint> PARTIAL_FAILURE_CONSTRAINT =
            ImmutableList.of(new PartialFailureConstraint());

    private SlsNetL2NetworkIntent() {
        // Utility classes should not have a public or default constructor.
    }

    /**
     * Builds broadcast Intents for a L2 Network.
     *
     * @param l2Network the L2 Network
     * @param appId the application id for Intents
     * @return broadcast Intents for the L2 Network
     */
    public static Set<Intent> buildBrcIntents(L2Network l2Network, ApplicationId appId) {
        Set<Interface> interfaces = l2Network.interfaces();

        // At least two or more network interfaces to build broadcast Intents
        if (interfaces.size() < 2) {
            return ImmutableSet.of();
        }
        Set<Intent> brcIntents = Sets.newHashSet();
        ResourceGroup resourceGroup = ResourceGroup.of(l2Network.name());

        // Generates broadcast Intents from any network interface to other
        // network interface from the L2 Network.
        interfaces.forEach(src -> {
            FilteredConnectPoint srcFcp = SlsNetL2NetworkIntent.buildFilteredConnectedPoint(src);
            Set<FilteredConnectPoint> dstFcps =
                    interfaces.stream()
                            .filter(iface -> !iface.equals(src))
                            .map(SlsNetL2NetworkIntent::buildFilteredConnectedPoint)
                            .collect(Collectors.toSet());
            Key key = SlsNetL2NetworkIntent.buildKey(PREFIX_BROADCAST,
                                               srcFcp.connectPoint(),
                                               l2Network.name(),
                                               MacAddress.BROADCAST,
                                               appId);
            Intent brcIntent = buildBrcIntent(key,
                                              appId,
                                              srcFcp,
                                              dstFcps,
                                              l2Network.encapsulationType(),
                                              resourceGroup);

            brcIntents.add(brcIntent);
        });
        return brcIntents;
    }

    /**
     * Builds a broadcast intent.
     *
     * @param key key to identify the intent
     * @param appId application ID for this Intent
     * @param src the source connect point
     * @param dsts the destination connect points
     * @param encap the encapsulation type
     * @param resourceGroup resource group for this Intent
     * @return the generated single-point to multi-point intent
     */
    protected static SinglePointToMultiPointIntent buildBrcIntent(Key key,
                                                                  ApplicationId appId,
                                                                  FilteredConnectPoint src,
                                                                  Set<FilteredConnectPoint> dsts,
                                                                  EncapsulationType encap,
                                                                  ResourceGroup resourceGroup) {
        log.debug("Building broadcast intent for source {}", src);

        SinglePointToMultiPointIntent.Builder intentBuilder;

        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthDst(MacAddress.BROADCAST)
                .build();

        intentBuilder = SinglePointToMultiPointIntent.builder()
                .appId(appId)
                .key(key)
                .selector(selector)
                .filteredIngressPoint(src)
                .filteredEgressPoints(dsts)
                .constraints(PARTIAL_FAILURE_CONSTRAINT)
                .priority(SlsNetService.PRI_L2NETWORK_BROADCAST)
                .resourceGroup(resourceGroup);

        setEncap(intentBuilder, PARTIAL_FAILURE_CONSTRAINT, encap);

        return intentBuilder.build();
    }

    /**
     * Builds unicast Intents for a L2 Network.
     *
     * @param l2Network the L2 Network
     * @param hosts the hosts of the L2 Network
     * @param appId application ID for Intents
     * @return unicast Intents for the L2 Network
     */
    public static Set<Intent> buildUniIntents(L2Network l2Network, Set<Host> hosts, ApplicationId appId) {
        Set<Interface> interfaces = l2Network.interfaces();
        if (interfaces.size() < 2) {
            return ImmutableSet.of();
        }
        Set<Intent> uniIntents = Sets.newHashSet();
        ResourceGroup resourceGroup = ResourceGroup.of(l2Network.name());
        hosts.forEach(host -> {
            FilteredConnectPoint hostFcp = buildFilteredConnectedPoint(host);
            Set<FilteredConnectPoint> srcFcps =
                    interfaces.stream()
                            .map(SlsNetL2NetworkIntent::buildFilteredConnectedPoint)
                            .filter(fcp -> !fcp.equals(hostFcp))
                            .collect(Collectors.toSet());
            Key key = buildKey(PREFIX_UNICAST,
                               hostFcp.connectPoint(),
                               l2Network.name(),
                               host.mac(),
                               appId);
            Intent uniIntent = buildUniIntent(key,
                                              appId,
                                              srcFcps,
                                              hostFcp,
                                              host,
                                              l2Network.encapsulationType(),
                                              resourceGroup);
            uniIntents.add(uniIntent);
        });

        return uniIntents;
    }

    /**
     * Builds a unicast intent.
     *
     * @param key key to identify the intent
     * @param appId application ID for this Intent
     * @param srcs the source Connect Points
     * @param dst the destination Connect Point
     * @param host destination Host
     * @param encap the encapsulation type
     * @param resourceGroup resource group for this Intent
     * @return the generated multi-point to single-point intent
     */
    protected static MultiPointToSinglePointIntent buildUniIntent(Key key,
                                                                  ApplicationId appId,
                                                                  Set<FilteredConnectPoint> srcs,
                                                                  FilteredConnectPoint dst,
                                                                  Host host,
                                                                  EncapsulationType encap,
                                                                  ResourceGroup resourceGroup) {
        log.debug("Building unicast intent for destination {}", dst);

        MultiPointToSinglePointIntent.Builder intentBuilder;

        TrafficSelector selector = DefaultTrafficSelector.builder()
                .matchEthDst(host.mac())
                .build();

        intentBuilder = MultiPointToSinglePointIntent.builder()
                .appId(appId)
                .key(key)
                .selector(selector)
                .filteredIngressPoints(srcs)
                .filteredEgressPoint(dst)
                .constraints(PARTIAL_FAILURE_CONSTRAINT)
                .priority(SlsNetService.PRI_L2NETWORK_UNICAST)
                .resourceGroup(resourceGroup);

        setEncap(intentBuilder, PARTIAL_FAILURE_CONSTRAINT, encap);

        return intentBuilder.build();
    }

    /**
     * Builds an intent key either for single-point to multi-point or
     * multi-point to single-point intents, based on a prefix that defines
     * the type of intent, the single connect point representing the single
     * source or destination for that intent, the name of the L2Network the intent
     * belongs to, and the destination host MAC address the intent reaches.
     *
     * @param prefix the key prefix
     * @param cPoint the connect point identifying the source/destination
     * @param l2NetworkName the name of the L2 Network
     * @param hostMac the source/destination MAC address
     * @param appId application ID for the key
     * @return the key to identify the intent
     */
    protected static Key buildKey(String prefix,
                                  ConnectPoint cPoint,
                                  String l2NetworkName,
                                  MacAddress hostMac,
                                  ApplicationId appId) {
        String keyString = l2NetworkName +
                SEPARATOR +
                prefix +
                SEPARATOR +
                cPoint.deviceId() +
                SEPARATOR +
                cPoint.port() +
                SEPARATOR +
                hostMac;

        return Key.of(keyString, appId);
    }


    /**
     * Sets one or more encapsulation constraints on the intent builder given.
     *
     * @param builder the intent builder
     * @param constraints the existing intent constraints
     * @param encap the encapsulation type to be set
     */
    public static void setEncap(ConnectivityIntent.Builder builder,
                                List<Constraint> constraints,
                                EncapsulationType encap) {
        // Constraints might be an immutable list, so a new modifiable list
        // is created
        List<Constraint> newConstraints = new ArrayList<>(constraints);

        // Remove any encapsulation constraint if already in the list
        constraints.stream()
                .filter(c -> c instanceof EncapsulationConstraint)
                .forEach(newConstraints::remove);

        // if the new encapsulation is different from NONE, a new encapsulation
        // constraint should be added to the list
        if (!encap.equals(NONE)) {
            newConstraints.add(new EncapsulationConstraint(encap));
        }

        // Submit new constraint list as immutable list
        builder.constraints(ImmutableList.copyOf(newConstraints));
    }

    /**
     * Builds filtered connected point by a given network interface.
     *
     * @param iface the network interface
     * @return the filtered connected point of a given network interface
     */
    protected static FilteredConnectPoint buildFilteredConnectedPoint(Interface iface) {
        Objects.requireNonNull(iface);
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();

        if (iface.vlan() != null && !iface.vlan().equals(VlanId.NONE)) {
            trafficSelector.matchVlanId(iface.vlan());
        }

        return new FilteredConnectPoint(iface.connectPoint(), trafficSelector.build());
    }

    /**
     * Builds filtered connected point by a given host.
     *
     * @param host the host
     * @return the filtered connected point of the given host
     */
    protected static FilteredConnectPoint buildFilteredConnectedPoint(Host host) {
        Objects.requireNonNull(host);
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();

        if (host.vlan() != null && !host.vlan().equals(VlanId.NONE)) {
            trafficSelector.matchVlanId(host.vlan());
        }
        return new FilteredConnectPoint(host.location(), trafficSelector.build());
    }
}
