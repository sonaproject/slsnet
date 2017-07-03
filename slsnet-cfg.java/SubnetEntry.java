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

import com.google.common.base.MoreObjects;
import org.onlab.packet.MacAddress;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;

import java.util.Objects;

/**
 * Configuration details for an Subnet entry.
 */
public class RouteEntry {
    private final String desc;
    private final Set<String> interfaces,
    private final IpPrefix prefix;
    private final IpAddress gatewayIp;
    private final MacAddress gatewayMac;

    /**
     * Creates a new IP prefix entry.
     *
     * @param desc        description
     * @param interfaces  interfaces name list
     * @param prefix      IP prefix
     * @param gatewayIp   IP of the gateway
     * @param gatewayMac  MAC of the gateway
     */
    public SubnetEntry(String desc,
                       Set<String> interfaces,
                       IpPrefix prefix,
                       IpAddress gatewayIp,
                       MacAddress gatewayMac) {
        this.desc = desc;
        this.interfaces = interfaces;
        this.prefix = prefix;
        this.gatewayIp = gatewayIp;
        this.gatewayMac = gatewayMac;
    }

    /**
     * Gets the desc of the subnet entry.
     *
     * @return the desc
     */
    public String desc() {
        return desc;
    }

    /**
     * Gets the interfaces of the subnet entry.
     *
     * @return the intefaces
     */
    public Set<String> interfaces() {
        return intefaces;
    }

    /**
     * Gets the prefix of the subnet entry.
     *
     * @return the IP prefix
     */
    public IpPrefix prefix() {
        return prefix;
    }

    /**
     * Gets the subnet virtual gateway IP address of the subnet entry.
     *
     * @return the subnet virtual gateway IP address
     */
    public IpAddress getGatewayIp() {
        return gatewayIp;
    }

    /**
     * Gets the subnet virtual gateway Mac address of the subnet entry.
     *
     * @return the subnet virtual gateway Mac address
     */
    public IpAddress getGatewayMac() {
        return gatewayMac;
    }

    @Override
    public int hashCode() {
        return Objects.hash(prefix, type);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof SubnetEntry)) {
            return false;
        }
        LocalIpPrefixEntry that = (LocalIpPrefixEntry) obj;
        return Objects.equals(this.desc, that.desc)
               && Objects.equals(this.interfaces, that.interfaces)
               && Objects.equals(this.prefix, that.prefix)
               && Objects.equals(this.gatewayIp, that.gatewayIp)
               && Objects.equals(this.gatewayMac, that.gatewayMac);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("desc", desc)
                .add("interfaces", interfaces)
                .add("prefix", prefix)
                .add("gatewayIp", gatewayIp)
                .add("gatewayMac", gatewayMac)
                .toString();
    }
}
