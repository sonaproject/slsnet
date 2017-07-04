/*
 * Copyright 2015-present Open Networking Laboratory
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
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onosproject.incubator.net.intf.Interface;
import org.onosproject.incubator.net.intf.InterfaceService;
import org.onosproject.net.EncapsulationType;
import org.onosproject.net.Host;
import org.onosproject.net.host.HostEvent;
import org.onosproject.net.host.HostListener;
import org.onosproject.net.host.HostService;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Set;

import static java.util.Objects.*;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Application to create L2 broadcast overlay networks using VLANs.
 */
@Component(immediate = true)
@Service
public class VplsManager implements Vpls {
    public static final String VPLS_APP = "org.onosproject.slsnet";
    private static final String UNSUPPORTED_STORE_EVENT_TYPE =
            "Unsupported store event type {}.";
    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected HostService hostService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected InterfaceService interfaceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected VplsStore vplsStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected VplsOperationService operationService;

    private HostListener vplsHostListener = new VplsHostListener();

    @Activate
    public void activate() {
        hostService.addListener(vplsHostListener);
    }

    @Deactivate
    public void deactivate() {
        hostService.removeListener(vplsHostListener);
    }

    @Override
    public VplsData createVpls(String vplsName, EncapsulationType encapsulationType) {
        requireNonNull(vplsName);
        requireNonNull(encapsulationType);

        VplsData vplsData = VplsData.of(vplsName, encapsulationType);
        return vplsData;
    }

    @Override
    public VplsData removeVpls(VplsData vplsData) {
        requireNonNull(vplsData);
        VplsData newData = VplsData.of(vplsData);
        newData.state(VplsData.VplsState.REMOVING);
        vplsStore.removeVpls(vplsData);
        return vplsData;
    }

    @Override
    public void addInterfaces(VplsData vplsData, Collection<Interface> interfaces) {
        requireNonNull(vplsData);
        requireNonNull(interfaces);
        VplsData newData = VplsData.of(vplsData);
        newData.addInterfaces(interfaces);
        updateVplsStatus(newData, VplsData.VplsState.UPDATING);
    }

    @Override
    public void addInterface(VplsData vplsData, Interface iface) {
        requireNonNull(vplsData);
        requireNonNull(iface);
        VplsData newData = VplsData.of(vplsData);
        newData.addInterface(iface);
        updateVplsStatus(newData, VplsData.VplsState.UPDATING);
    }

    @Override
    public void setEncapsulationType(VplsData vplsData, EncapsulationType encapsulationType) {
        requireNonNull(vplsData);
        requireNonNull(encapsulationType);
        VplsData newData = VplsData.of(vplsData);
        if (newData.encapsulationType().equals(encapsulationType)) {
            // Encap type not changed.
            return;
        }
        newData.encapsulationType(encapsulationType);
        updateVplsStatus(newData, VplsData.VplsState.UPDATING);
    }

    @Override
    public VplsData getVpls(String vplsName) {
        requireNonNull(vplsName);
        return vplsStore.getVpls(vplsName);
    }

    @Override
    public Collection<Interface> removeInterfaces(VplsData vplsData, Collection<Interface> interfaces) {
        requireNonNull(vplsData);
        requireNonNull(interfaces);
        VplsData newData = VplsData.of(vplsData);
        newData.removeInterfaces(interfaces);
        updateVplsStatus(newData, VplsData.VplsState.UPDATING);
        return interfaces;
    }

    @Override
    public Interface removeInterface(VplsData vplsData, Interface iface) {
        requireNonNull(vplsData);
        requireNonNull(iface);
        VplsData newData = VplsData.of(vplsData);
        newData.removeInterface(iface);
        updateVplsStatus(newData, VplsData.VplsState.UPDATING);
        return iface;
    }

    @Override
    public void removeAllVpls() {
        Set<VplsData> allVplses = ImmutableSet.copyOf(vplsStore.getAllVpls());
        allVplses.forEach(this::removeVpls);
    }

    @Override
    public Collection<VplsData> getAllVpls() {
        return ImmutableSet.copyOf(vplsStore.getAllVpls());
    }

    /**
     * Updates VPLS status to the store.
     *
     * @param vplsData the VPLS
     * @param vplsState the new state to the VPLS
     */
    private void updateVplsStatus(VplsData vplsData, VplsData.VplsState vplsState) {
        vplsData.state(vplsState);
        vplsStore.updateVpls(vplsData);
    }

    /**
     * A listener for host events.
     * Updates a VPLS if host added or removed.
     */
    class VplsHostListener implements HostListener {
        @Override
        public void event(HostEvent event) {
            Host host = event.subject();
            Interface iface = getHostInterface(host);
            if (iface == null) {
                return;
            }
            VplsData vplsData = vplsStore.getAllVpls().stream()
                    .filter(v -> v.interfaces().contains(iface))
                    .findFirst()
                    .orElse(null);
            if (vplsData == null) {
                // the host does not related to any vpls
                return;
            }
            updateVplsStatus(vplsData, VplsData.VplsState.UPDATING);
        }

        /**
         * Gets the network interface of the host.
         *
         * @param host the host
         * @return the network interface of the host; null if no network
         * interface found
         */
        private Interface getHostInterface(Host host) {
            Set<Interface> interfaces = interfaceService.getInterfaces();
            return interfaces.stream()
                    .filter(iface -> iface.connectPoint().equals(host.location()) &&
                            iface.vlan().equals(host.vlan()))
                    .findFirst()
                    .orElse(null);
        }
    }

}
