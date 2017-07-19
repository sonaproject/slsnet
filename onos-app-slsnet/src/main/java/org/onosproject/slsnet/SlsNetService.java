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
import org.onlab.packet.VlanId;
import org.onosproject.core.ApplicationId;
import org.onosproject.event.ListenerService;
import org.onosproject.incubator.net.intf.Interface;
import org.onosproject.incubator.net.routing.Route;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Host;

import java.util.Set;
import java.util.Collection;

/**
 * Provides information about the routing configuration.
 */
public interface SlsNetService
        extends ListenerService<SlsNetEvent, SlsNetListener> {

    static final String APP_ID = "org.onosproject.slsnet";

    // priority for l2NetworkRouting: L2NETWORK_UNICAST or L2NETWORK_BROADCAST
    static final int PRI_L2NETWORK_UNICAST   = 701;
    static final int PRI_L2NETWORK_BROADCAST = 700;

    // priority for reactiveRouting: REACTIVE_BASE + ipPrefix * REACTIVE_STEP
    //                               + REACTIVE_ACTION or REACTIVE_INTERCEPT
    static final int PRI_REACTIVE_BASE = 400;
    static final int PRI_REACTIVE_STEP = 2;
    static final int PRI_REACTIVE_ACTION = 1;
    static final int PRI_REACTIVE_INTERCEPT = 0;

    // priority for borderRouting: BORDER_ROUTE_BBASE + ipPrefix * BORDER_ROUTE_STEP
    static final int PRI_BORDER_ROUTE_BASE  = 100;
    static final int PRI_BORDER_ROUTE_STEP  = 1;

    /**
     * Gets appId.
     *
     * @return appId of slsnet app
     */
    ApplicationId getAppId();

    /**
     * Gets all the  l2Networks.
     *
     * @return all the l2Networks
     */
    Collection<L2Network> getL2Networks();

    /**
     * Retrieves the entire set of ip4Subnets configuration.
     *
     * @return the set of IpSubnet for ip4Subnets
     */
    Set<IpSubnet> getIp4Subnets();

    /**
     * Retrieves the entire set of ip6Subnets configuration.
     *
     * @return the set of IpSubnet for ip6Subnets
     */
    Set<IpSubnet> getIp6Subnets();

    /**
     * Retrieves the entire set of Interface names connected to BGP peers in the
     * network.
     *
     * @return the set of connect points connected to BGP peers
     */
    Set<Route> getBorderRoutes();

    /**
     * Get Virtual Gateway Mac Address for Local Subnet Virtual Gateway.
     *
     * @return mac address of virtual gateway
     */
    MacAddress getVirtualGatewayMacAddress();

    /**
     * Get Virtual Gateway Ip Addresses for Local Subnet Virtual Gateway.
     *
     * @return ip addresses of virtual gateway from ipSubnets
     */
    Set<IpAddress> getVirtualGatewayIpAddresses();

    /**
     * Evaluates whether an Interface belongs to l2Networks.
     *
     * @param intf the interface to evaluate
     * @return true if the inteface belongs to l2Networks configed, otherwise false
     */
    boolean isL2NetworkInterface(Interface intf);

    /**
     * Finds the L2 Network with given port and vlanId.
     *
     * @param port the port to be matched
     * @param vlanId the vlanId to be matched
     * @return the L2 Network for specific port and vlanId or null
     */
    L2Network findL2Network(ConnectPoint port, VlanId vlanId);

    /**
     * Finds the L2 Network of the name.
     *
     * @param name the name to be matched
     * @return the L2 Network for specific name
     */
    L2Network findL2Network(String name);

    /**
     * Finds the IpSubnet containing the ipAddress.
     *
     * @param ipAddress the ipAddress to be matched
     * @return the IpSubnet for specific ipAddress
     */
    IpSubnet findIpSubnet(IpAddress ipAddress);

    /**
     * Finds the network interface related to the host.
     *
     * @param host the host
     * @return the interface related to the host
     */
    Interface getHostInterface(Host host);

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
     * Send Neighbour Query (ARP or NDP) to Find Host Location.
     *
     * @param ip the ip address to resolve
     * @return true if request mac packets are emitted. otherwise false
     */
    boolean requestMac(IpAddress ip);

}
