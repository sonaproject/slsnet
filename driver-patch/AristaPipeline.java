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
package org.onosproject.driver.pipeline;

import org.onlab.osgi.ServiceDirectory;
import org.onlab.packet.EthType.EtherType;
import org.onlab.packet.IpAddress;
import org.onlab.packet.IpPrefix;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.behaviour.Pipeliner;
import org.onosproject.net.behaviour.PipelinerContext;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.*;
import org.onosproject.net.flow.criteria.Criteria;
import org.onosproject.net.flow.criteria.Criterion.Type;
import org.onosproject.net.flow.criteria.IPCriterion;
import org.onosproject.net.flow.instructions.Instructions;
import org.onosproject.net.flowobjective.*;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Driver for standard OpenVSwitch.
 */
public class AristaPipeline extends DefaultSingleTablePipeline
        implements Pipeliner {

    private final Logger log = getLogger(getClass());
    private CoreService coreService;
    private ServiceDirectory serviceDirectory;
    protected FlowObjectiveStore flowObjectiveStore;
    protected DeviceId deviceId;
    protected ApplicationId appId;
    protected FlowRuleService flowRuleService;
    protected DeviceService deviceService;
    private static final int TIME_OUT = 30;

    @Override
    public void init(DeviceId deviceId, PipelinerContext context) {
        super.init(deviceId, context);
        this.serviceDirectory = context.directory();
        this.deviceId = deviceId;

        coreService = serviceDirectory.get(CoreService.class);
        flowRuleService = serviceDirectory.get(FlowRuleService.class);
        flowObjectiveStore = context.store();
        appId = coreService
                .registerApplication("org.onosproject.driver.AristaPipeline");
    }

    @Override
    public void filter(FilteringObjective filteringObjective) {
        super.filter(filteringObjective);
    }

    @Override
    public void forward(ForwardingObjective fwd) {
        Collection<FlowRule> rules;
        FlowRuleOperations.Builder flowOpsBuilder = FlowRuleOperations
                .builder();

        rules = processForward(fwd);
        switch (fwd.op()) {
        case ADD:
            rules.stream().filter(Objects::nonNull)
                    .forEach(flowOpsBuilder::add);
            break;
        case REMOVE:
            rules.stream().filter(Objects::nonNull)
                    .forEach(flowOpsBuilder::remove);
            break;
        default:
            fail(fwd, ObjectiveError.UNKNOWN);
            log.warn("Unknown forwarding type {}", fwd.op());
        }

        flowRuleService.apply(flowOpsBuilder
                .build(new FlowRuleOperationsContext() {
                    @Override
                    public void onSuccess(FlowRuleOperations ops) {
                        pass(fwd);
                    }

                    @Override
                    public void onError(FlowRuleOperations ops) {
                        fail(fwd, ObjectiveError.FLOWINSTALLATIONFAILED);
                    }
                }));
    }

    @Override
    public void next(NextObjective nextObjective) {
        super.next(nextObjective);
    }

    private Collection<FlowRule> processForward(ForwardingObjective fwd) {

        log.debug("Proecssing forwarding object");

        TrafficTreatment tb = fwd.treatment();
        TrafficTreatment.Builder newTraffic = DefaultTrafficTreatment
                .builder();

        // rebuild traffic trreatment without clear because Arista's EOS cannot support ClearActions
        tb.allInstructions().forEach(t -> newTraffic.add(t));

        FlowRule.Builder ruleBuilder = DefaultFlowRule.builder()
                .forDevice(deviceId)
                .withSelector(fwd.selector())
                .withTreatment(newTraffic.build())
                .withPriority(fwd.priority())
                .fromApp(fwd.appId());

        if (fwd.permanent()) {
            ruleBuilder.makePermanent();
        } else {
            ruleBuilder.makeTemporary(TIME_OUT);
        }
        return Collections.singletonList(ruleBuilder.build());
    }

    private void fail(Objective obj, ObjectiveError error) {
        obj.context().ifPresent(context -> context.onError(obj, error));
    }

    private void pass(Objective obj) {
        obj.context().ifPresent(context -> context.onSuccess(obj));
    }
}