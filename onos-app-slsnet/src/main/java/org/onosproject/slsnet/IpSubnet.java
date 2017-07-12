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
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;

import java.util.Objects;

/**
 * Configuration details for an IP prefix entry.
 */
public class IpSubnet {
    private final IpPrefix ipPrefix;
    private final IpAddress gatewayIp;

    /**
     * Creates a new IP prefix entry.
     *
     * @param ipPrefix  an IP prefix
     * @param gatewayIp IP of the gateway
     */
    public IpSubnet(IpPrefix ipPrefix, IpAddress gatewayIp) {
        this.ipPrefix = ipPrefix;
        this.gatewayIp = gatewayIp;
    }

    /**
     * Gets the IP prefix of the IP prefix entry.
     *
     * @return the IP prefix
     */
    public IpPrefix ipPrefix() {
        return ipPrefix;
    }

    /**
     * Gets the gateway IP address of the IP prefix entry.
     *
     * @return the gateway IP address
     */
    public IpAddress getGatewayIp() {
        return gatewayIp;
    }

    /**
     * Tests whether the IP version of this entry is IPv4.
     *
     * @return true if the IP version of this entry is IPv4, otherwise false.
     */
    public boolean isIp4() {
        return ipPrefix.isIp4();
    }

    /**
     * Tests whether the IP version of this entry is IPv6.
     *
     * @return true if the IP version of this entry is IPv6, otherwise false.
     */
    public boolean isIp6() {
        return ipPrefix.isIp6();
    }

    @Override
    public int hashCode() {
        return Objects.hash(ipPrefix);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof IpSubnet)) {
            return false;
        }
        IpSubnet that = (IpSubnet) obj;
        return Objects.equals(this.ipPrefix, that.ipPrefix)
               && Objects.equals(this.gatewayIp, that.gatewayIp);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("ipPrefix", ipPrefix)
                .add("gatewayIp", gatewayIp)
                .toString();
    }
}
