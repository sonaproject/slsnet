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

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Sets;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import org.onosproject.net.EncapsulationType;
import org.onosproject.incubator.net.routing.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Configuration object for prefix config.
 */
public class SlsNetConfig extends Config<ApplicationId> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final String L2NETWORKS = "l2Networks";
    private static final String NAME = "name";
    private static final String INTERFACES = "interfaces";
    private static final String ENCAPSULATION = "encapsulation";
    private static final String L2FORWARDING = "l2Forwarding";
    private static final String IP4SUBNETS = "ip4Subnets";
    private static final String IP6SUBNETS = "ip6Subnets";
    private static final String BORDERROUTES = "borderRoutes";
    private static final String IPPREFIX = "ipPrefix";
    private static final String GATEWAYIP = "gatewayIp";
    private static final String L2NETWORKNAME = "l2NetworkName";
    private static final String VIRTUALGATEWAYMACADDRESS =
                               "virtualGatewayMacAddress";

    /**
     * Returns all l2Networks in this configuration.
     *
     * @return A set of L2Network.
     */
    public Set<L2Network> getL2Networks() {
        Set<L2Network> l2Networks = Sets.newHashSet();
        JsonNode l2NetworkNode = object.get(L2NETWORKS);
        if (l2NetworkNode == null) {
            return l2Networks;
        }

        l2NetworkNode.forEach(jsonNode -> {
            String name = jsonNode.get(NAME).asText();

            Set<String> ifaces = Sets.newHashSet();
            JsonNode l2NetworkIfaces = jsonNode.path(INTERFACES);
            if (l2NetworkIfaces == null) {
                log.warn("slsnet network config cannot find {}; skip: {}", INTERFACES, jsonNode);
            } else if (!l2NetworkIfaces.toString().isEmpty()) {
                l2NetworkIfaces.forEach(ifacesNode -> ifaces.add(new String(ifacesNode.asText())));
            }

            EncapsulationType encap = EncapsulationType.NONE;
            if (jsonNode.hasNonNull(ENCAPSULATION)) {
                encap = EncapsulationType.enumFromString(jsonNode.get(ENCAPSULATION).asText());
            }

            boolean l2Forwarding = true;
            if (jsonNode.hasNonNull(L2FORWARDING)) {
                l2Forwarding = jsonNode.get(L2FORWARDING).asBoolean();
            }

            //l2Networks.add(new L2Network(name, ifaces, EncapsulationType.NONE, l2Forwarding));
            l2Networks.add(new L2Network(name, ifaces, encap, l2Forwarding));
        });
        return l2Networks;
    }

    /**
     * Gets the set of configured local IPv4 prefixes.
     *
     * @return IPv4 prefixes
     */
    public Set<IpSubnet> ip4Subnets() {
        Set<IpSubnet> subnets = Sets.newHashSet();

        JsonNode subnetsNode = object.get(IP4SUBNETS);
        if (subnetsNode == null) {
            log.warn("slsnet network config ip4Subnets is null!");
            return subnets;
        }

        subnetsNode.forEach(jsonNode -> {
            try {
                subnets.add(new IpSubnet(
                        IpPrefix.valueOf(jsonNode.get(IPPREFIX).asText()),
                        IpAddress.valueOf(jsonNode.get(GATEWAYIP).asText()),
                        jsonNode.get(L2NETWORKNAME).asText()));
            } catch (IllegalArgumentException e) {
                log.warn("slsnet network config parse error; skip: {}", jsonNode);
            }
        });

        return subnets;
    }

    /**
     * Gets the set of configured local IPv6 prefixes.
     *
     * @return IPv6 prefixes
     */
    public Set<IpSubnet> ip6Subnets() {
        Set<IpSubnet> subnets = Sets.newHashSet();

        JsonNode subnetsNode = object.get(IP6SUBNETS);
        if (subnetsNode == null) {
            /* NOTE: no warning for ip6 case is not implemented */
            /*log.warn("ip6LocalPrefixes is null!"); */
            return subnets;
        }

        subnetsNode.forEach(jsonNode -> {
            try {
                subnets.add(new IpSubnet(
                        IpPrefix.valueOf(jsonNode.get(IPPREFIX).asText()),
                        IpAddress.valueOf(jsonNode.get(GATEWAYIP).asText()),
                        jsonNode.get(L2NETWORKNAME).asText()));
            } catch (IllegalArgumentException e) {
                log.warn("slsnet network config parse error; skip: {}", jsonNode);
            }
        });

        return subnets;
    }

    /**
     * Returns all routes in this configuration.
     *
     * @return A set of route.
     */
    public Set<Route> borderRoutes() {
        Set<Route> routes = Sets.newHashSet();

        JsonNode routesNode = object.get(BORDERROUTES);
        if (routesNode == null) {
            log.warn("slsnet network config borderRoutes is null!");
            return routes;
        }

        routesNode.forEach(jsonNode -> {
            try {
                routes.add(new Route(
                      Route.Source.STATIC,
                      IpPrefix.valueOf(jsonNode.path(IPPREFIX).asText()),
                      IpAddress.valueOf(jsonNode.path(GATEWAYIP).asText())));
            } catch (IllegalArgumentException e) {
                log.warn("slsnet network config parse error; skip: {}", jsonNode);
            }
        });

        return routes;
    }

    /**
     *  Gets of the virtual gateway MAC address.
     *
     * @return virtual gateway MAC address
     */
    public MacAddress virtualGatewayMacAddress() {
        JsonNode macNode = object.get(VIRTUALGATEWAYMACADDRESS);
        if (macNode == null) {
            return null;
        }
        return MacAddress.valueOf(macNode.asText());
    }

}
