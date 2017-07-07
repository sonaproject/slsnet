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
import org.onosproject.incubator.net.intf.Interface;
import org.onosproject.net.EncapsulationType;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * Class stores a L2Network information.
 */
public final class L2Network {
    /**
     * States of a Entry.
     */
    public enum State {
        UPDATING,
        ADDING,
        REMOVING,
        ADDED,
        REMOVED,
        FAILED
    }

    private String name;
    private Set<Interface> interfaces;
    private EncapsulationType encapsulationType;
    private State state;

    /**
     * Constructs a L2Network data by given name and encapsulation type.
     *
     * @param name the given name
     * @param encapType the encapsulation type
     */
    private L2Network(String name, EncapsulationType encapType) {
        this.name = name;
        this.encapsulationType = encapType;
        this.interfaces = Sets.newHashSet();
        this.state = State.ADDING;
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
     * Creates a L2Network data by given name and encapsulation type.
     *
     * @param name the given name
     * @param encapType the encapsulation type
     * @return the L2Network data
     */
    public static L2Network of(String name, EncapsulationType encapType) {
        Objects.requireNonNull(name);
        if (encapType == null) {
            return new L2Network(name, EncapsulationType.NONE);
        } else {
            return new L2Network(name, encapType);
        }
    }

    /**
     * Creates a copy of L2Network data.
     *
     * @param l2Network the L2Network data
     * @return the copy of the L2Network data
     */
    public static L2Network of(L2Network l2Network) {
        Objects.requireNonNull(l2Network);
        L2Network l2NetworkCopy = new L2Network(l2Network.name(), l2Network.encapsulationType());
        l2NetworkCopy.state(l2Network.state());
        l2NetworkCopy.addInterfaces(l2Network.interfaces());
        return l2Network;
    }

    /**
     * Gets name of the L2Network.
     *
     * @return the name of the L2Network
     */
    public String name() {
        return name;
    }

    public Set<Interface> interfaces() {
        return ImmutableSet.copyOf(interfaces);
    }

    public EncapsulationType encapsulationType() {
        return encapsulationType;
    }

    public void addInterfaces(Collection<Interface> interfaces) {
        Objects.requireNonNull(interfaces);
        this.interfaces.addAll(interfaces);
    }

    public void addInterface(Interface iface) {
        Objects.requireNonNull(iface);
        this.interfaces.add(iface);
    }

    public void removeInterfaces(Collection<Interface> interfaces) {
        Objects.requireNonNull(interfaces);
        this.interfaces.removeAll(interfaces);
    }

    public void removeInterface(Interface iface) {
        Objects.requireNonNull(iface);
        this.interfaces.remove(iface);
    }

    public void encapsulationType(EncapsulationType encapType) {
        this.encapsulationType = encapType;
    }

    public State state() {
        return state;
    }

    public void state(State state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass())
                .add("name", name)
                .add("interfaces", interfaces)
                .add("encap type", encapsulationType)
                .add("state", state)
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
        return Objects.equals(other.name, this.name) &&
                Objects.equals(other.interfaces, this.interfaces) &&
                Objects.equals(other.encapsulationType, this.encapsulationType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, interfaces, encapsulationType);
    }
}
