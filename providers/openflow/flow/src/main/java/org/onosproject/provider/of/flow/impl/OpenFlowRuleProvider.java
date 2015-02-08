/*
 * Copyright 2014 Open Networking Laboratory
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
package org.onosproject.provider.of.flow.impl;


import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalNotification;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.flow.CompletedBatchOperation;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.FlowRuleBatchEntry;
import org.onosproject.net.flow.FlowRuleBatchOperation;
import org.onosproject.net.flow.FlowRuleProvider;
import org.onosproject.net.flow.FlowRuleProviderRegistry;
import org.onosproject.net.flow.FlowRuleProviderService;
import org.onosproject.net.provider.AbstractProvider;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.openflow.controller.Dpid;
import org.onosproject.openflow.controller.OpenFlowController;
import org.onosproject.openflow.controller.OpenFlowEventListener;
import org.onosproject.openflow.controller.OpenFlowSwitch;
import org.onosproject.openflow.controller.OpenFlowSwitchListener;
import org.onosproject.openflow.controller.RoleState;
import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.OFBarrierRequest;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFErrorType;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowRemoved;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFInstructionType;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPortStatus;
import org.projectfloodlight.openflow.protocol.OFStatsReply;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.errormsg.OFFlowModFailedErrorMsg;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;


/**
 * Provider which uses an OpenFlow controller to detect network
 * end-station hosts.
 */
@Component(immediate = true)
public class OpenFlowRuleProvider extends AbstractProvider implements FlowRuleProvider {

    private static final int LOWEST_PRIORITY = 0;

    private final Logger log = getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleProviderRegistry providerRegistry;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected OpenFlowController controller;


    private FlowRuleProviderService providerService;

    private final InternalFlowProvider listener = new InternalFlowProvider();

    private Cache<Long, InternalCacheEntry> pendingBatches;

    private final Map<Dpid, FlowStatsCollector> collectors = Maps.newHashMap();


    /**
     * Creates an OpenFlow host provider.
     */
    public OpenFlowRuleProvider() {
        super(new ProviderId("of", "org.onosproject.provider.openflow"));
    }

    @Activate
    public void activate() {
        providerService = providerRegistry.register(this);
        controller.addListener(listener);
        controller.addEventListener(listener);

        pendingBatches = CacheBuilder.newBuilder()
                .expireAfterWrite(10, TimeUnit.SECONDS)
                .removalListener((RemovalNotification<Long, InternalCacheEntry> notification) -> {
                    if (notification.getCause() == RemovalCause.EXPIRED) {
                        providerService.batchOperationCompleted(notification.getKey(),
                                                                notification.getValue().failedCompletion());
                    }
                }).build();


        for (OpenFlowSwitch sw : controller.getSwitches()) {
            FlowStatsCollector fsc = new FlowStatsCollector(sw, POLL_INTERVAL);
            fsc.start();
            collectors.put(new Dpid(sw.getId()), fsc);
        }


        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        providerRegistry.unregister(this);
        providerService = null;

        log.info("Stopped");
    }

    @Override
    public void applyFlowRule(FlowRule... flowRules) {
        for (FlowRule flowRule : flowRules) {
            applyRule(flowRule);
        }
    }

    private void applyRule(FlowRule flowRule) {
        OpenFlowSwitch sw = controller.getSwitch(Dpid.dpid(flowRule.deviceId().uri()));
        if (flowRule.type() == FlowRule.Type.DEFAULT) {
            sw.sendMsg(FlowModBuilder.builder(flowRule, sw.factory(),
                    Optional.empty()).buildFlowAdd());
        } else {
            sw.sendMsg(FlowModBuilder.builder(flowRule, sw.factory(),
                    Optional.empty()).buildFlowAdd(), getTableType(flowRule.type()));
        }
    }


    @Override
    public void removeFlowRule(FlowRule... flowRules) {
        for (FlowRule flowRule : flowRules) {
            removeRule(flowRule);
        }

    }

    private void removeRule(FlowRule flowRule) {
        OpenFlowSwitch sw = controller.getSwitch(Dpid.dpid(flowRule.deviceId().uri()));
        if (flowRule.type() == FlowRule.Type.DEFAULT) {
            sw.sendMsg(FlowModBuilder.builder(flowRule, sw.factory(),
                    Optional.empty()).buildFlowDel());
        } else {
            sw.sendMsg(FlowModBuilder.builder(flowRule, sw.factory(),
                    Optional.empty()).buildFlowDel(), getTableType(flowRule.type()));
        }
    }

    @Override
    public void removeRulesById(ApplicationId id, FlowRule... flowRules) {
        // TODO: optimize using the ApplicationId
        removeFlowRule(flowRules);
    }

    @Override

    public void executeBatch(FlowRuleBatchOperation batch) {

        pendingBatches.put(batch.id(), new InternalCacheEntry(batch));


        OpenFlowSwitch sw = controller.getSwitch(Dpid.dpid(batch.deviceId().uri()));
        OFFlowMod mod;

        for (FlowRuleBatchEntry fbe : batch.getOperations()) {

            FlowModBuilder builder =
                    FlowModBuilder.builder(fbe.target(), sw.factory(),
                                           Optional.of(batch.id()));
            switch (fbe.operator()) {
                case ADD:
                    mod = builder.buildFlowAdd();
                    break;
                case REMOVE:
                    mod = builder.buildFlowDel();
                    break;
                case MODIFY:
                    mod = builder.buildFlowMod();
                    break;
                default:
                    log.error("Unsupported batch operation {}; skipping flowmod {}",
                              fbe.operator(), fbe);
                    continue;
                }
            sw.sendMsg(mod);
        }
        OFBarrierRequest.Builder builder = sw.factory()
                .buildBarrierRequest()
                .setXid(batch.id());
        sw.sendMsg(builder.build());
    }

    private OpenFlowSwitch.TableType getTableType(FlowRule.Type type) {
        switch (type) {
            case IP:
                return OpenFlowSwitch.TableType.IP;
            case MPLS:
                return OpenFlowSwitch.TableType.MPLS;
            case ACL:
                return OpenFlowSwitch.TableType.ACL;
            default:
                return OpenFlowSwitch.TableType.NONE;
        }
    }




    private class InternalFlowProvider
            implements OpenFlowSwitchListener, OpenFlowEventListener {

        @Override
        public void switchAdded(Dpid dpid) {
            FlowStatsCollector fsc = new FlowStatsCollector(controller.getSwitch(dpid), POLL_INTERVAL);
            fsc.start();
            collectors.put(dpid, fsc);
        }

        @Override
        public void switchRemoved(Dpid dpid) {
            FlowStatsCollector collector = collectors.remove(dpid);
            if (collector != null) {
                collector.stop();
            }
        }

        @Override
        public void switchChanged(Dpid dpid) {
        }

        @Override
        public void portChanged(Dpid dpid, OFPortStatus status) {
            //TODO: Decide whether to evict flows internal store.
        }

        @Override
        public void handleMessage(Dpid dpid, OFMessage msg) {
            switch (msg.getType()) {
                case FLOW_REMOVED:
                    OFFlowRemoved removed = (OFFlowRemoved) msg;

                    FlowEntry fr = new FlowEntryBuilder(dpid, removed).build();
                    providerService.flowRemoved(fr);
                    break;
                case STATS_REPLY:
                    pushFlowMetrics(dpid, (OFStatsReply) msg);
                    break;
                case BARRIER_REPLY:
                    try {
                        InternalCacheEntry entry = pendingBatches.getIfPresent(msg.getXid());
                        if (entry != null) {
                            providerService.batchOperationCompleted(msg.getXid(), entry.completed());
                        } else {
                            log.warn("Received unknown Barrier Reply: {}", msg.getXid());
                        }
                    } finally {
                        pendingBatches.invalidate(msg.getXid());
                    }
                    break;
                case ERROR:
                    log.warn("received Error message {} from {}", msg, dpid);

                    OFErrorMsg error = (OFErrorMsg) msg;
                    if (error.getErrType() == OFErrorType.FLOW_MOD_FAILED) {
                        OFFlowModFailedErrorMsg fmFailed = (OFFlowModFailedErrorMsg) error;
                        if (fmFailed.getData().getParsedMessage().isPresent()) {
                            OFMessage m = fmFailed.getData().getParsedMessage().get();
                            OFFlowMod fm = (OFFlowMod) m;
                            InternalCacheEntry entry = pendingBatches.getIfPresent(msg.getXid());
                            if (entry != null) {
                                entry.appendFailure(new FlowEntryBuilder(dpid, fm).build());
                            } else {
                                log.error("No matching batch for this error: {}", error);
                            }
                        } else {
                            //FIXME: Potentially add flowtracking to avoid this message.
                            log.error("Flow installation failed but switch didn't" +
                                              " tell us which one.");
                        }
                    } else {
                        log.warn("Received error {}", error);
                    }


                default:
                    log.debug("Unhandled message type: {}", msg.getType());
            }

        }

        @Override
        public void receivedRoleReply(Dpid dpid, RoleState requested,
                                      RoleState response) {
            // Do nothing here for now.
        }

        private void pushFlowMetrics(Dpid dpid, OFStatsReply stats) {

            DeviceId did = DeviceId.deviceId(Dpid.uri(dpid));
            final OFFlowStatsReply replies = (OFFlowStatsReply) stats;

            List<FlowEntry> flowEntries = replies.getEntries().stream()
                    .filter(entry -> !tableMissRule(dpid, entry))
                    .map(entry -> new FlowEntryBuilder(dpid, entry).build())
                    .collect(Collectors.toList());

            providerService.pushFlowMetrics(did, flowEntries);

        }

        private boolean tableMissRule(Dpid dpid, OFFlowStatsEntry reply) {
            if (reply.getMatch().getMatchFields().iterator().hasNext()) {
                return false;
            }
            if (reply.getVersion().equals(OFVersion.OF_10)) {
                return reply.getPriority() == LOWEST_PRIORITY
                        && reply.getActions().isEmpty();
            }
            for (OFInstruction ins : reply.getInstructions()) {
                if (ins.getType() == OFInstructionType.APPLY_ACTIONS) {
                    OFInstructionApplyActions apply = (OFInstructionApplyActions) ins;
                    List<OFAction> acts = apply.getActions();
                    for (OFAction act : acts) {
                        if (act.getType() == OFActionType.OUTPUT) {
                            OFActionOutput out = (OFActionOutput) act;
                            if (out.getPort() == OFPort.CONTROLLER) {
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

    }

    /**
     * The internal cache entry holding the original request as well
     * as accumulating the any failures along the way.
     *
     * If this entry is evicted from the cache then the entire operation
     * is considered failed. Otherwise, only the failures reported by the device
     * will be propagated up.
     */
    private class InternalCacheEntry {

        private final FlowRuleBatchOperation operation;
        private final Set<FlowRule> failures = Sets.newConcurrentHashSet();

        public InternalCacheEntry(FlowRuleBatchOperation operation) {
            this.operation = operation;
        }

        /**
         * Appends a failed rule to the set of failed items.
         * @param rule the failed rule
         */
        public void appendFailure(FlowRule rule) {
            failures.add(rule);
        }

        /**
         * Fails the entire batch and returns the failed operation.
         * @return the failed operation
         */
        public CompletedBatchOperation failedCompletion() {
            Set<FlowRule> fails = operation.getOperations().stream()
                    .map(op -> op.target()).collect(Collectors.toSet());
            return new CompletedBatchOperation(false, Collections.unmodifiableSet(fails), operation.deviceId());
        }

        /**
         * Returns the completed operation and whether the batch suceeded.
         * @return the completed operation
         */
        public CompletedBatchOperation completed() {
            return new CompletedBatchOperation(failures.isEmpty(),
                                               Collections.unmodifiableSet(failures), operation.deviceId());
        }

    }

}
