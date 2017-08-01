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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.onlab.packet.VlanId;
import org.onosproject.incubator.net.intf.Interface;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Host;
import org.onosproject.net.HostId;
import org.onosproject.net.EncapsulationType;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * Class stores a L2Network information.
 */
public final class L2Network {

    private String name;                  // also for network configuration
    private Set<String> interfaceNames;   // also for network configuration
    private EncapsulationType encapsulation;  // also for network configuration
    private boolean l2Forward;            // do l2Forward (default:true) or not
    private Set<Interface> interfaces;    // available interfaces from interfaceNames
    private Set<HostId> hostIds;          // available hosts from interfaces
    private boolean dirty;

    /**
     * Constructs a L2Network data for Config value.
     *
     * @param name the given name
     * @param ifaceNames the interface names
     * @param encapsulation the encapsulation type
     * @param l2Forward flag for l2Forward intents to be installed or not
     */
    L2Network(String name, Collection<String> ifaceNames, EncapsulationType encapsulation, boolean l2Forward) {
        this.name = name;
        this.interfaceNames = Sets.newHashSet();
        this.addInterfaceNames(ifaceNames);
        this.encapsulation = encapsulation;
        this.l2Forward = (SlsNetService.ALLOW_ETH_ADDRESS_SELECTOR) ? l2Forward : false;
        this.interfaces = Sets.newHashSet();
        this.hostIds = Sets.newHashSet();
        this.dirty = false;
    }

    /**
     * Constructs a L2Network data by given name and encapsulation type.
     *
     * @param name the given name
     * @param encapType the encapsulation type
     */
    private L2Network(String name, EncapsulationType encaptulation) {
        this.name = name;
        this.interfaceNames = Sets.newHashSet();
        this.encapsulation = encapsulation;
        this.l2Forward = (SlsNetService.ALLOW_ETH_ADDRESS_SELECTOR) ? true : false;
        this.interfaces = Sets.newHashSet();
        this.hostIds = Sets.newHashSet();
        this.dirty = false;
    }

    /**
     * Creates a L2Network data by given name.
     * The encapsulation type of the L2Network will be NONE.
     *
     * @param name the given name
     * @return the L2Network data
     */
    public static L2Network of(String name) {
        Objects.requireNonNull(name);
        return new L2Network(name, EncapsulationType.NONE);
    }

    /**
     * Creates a copy of L2Network data.
     *
     * @param l2Network the L2Network data
     * @return the copy of the L2Network data
     */
    public static L2Network of(L2Network l2Network) {
        Objects.requireNonNull(l2Network);
        L2Network l2NetworkCopy = new L2Network(l2Network.name(), l2Network.encapsulation());
        l2NetworkCopy.addInterfaceNames(l2Network.interfaceNames());
        l2NetworkCopy.setEncapsulation(l2Network.encapsulation());
        l2NetworkCopy.setL2Forward((SlsNetService.ALLOW_ETH_ADDRESS_SELECTOR) ? l2Network.l2Forward() : false);
        l2NetworkCopy.addInterfaces(l2Network.interfaces());
        l2NetworkCopy.setDirty(l2Network.dirty());
        return l2NetworkCopy;
    }

    // field queries

    public String name() {
        return name;
    }

    public Set<String> interfaceNames() {
        return ImmutableSet.copyOf(interfaceNames);
    }

    public EncapsulationType encapsulation() {
        return encapsulation;
    }

    public boolean l2Forward() {
        return l2Forward;
    }

    public Set<Interface> interfaces() {
        return ImmutableSet.copyOf(interfaces);
    }

    public boolean contains(Interface iface) {
        return interfaces.contains(iface);
    }

    public boolean contains(ConnectPoint port, VlanId vlanId) {
        for (Interface iface : interfaces) {
            if (iface.connectPoint().equals(port) && iface.vlan().equals(vlanId)) {
                return true;
            }
        }
        return false;
    }

    public boolean contains(DeviceId deviceId) {
        for (Interface iface : interfaces) {
            if (iface.connectPoint().deviceId().equals(deviceId)) {
                return true;
            }
        }
        return false;
    }

    public Set<HostId> hostIds() {
        return ImmutableSet.copyOf(hostIds);
    }

    public boolean hostIdsContains(Host host) {
        return hostIds.contains(host.id());
    }
    public boolean dirty() {
        return dirty;
    }

    // field updates

    public void setEncapsulation(EncapsulationType encapsulation) {
        if (!encapsulation.equals(this.encapsulation)) {
            this.encapsulation = encapsulation;
            setDirty(true);
        }
    }

    // add only for interfaceName configuration values
    public void addInterfaceNames(Collection<String> ifaceNames) {
        Objects.requireNonNull(ifaceNames);
        if (interfaceNames.addAll(ifaceNames)) {
            setDirty(true);
        }
    }
    public void addInterfaceName(String ifaceName) {
        Objects.requireNonNull(ifaceName);
        if (interfaceNames.add(ifaceName)) {
            setDirty(true);
        }
    }

    // set l2Forward flag
    public void setL2Forward(boolean l2Forward) {
        this.l2Forward = (SlsNetService.ALLOW_ETH_ADDRESS_SELECTOR) ? l2Forward : false;
    }

    // add and remove interfaces from port configuration for each interfaceName */
    public void addInterfaces(Collection<Interface> ifaces) {
        Objects.requireNonNull(ifaces);
        if (interfaces.addAll(ifaces)) {
            setDirty(true);
        }
    }
    public void addInterface(Interface iface) {
        Objects.requireNonNull(iface);
        if (interfaces.add(iface)) {
            setDirty(true);
        }
    }
    public void removeInterfaces(Collection<Interface> ifaces) {
        Objects.requireNonNull(ifaces);
        if (interfaces.removeAll(ifaces)) {
            setDirty(true);
        }
    }
    public void removeInterface(Interface iface) {
        Objects.requireNonNull(iface);
        if (interfaces.remove(iface)) {
            setDirty(true);
        }
    }

    // add and remove hostNames for HostName->Interface lookup relation
    public void addHosts(Collection<Host> hosts) {
        Objects.requireNonNull(hosts);
        for (Host host : hosts) {
            addHost(host);
        }
    }
    public void addHost(Host host) {
        Objects.requireNonNull(host);
        if (hostIds.add(host.id())) {
            setDirty(true);
        }
    }
    public void removeHosts(Collection<Host> hosts) {
        Objects.requireNonNull(hosts);
        for (Host host : hosts) {
            removeHost(host);
        }
    }
    public void removeHost(Host host) {
        Objects.requireNonNull(host);
        if (hostIds.remove(host.id())) {
            setDirty(true);
        }
    }

    // set L2NetworkEntry Dirty Mark
    public void setDirty(boolean newDirty) {
        dirty = newDirty;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("name", name)
                .add("interfaceNames", interfaceNames)
                .add("encapsulation", encapsulation)
                .add("l2Forward", l2Forward)
                .add("interfaces", interfaces)
                .add("hostIds", hostIds)
                .add("dirty", dirty)
                .toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof L2Network)) {
            return false;
        }
        L2Network other = (L2Network) obj;
        return Objects.equals(other.name, this.name)
               && Objects.equals(other.interfaceNames, this.interfaceNames)
               && Objects.equals(other.encapsulation, this.encapsulation)
               && Objects.equals(other.l2Forward, this.l2Forward)
               && Objects.equals(other.interfaces, this.interfaces)
               && Objects.equals(other.hostIds, this.hostIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, interfaces, encapsulation, l2Forward);
    }
}
