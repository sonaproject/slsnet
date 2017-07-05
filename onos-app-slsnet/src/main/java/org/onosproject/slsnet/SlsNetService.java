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

import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onlab.packet.MacAddress;
//import org.onosproject.net.ConnectPoint;

import java.util.Set;
import java.util.Collection;

/**
 * Provides information about the routing configuration.
 */
public interface SlsNetService {

    String APP_ID = "org.onosproject.slsnet";

   /**
    * Gets all the  l2Networks.
    *
    * @return all the l2Networks
    */
    Collection<VplsData> getAllVpls();

    /**
     * Evaluates whether an IP address is a virtual gateway IP address.
     *
     * @param ipAddress the IP address to evaluate
     * @return true if the IP address is a virtual gateway address, otherwise false
     */
    boolean isVirtualGatewayIpAddress(IpAddress ipAddress);

    /**
     * Evaluates whether an IP address belongs to local SDN network.
     *
     * @param ipAddress the IP address to evaluate
     * @return true if the IP address belongs to local SDN network, otherwise false
     */
    boolean isIpAddressLocal(IpAddress ipAddress);

    /**
     * Evaluates whether an IP prefix belongs to local SDN network.
     *
     * @param ipPrefix the IP prefix to evaluate
     * @return true if the IP prefix belongs to local SDN network, otherwise false
     */
    boolean isIpPrefixLocal(IpPrefix ipPrefix);

    /**
     * Get Virtual Gateway Mac Address for Local Subnet Virtual Router
     * and also for myself to communicate with bgp Peers.
     *
     * @return mac address of virtual gateway
     */
    MacAddress getVirtualGatewayMacAddress();

    /**
     * Retrieves the entire set of connect points connected to BGP peers in the
     * network.
     *
     * @return the set of connect points connected to BGP peers
     */
    Set<String> getRouteInterfaces();

}
