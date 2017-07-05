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
//import com.google.common.collect.Sets;
import com.googlecode.concurrenttrees.radix.node.concrete.DefaultByteArrayNodeFactory;
import com.googlecode.concurrenttrees.radixinverted.ConcurrentInvertedRadixTree;
import com.googlecode.concurrenttrees.radixinverted.InvertedRadixTree;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip6Address;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onosproject.app.ApplicationService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
//import org.onosproject.net.ConnectPoint;
import org.onosproject.incubator.net.intf.Interface;
import org.onosproject.incubator.net.intf.InterfaceService;
import org.onosproject.incubator.net.routing.Route;
import org.onosproject.incubator.net.routing.RouteAdminService;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.config.NetworkConfigService;
import org.onosproject.net.config.basics.SubjectFactories;
//import org.onosproject.routing.RoutingService;
import org.onosproject.intentsync.IntentSynchronizationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
//import java.util.Collection;
import java.util.stream.Collectors;

//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.TimeUnit;
//import java.util.stream.Collectors;

//import static org.onlab.util.Tools.groupedThreads;
import static org.onosproject.incubator.net.routing.RouteTools.createBinaryString;


/**
 * Reactive routing configuration manager.
 */
@Component(immediate = true)
@Service
public class SlsNet implements SlsNetService {

    private ApplicationId appId;

    public static final String APP_ID = "org.onosproject.slsnet";

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ApplicationService applicationService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigService configService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkConfigRegistry registry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected InterfaceService interfaceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected IntentSynchronizationService intentSynchronizer;

    // from VPLS
    /*
    private static final int INITIAL_RELOAD_CONFIG_DELAY = 0;
    private static final int INITIAL_RELOAD_CONFIG_PERIOD = 1000
    private static final int NUM_THREADS = 1;
    */

    // l2 broadcast networks
    private Set<VplsConfig> l2NetworkConfig = new HashSet<>();
    private Set<VplsData> l2NetworkTable = new HashSet<>();

    // Subnet table
    private Set<LocalIpPrefixEntry> localIp4PrefixEntries;
    private Set<LocalIpPrefixEntry> localIp6PrefixEntries;
    private InvertedRadixTree<LocalIpPrefixEntry>
            localPrefixTable4 = new ConcurrentInvertedRadixTree<>(
                    new DefaultByteArrayNodeFactory());
    private InvertedRadixTree<LocalIpPrefixEntry>
            localPrefixTable6 = new ConcurrentInvertedRadixTree<>(
                    new DefaultByteArrayNodeFactory());

    // Virtial Subnet gateway
    private MacAddress virtualGatewayMacAddress;
    private Set<IpAddress> gatewayIpAddresses = new HashSet<>();

    // Route table
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    private RouteAdminService routeService;

    // External router connectPoint from ipRouteInterface
    private Set<String> routeInterfaces = new HashSet<>();

    private final InternalNetworkConfigListener configListener =
            new InternalNetworkConfigListener();

    private ConfigFactory<ApplicationId, SlsNetConfig>
            reactiveRoutingConfigFactory =
            new ConfigFactory<ApplicationId, SlsNetConfig>(
                    SubjectFactories.APP_SUBJECT_FACTORY,
                    SlsNetConfig.class, "slsnet") {
        @Override
        public SlsNetConfig createConfig() {
            return new SlsNetConfig();
        }
    };

    @Activate
    public void activate() {
        appId = coreService.registerApplication(APP_ID);
        configService.addListener(configListener);
        registry.registerConfigFactory(reactiveRoutingConfigFactory);
        setUpConfiguration(null);
        log.info("SlsNet started");
    }

    @Deactivate
    public void deactivate() {
        registry.unregisterConfigFactory(reactiveRoutingConfigFactory);
        configService.removeListener(configListener);
        log.info("SlsNet stopped");
    }

    public ApplicationId getAppId() {
        return appId;
    }

    /**
     * Set up from configuration.
     */
    private void setUpConfiguration(NetworkConfigEvent event) {
        SlsNetConfig config = configService.getConfig(
                coreService.registerApplication(APP_ID),
                SlsNetConfig.class);
        if (config == null) {
            log.warn("No reactive routing config available!");
            return;
        }

        // l2Networks
        l2NetworkConfig = config.getL2Networks();
        l2NetworkTable = l2NetworkConfig.stream()
                .map(vplsConfig -> {
                    Set<Interface> interfaces = vplsConfig.ifaces().stream()
                            .map(this::getInterfaceByName)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet());
                    VplsData vplsData = VplsData.of(vplsConfig.name(), vplsConfig.encap());
                    vplsData.addInterfaces(interfaces);
                    return vplsData;
                }).collect(Collectors.toSet());

        // ipSubnet
        localIp4PrefixEntries = config.localIp4PrefixEntries();
        for (LocalIpPrefixEntry entry : localIp4PrefixEntries) {
            localPrefixTable4.put(createBinaryString(entry.ipPrefix()), entry);
            gatewayIpAddresses.add(entry.getGatewayIpAddress());
        }
        localIp6PrefixEntries = config.localIp6PrefixEntries();
        for (LocalIpPrefixEntry entry : localIp6PrefixEntries) {
            localPrefixTable6.put(createBinaryString(entry.ipPrefix()), entry);
            gatewayIpAddresses.add(entry.getGatewayIpAddress());
        }
        virtualGatewayMacAddress = config.virtualGatewayMacAddress();

        // ipRoutes config handling
        if (event == null) {
            // do not handle route info
        } else if (event.type() == NetworkConfigEvent.Type.CONFIG_ADDED) {
            Set<Route> routes = ((SlsNetConfig) event.config().get()).getRoutes();
            routeService.update(routes);
        } else if (event.type() == NetworkConfigEvent.Type.CONFIG_UPDATED) {
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
        } else if (event.type() == NetworkConfigEvent.Type.CONFIG_REMOVED) {
            Set<Route> prevRoutes = ((SlsNetConfig) event.prevConfig().get()).getRoutes();
            routeService.withdraw(prevRoutes);
        }

        /* ipRouteInterfaces */
        routeInterfaces = config.getRouteInterfaces();
    }

    private Interface getInterfaceByName(String interfaceName) {
        return interfaceService.getInterfaces().stream()
                .filter(iface -> iface.name().equals(interfaceName))
                .findFirst()
                .orElse(null);
    }

    @Override
    public Collection<VplsData> getAllVpls() {
        return ImmutableSet.copyOf(l2NetworkTable);
    }

    @Override
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

    @Override
    public boolean isIpPrefixLocal(IpPrefix ipPrefix) {
        return (localPrefixTable4.getValueForExactKey(
                createBinaryString(ipPrefix)) != null ||
                localPrefixTable6.getValueForExactKey(
                createBinaryString(ipPrefix)) != null);
    }

    @Override
    public boolean isVirtualGatewayIpAddress(IpAddress ipAddress) {
        return gatewayIpAddresses.contains(ipAddress);
    }

    @Override
    public Set<LocalIpPrefixEntry> getLocalIp4PrefixEntries() {
        return ImmutableSet.copyOf(localIp4PrefixEntries);
    }

    @Override
    public Set<LocalIpPrefixEntry> getLocalIp6PrefixEntries() {
        return ImmutableSet.copyOf(localIp6PrefixEntries);
    }

    @Override
    public MacAddress getVirtualGatewayMacAddress() {
        return virtualGatewayMacAddress;
    }

    @Override
    public Set<String> getRouteInterfaces() {
        return ImmutableSet.copyOf(routeInterfaces);
    }

    private class InternalNetworkConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {
            if (event.configClass().equals(SlsNetConfig.class)) {
                switch (event.type()) {
                case CONFIG_ADDED:
                case CONFIG_UPDATED:
                case CONFIG_REMOVED:
                    setUpConfiguration(event);
                    break;
                default:
                    break;
                }
            }
        }
    }
}

