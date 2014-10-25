package org.onlab.onos.provider.of.flow.impl;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.onlab.onos.net.flow.FlowRule;
import org.onlab.onos.net.flow.TrafficTreatment;
import org.onlab.onos.net.flow.instructions.Instruction;
import org.onlab.onos.net.flow.instructions.Instructions.OutputInstruction;
import org.onlab.onos.net.flow.instructions.L0ModificationInstruction;
import org.onlab.onos.net.flow.instructions.L0ModificationInstruction.ModLambdaInstruction;
import org.onlab.onos.net.flow.instructions.L2ModificationInstruction;
import org.onlab.onos.net.flow.instructions.L2ModificationInstruction.ModEtherInstruction;
import org.onlab.onos.net.flow.instructions.L2ModificationInstruction.ModVlanIdInstruction;
import org.onlab.onos.net.flow.instructions.L2ModificationInstruction.ModVlanPcpInstruction;
import org.onlab.onos.net.flow.instructions.L3ModificationInstruction;
import org.onlab.onos.net.flow.instructions.L3ModificationInstruction.ModIPInstruction;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowDelete;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.oxm.OFOxm;
import org.projectfloodlight.openflow.types.CircuitSignalID;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U64;
import org.projectfloodlight.openflow.types.VlanPcp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flow mod builder for OpenFlow 1.3+.
 */
public class FlowModBuilderVer13 extends FlowModBuilder {

    private static final Logger log = LoggerFactory.getLogger(FlowModBuilderVer10.class);

    private final TrafficTreatment treatment;

    /**
     * Constructor for a flow mod builder for OpenFlow 1.3.
     *
     * @param flowRule the flow rule to transform into a flow mod
     * @param factory the OpenFlow factory to use to build the flow mod
     */
    protected FlowModBuilderVer13(FlowRule flowRule, OFFactory factory) {
        super(flowRule, factory);

        this.treatment = flowRule.treatment();
    }

    @Override
    public OFFlowAdd buildFlowAdd() {
        Match match = buildMatch();
        OFInstruction writeActions =
                factory().instructions().writeActions(buildActions());

        long cookie = flowRule().id().value();

        //TODO: what to do without bufferid? do we assume that there will be a pktout as well?
        OFFlowAdd fm = factory().buildFlowAdd()
                .setXid(cookie)
                .setCookie(U64.of(cookie))
                .setBufferId(OFBufferId.NO_BUFFER)
                .setInstructions(Collections.singletonList(writeActions))
                .setMatch(match)
                .setFlags(Collections.singleton(OFFlowModFlags.SEND_FLOW_REM))
                .setPriority(flowRule().priority())
                .build();

        return fm;
    }

    @Override
    public OFFlowMod buildFlowMod() {
        Match match = buildMatch();
        OFInstruction writeActions =
                factory().instructions().writeActions(buildActions());

        long cookie = flowRule().id().value();

        //TODO: what to do without bufferid? do we assume that there will be a pktout as well?
        OFFlowMod fm = factory().buildFlowModify()
                .setXid(cookie)
                .setCookie(U64.of(cookie))
                .setBufferId(OFBufferId.NO_BUFFER)
                .setInstructions(Collections.singletonList(writeActions))
                .setMatch(match)
                .setFlags(Collections.singleton(OFFlowModFlags.SEND_FLOW_REM))
                .setPriority(flowRule().priority())
                .build();

        return fm;
    }

    @Override
    public OFFlowDelete buildFlowDel() {
        Match match = buildMatch();
        OFInstruction writeActions =
                factory().instructions().writeActions(buildActions());

        long cookie = flowRule().id().value();

        OFFlowDelete fm = factory().buildFlowDelete()
                .setXid(cookie)
                .setCookie(U64.of(cookie))
                .setBufferId(OFBufferId.NO_BUFFER)
                .setInstructions(Collections.singletonList(writeActions))
                .setMatch(match)
                .setFlags(Collections.singleton(OFFlowModFlags.SEND_FLOW_REM))
                .setPriority(flowRule().priority())
                .build();

        return fm;
    }

    private List<OFAction> buildActions() {
        List<OFAction> actions = new LinkedList<>();
        if (treatment == null) {
            return actions;
        }
        for (Instruction i : treatment.instructions()) {
            switch (i.type()) {
            case DROP:
                log.warn("Saw drop action; assigning drop action");
                return new LinkedList<>();
            case L0MODIFICATION:
                actions.add(buildL0Modification(i));
                break;
            case L2MODIFICATION:
                actions.add(buildL2Modification(i));
                break;
            case L3MODIFICATION:
                actions.add(buildL3Modification(i));
                break;
            case OUTPUT:
                OutputInstruction out = (OutputInstruction) i;
                actions.add(factory().actions().buildOutput().setPort(
                        OFPort.of((int) out.port().toLong())).build());
                break;
            case GROUP:
            default:
                log.warn("Instruction type {} not yet implemented.", i.type());
            }
        }

        return actions;
    }

    private OFAction buildL0Modification(Instruction i) {
        L0ModificationInstruction l0m = (L0ModificationInstruction) i;
        switch (l0m.subtype()) {
        case LAMBDA:
            ModLambdaInstruction ml = (ModLambdaInstruction) i;
            return factory().actions().circuit(factory().oxms().ochSigidBasic(
                    new CircuitSignalID((byte) 1, (byte) 2, ml.lambda(), (short) 1)));
        default:
            log.warn("Unimplemented action type {}.", l0m.subtype());
            break;
        }
        return null;
    }

    private OFAction buildL2Modification(Instruction i) {
        L2ModificationInstruction l2m = (L2ModificationInstruction) i;
        ModEtherInstruction eth;
        OFOxm<?> oxm = null;
        switch (l2m.subtype()) {
        case ETH_DST:
            eth = (ModEtherInstruction) l2m;
            oxm = factory().oxms().ethDst(MacAddress.of(eth.mac().toLong()));
            break;
        case ETH_SRC:
            eth = (ModEtherInstruction) l2m;
            oxm = factory().oxms().ethSrc(MacAddress.of(eth.mac().toLong()));
            break;
        case VLAN_ID:
            ModVlanIdInstruction vlanId = (ModVlanIdInstruction) l2m;
            oxm = factory().oxms().vlanVid(OFVlanVidMatch.ofVlan(vlanId.vlanId.toShort()));
            break;
        case VLAN_PCP:
            ModVlanPcpInstruction vlanPcp = (ModVlanPcpInstruction) l2m;
            oxm = factory().oxms().vlanPcp(VlanPcp.of(vlanPcp.vlanPcp));
            break;
        default:
            log.warn("Unimplemented action type {}.", l2m.subtype());
            break;
        }

        if (oxm != null) {
            return factory().actions().buildSetField().setField(oxm).build();
        }
        return null;
    }

    private OFAction buildL3Modification(Instruction i) {
        L3ModificationInstruction l3m = (L3ModificationInstruction) i;
        ModIPInstruction ip;
        OFOxm<?> oxm = null;
        switch (l3m.subtype()) {
        case IP_DST:
            ip = (ModIPInstruction) i;
            oxm = factory().oxms().ipv4Dst(IPv4Address.of(ip.ip().toInt()));
        case IP_SRC:
            ip = (ModIPInstruction) i;
            oxm = factory().oxms().ipv4Src(IPv4Address.of(ip.ip().toInt()));
        default:
            log.warn("Unimplemented action type {}.", l3m.subtype());
            break;
        }

        if (oxm != null) {
            return factory().actions().buildSetField().setField(oxm).build();
        }
        return null;
    }

}