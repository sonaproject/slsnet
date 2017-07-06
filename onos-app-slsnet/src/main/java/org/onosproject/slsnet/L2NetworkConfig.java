/*
 * Copyright 2016-present Open Networking Laboratory
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
import org.onosproject.net.EncapsulationType;

import java.util.Objects;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Configuration of a L2Network.
 */
public class L2NetworkConfig {
    private final String name;
    private final Set<String> ifaces;
    private final EncapsulationType encap;

    /**
     * Creates a new L2Network configuration.
     *
     * @param name the L2Network name
     * @param ifaces the interfaces associated with the L2Network
     * @param encap the encapsulation type if set
     */
    public L2NetworkConfig(String name, Set<String> ifaces, EncapsulationType encap) {
        this.name = checkNotNull(name);
        this.ifaces = checkNotNull(ImmutableSet.copyOf(ifaces));
        this.encap = checkNotNull(encap);
    }

    /**
     * The name of the L2Network.
     *
     * @return the name of the L2Network
     */
    public String name() {
        return name;
    }

    /**
     * The name of the interfaces associated with the L2Network.
     *
     * @return a set of interface names associated with the L2Network
     */
    public Set<String> ifaces() {
        return ImmutableSet.copyOf(ifaces);
    }

    /**
     * The encapsulation type.
     *
     * @return the encapsulation type, if active; null otherwise
     */
    public EncapsulationType encap() {
        return encap;
    }

    /**
     * States if a given interface is part of a L2Network.
     * @param iface the interface attached to a L2Network
     * @return true if the interface is associated to the L2Network; false otherwise
     */
    protected boolean isAttached(String iface) {
        return ifaces.stream().anyMatch(iface::equals);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof L2NetworkConfig) {
            L2NetworkConfig that = (L2NetworkConfig) obj;
            return Objects.equals(name, that.name) &&
                    Objects.equals(ifaces, that.ifaces) &&
                    Objects.equals(encap, that.encap);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, ifaces, encap);
    }
}
