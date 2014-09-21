package org.onlab.onos.net.trivial.impl;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Service;
import org.onlab.onos.net.DeviceId;
import org.onlab.onos.net.flow.DefaultFlowRule;
import org.onlab.onos.net.flow.FlowRule;
import org.onlab.onos.net.flow.FlowRuleEvent;
import org.onlab.onos.net.flow.FlowRuleStore;
import org.slf4j.Logger;

import static org.onlab.onos.net.flow.FlowRuleEvent.Type.RULE_ADDED;
import static org.onlab.onos.net.flow.FlowRuleEvent.Type.RULE_REMOVED;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Manages inventory of flow rules using trivial in-memory implementation.
 */
@Component(immediate = true)
@Service
public class SimpleFlowRuleStore implements FlowRuleStore {

    private final Logger log = getLogger(getClass());

    // store entries as a pile of rules, no info about device tables
    private final Multimap<DeviceId, FlowRule> flowEntries = HashMultimap.create();

    @Activate
    public void activate() {
        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        log.info("Stopped");
    }

    @Override
    public Iterable<FlowRule> getFlowEntries(DeviceId deviceId) {
        return ImmutableSet.copyOf(flowEntries.get(deviceId));
    }

    @Override
    public FlowRule storeFlowRule(FlowRule rule) {
        DeviceId did = rule.deviceId();
        FlowRule entry = new DefaultFlowRule(did,
                                             rule.selector(), rule.treatment(), rule.priority());
        flowEntries.put(did, entry);
        return entry;
    }

    @Override
    public FlowRuleEvent addOrUpdateFlowRule(FlowRule rule) {
        DeviceId did = rule.deviceId();

        // check if this new rule is an update to an existing entry
        for (FlowRule fe : flowEntries.get(did)) {
            if (rule.equals(fe)) {
                // TODO update the stats on this FlowRule?
                return null;
            }
        }
        flowEntries.put(did, rule);
        return new FlowRuleEvent(RULE_ADDED, rule);
    }

    @Override
    public FlowRuleEvent removeFlowRule(FlowRule rule) {
        synchronized (this) {
            if (flowEntries.remove(rule.deviceId(), rule)) {
                return new FlowRuleEvent(RULE_REMOVED, rule);
            } else {
                return null;
            }
        }
    }

}