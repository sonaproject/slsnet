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
import org.onlab.packet.MacAddress;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Configuration object for subnet and route config.
 */
public class SLSNetNetworkConfig extends Config<ApplicationId> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    public static final String SUBNETS    = "subnets";
    public static final String ROUTES     = "routes";

    public static final String DESC       = "desc";
    public static final String INTERFACES = "interfaces";
    public static final String PREFIX     = "prefix";
    public static final String GATEWAYIP  = "gatewayIp";
    public static final String GATEWAYMAC = "gatewayMac";

    /**
     * Gets the set of configured subnets.
     *
     * @return Subnets
     */
    public Set<SubnetEntry> SubnetEntries() {
        Set<SubnetEntry> subnets = Sets.newHashSet();

        JsonNode subnetsNode = object.get(SUBNETS);
        if (subnetsNode == null) {
            log.warn("subnets is null!");
            return subnets;
        }

        subnetsNode.forEach(jsonNode -> {
            Set<String> interfaces = Sets.newHashSet();

            jsoneNode interfacesNode = object.get(INTERFACES);
            if (subnetsNode == null)
                log.warn("interfaces is null! - go ahead");
            else 
                interfacesNode.forEach(ifNode -> {
                    interfaces.add(new String(ifNode.asText()));
                });

            subnets.add(new SubnetEntry(
                        jsonNode.get(DESC).asText(),
                        interfaes,
                        IpPrefix.valueOf(jsonNode.get(PREFIX).asText()),
                        IpAddress.valueOf(jsonNode.get(GATEWAYIP).asText()),
                        MacAddress.valueOf(jsonNode.get(GATEWAYMAC).asText())));
        });

        return subnets;
    }

    /**
     * Gets the set of configured routes.
     *
     * @return Routes
     */
    public Set<RouteEntry> RouteEntries() {
        Set<RouteEntry> routes = Sets.newHashSet();

        JsonNode routesNode = object.get(ROUTES);
        if (routesNode == null) {
            log.warn("routes is null!");
            return routes;
        }

        routesNode.forEach(jsonNode -> {
            prefixes.add(new RoutesEntry(
                         jsoneNode.get(DESC).asText(),
                         IpPrefix.valueOf(jsonNode.get(IPPREFIX).asText()),
                         IpAddress.valueOf(jsonNode.get(GATEWAYIP).asText())));
        });

        return prefixes;
    }

}
