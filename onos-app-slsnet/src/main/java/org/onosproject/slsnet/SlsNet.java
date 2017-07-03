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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableList;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultByteArrayNodeFactory;
import com.googlecode.concurrenttrees.radixinverted.ConcurrentInvertedRadixTree;
import com.googlecode.concurrenttrees.radixinverted.InvertedRadixTree;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip6Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onosproject.app.ApplicationService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.incubator.component.ComponentService;
import org.onosproject.net.ConnectPoint;
import org.onosproject.incubator.net.intf.InterfaceService;
import org.onosproject.incubator.net.routing.Route;
import org.onosproject.incubator.net.routing.RouteAdminService;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.config.basics.SubjectFactories;
import org.onosproject.routing.RoutingService;
import org.onosproject.routing.config.BgpConfig;
import org.onosproject.intentsync.IntentSynchronizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;

import static org.onosproject.incubator.net.routing.RouteTools.createBinaryString;


/**
 * Reactive routing configuration manager.
 */
@Component(immediate = true)
public class SlsNet {

    public static final String APP_ID = "org.onosproject.slsnet";

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry registry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigService configService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected InterfaceService interfaceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected RouteAdminService routeService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected IntentSynchronizationService intentSynchronizer;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ApplicationService applicationService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ComponentService componentService;

    private ApplicationId appId;

    private Set<IpAddress> gatewayIpAddresses = new HashSet<>();
    private Set<ConnectPoint> bgpPeerConnectPoints = new HashSet<>();

    private InvertedRadixTree<LocalIpPrefixEntry>
            localPrefixTable4 = new ConcurrentInvertedRadixTree<>(
                    new DefaultByteArrayNodeFactory());
    private InvertedRadixTree<LocalIpPrefixEntry>
            localPrefixTable6 = new ConcurrentInvertedRadixTree<>(
                    new DefaultByteArrayNodeFactory());

    private MacAddress virtualGatewayMacAddress;
    private final InternalNetworkConfigListener configListener =
            new InternalNetworkConfigListener();

    private PeerConnectivityManager peerConnectivity;

    private final List<String> components = ImmutableList.of(
            "org.onosproject.routing.bgp.BgpSessionManager",
            "org.onosproject.routing.impl.BgpSpeakerNeighbourHandler",
            org.onosproject.slsnet.SdnIpFib.class.getName()
    );

    private ConfigFactory<ApplicationId, SlsNetConfig>
            reactiveRoutingConfigFactory =
            new ConfigFactory<ApplicationId, SlsNetConfig>(
                    SubjectFactories.APP_SUBJECT_FACTORY,
                    SlsNetConfig.class, "ipNetwork") {
        @Override
        public SlsNetConfig createConfig() {
            return new SlsNetConfig();
        }
    };

    @Activate
    public void activate() {
        configService.addListener(configListener);
        registry.registerConfigFactory(reactiveRoutingConfigFactory);
        setUpConfiguration();

        appId = coreService.registerApplication(APP_ID);
        components.forEach(name -> componentService.activate(appId, name));
        peerConnectivity = new PeerConnectivityManager(appId,
                                                       intentSynchronizer,
                                                       configService,
                coreService.registerApplication(RoutingService.ROUTER_APP_ID),
                                                       interfaceService);
        peerConnectivity.start();
        applicationService.registerDeactivateHook(appId,
                () -> intentSynchronizer.removeIntentsByAppId(appId));

        log.info("SlsNet started");
    }

    @Deactivate
    public void deactivate() {
        registry.unregisterConfigFactory(reactiveRoutingConfigFactory);
        configService.removeListener(configListener);

        components.forEach(name -> componentService.deactivate(appId, name));
        peerConnectivity.stop();

        log.info("SlsNet stopped");
    }

    /**
     * Set up reactive routing information from configuration.
     */
    private void setUpConfiguration() {
        SlsNetConfig config = configService.getConfig(
                coreService.registerApplication(APP_ID),
                SlsNetConfig.class);
        if (config == null) {
            log.warn("No reactive routing config available!");
            return;
        }
        for (LocalIpPrefixEntry entry : config.localIp4PrefixEntries()) {
            localPrefixTable4.put(createBinaryString(entry.ipPrefix()), entry);
            gatewayIpAddresses.add(entry.getGatewayIpAddress());
        }
        for (LocalIpPrefixEntry entry : config.localIp6PrefixEntries()) {
            localPrefixTable6.put(createBinaryString(entry.ipPrefix()), entry);
            gatewayIpAddresses.add(entry.getGatewayIpAddress());
        }

        virtualGatewayMacAddress = config.virtualGatewayMacAddress();

        // Setup BGP peer connect points
        ApplicationId routerAppId = coreService.getAppId(RoutingService.ROUTER_APP_ID);
        if (routerAppId == null) {
            log.info("Router application ID is null!");
            return;
        }

        BgpConfig bgpConfig = configService.getConfig(routerAppId, BgpConfig.class);

        if (bgpConfig == null) {
            /*log.info("BGP config is null!"); */
            return;
        } else {
            bgpPeerConnectPoints =
                    bgpConfig.bgpSpeakers().stream()
                    .flatMap(speaker -> speaker.peers().stream())
                    .map(peer -> interfaceService.getMatchingInterface(peer))
                    .filter(Objects::nonNull)
                    .map(intf -> intf.connectPoint())
                    .collect(Collectors.toSet());
        }
    }

    public boolean isIpAddressLocal(IpAddress ipAddress) {
        if (ipAddress.isIp4()) {
            return localPrefixTable4.getValuesForKeysPrefixing(
                    createBinaryString(
                    IpPrefix.valueOf(ipAddress, Ip4Address.BIT_LENGTH)))
                    .iterator().hasNext();
        } else {
            return localPrefixTable6.getValuesForKeysPrefixing(
                    createBinaryString(
                    IpPrefix.valueOf(ipAddress, Ip6Address.BIT_LENGTH)))
                    .iterator().hasNext();
        }
    }

    /* ipRoutes config event handling */
    private void processRouteConfigAdded(NetworkConfigEvent event) {
        Set<Route> routes = ((SlsNetConfig) event.config().get()).getRoutes();
        routeService.update(routes);
    }

    private void processRouteConfigUpdated(NetworkConfigEvent event) {
        Set<Route> routes = ((SlsNetConfig) event.config().get()).getRoutes();
        Set<Route> prevRoutes = ((SlsNetConfig) event.prevConfig().get()).getRoutes();
        Set<Route> pendingRemove = prevRoutes.stream()
                .filter(prevRoute -> routes.stream()
                        .noneMatch(route -> route.prefix().equals(prevRoute.prefix())))
                .collect(Collectors.toSet());
        Set<Route> pendingUpdate = routes.stream()
                .filter(route -> !pendingRemove.contains(route)).collect(Collectors.toSet());
        routeService.update(pendingUpdate);
        routeService.withdraw(pendingRemove);
    }

    private void processRouteConfigRemoved(NetworkConfigEvent event) {
        Set<Route> prevRoutes = ((SlsNetConfig) event.prevConfig().get()).getRoutes();
        routeService.withdraw(prevRoutes);
    }

    public boolean isIpPrefixLocal(IpPrefix ipPrefix) {
        return (localPrefixTable4.getValueForExactKey(
                createBinaryString(ipPrefix)) != null ||
                localPrefixTable6.getValueForExactKey(
                createBinaryString(ipPrefix)) != null);
    }

    public boolean isVirtualGatewayIpAddress(IpAddress ipAddress) {
        return gatewayIpAddresses.contains(ipAddress);
    }

    public MacAddress getVirtualGatewayMacAddress() {
        return virtualGatewayMacAddress;
    }

    public Set<ConnectPoint> getBgpPeerConnectPoints() {
        return ImmutableSet.copyOf(bgpPeerConnectPoints);
    }

    private class InternalNetworkConfigListener implements NetworkConfigListener {

        @Override
        public void event(NetworkConfigEvent event) {
            if (event.configClass().equals(SlsNetConfig.class)) {
                switch (event.type()) {
                case CONFIG_ADDED:
                    setUpConfiguration();
                    processRouteConfigAdded(event);
                    break;

                case CONFIG_UPDATED:
                    setUpConfiguration();
                    processRouteConfigUpdated(event);
                    break;

                case CONFIG_REMOVED:
                    setUpConfiguration();
                    processRouteConfigRemoved(event);
                    break;

                default:
                    break;
                }
            }
        }
    }
}

