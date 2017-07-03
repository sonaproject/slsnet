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
 * Configuration details for an Route entry.
 */
public class RouteEntry {
    private final String desc;
    private final IpPrefix prefix;
    private final IpAddress gatewayIp;
    /* SLSNET side's virtual address to communicated with external gateway */
    private final IpAddress localIp;
    private final MacAddress localMac;

    /**
     * Creates a new IP prefix entry.
     *
     * @param desc        description
     * @param interfaces  interfaces name list
     * @param prefix      IP prefix
     * @param gatewayIp   IP of the gateway (external node)
     * @param localIp     IP of local side
     * @param localMac    MAC of local side
     */
    public RouteEntry(String desc,
                      IpPrefix prefix,
                      IpAddress gatewayIp,
                      IpAddress localIp,
                      MacAddress localMac) {
        this.desc = desc;
        this.interfaces = interfaces;
        this.prefix = prefix;
        this.gatewayIp = gatewayIp;
        this.localIp = localIp;
        this.localMac = localMac;
    }

    /**
     * Gets the desc of the route entry.
     *
     * @return the desc
     */
    public String desc() {
        return desc;
    }

    /**
     * Gets the prefix of the route entry.
     *
     * @return the IP prefix
     */
    public IpPrefix prefix() {
        return prefix;
    }

    /**
     * Gets the gateway IP address of the route entry.
     *
     * @return the gateway IP address
     */
    public IpAddress getGatewayIp() {
        return gatewayIp;
    }

    /**
     * Gets the local side IP address of the route entry.
     *
     * @return the local side  Mac address
     */
    public IpAddress getLocalIp() {
        return localIp;
    }

    /**
     * Gets the local side Mac address of the route entry.
     *
     * @return the local side  Mac address
     */
    public IpAddress getLocalMac() {
        return localMac;
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
        if (!(obj instanceof RouteEntry)) {
            return false;
        }
        RouteEntry that = (RouteEntry) obj;
        return (Objects.equals(this.desc, that.desc)
                && Objects.equals(this.prefix, that.prefix)
                && Objects.equals(this.gatewayIp, that.gatewayIp)
                && Objects.equals(this.localIp, that.localIp)
                && Objects.equals(this.localMac, that.localMac));
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("desc", desc)
                .add("prefix", prefix)
                .add("gatewayIp", gatewayIp)
                .add("localIp", localIp)
                .add("localMac", localMac)
                .toString();
    }
}
