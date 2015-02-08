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

import com.google.common.collect.Lists;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.Ip4Prefix;
import org.onlab.packet.Ip6Address;
import org.onlab.packet.Ip6Prefix;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;
import org.onosproject.core.DefaultGroupId;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultFlowEntry;
import org.onosproject.net.flow.DefaultFlowRule;
import org.onosproject.net.flow.DefaultTrafficSelector;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowEntry;
import org.onosproject.net.flow.FlowEntry.FlowEntryState;
import org.onosproject.net.flow.FlowRule;
import org.onosproject.net.flow.TrafficSelector;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.openflow.controller.Dpid;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowRemoved;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFInstructionType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionCircuit;
import org.projectfloodlight.openflow.protocol.action.OFActionExperimenter;
import org.projectfloodlight.openflow.protocol.action.OFActionGroup;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionPopMpls;
import org.projectfloodlight.openflow.protocol.action.OFActionSetDlDst;
import org.projectfloodlight.openflow.protocol.action.OFActionSetDlSrc;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.action.OFActionSetNwDst;
import org.projectfloodlight.openflow.protocol.action.OFActionSetNwSrc;
import org.projectfloodlight.openflow.protocol.action.OFActionSetVlanPcp;
import org.projectfloodlight.openflow.protocol.action.OFActionSetVlanVid;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxm;
import org.projectfloodlight.openflow.protocol.oxm.OFOxmOchSigidBasic;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IPv6Address;
import org.projectfloodlight.openflow.types.Masked;
import org.projectfloodlight.openflow.types.OFVlanVidMatch;
import org.projectfloodlight.openflow.types.U32;
import org.projectfloodlight.openflow.types.VlanPcp;
import org.slf4j.Logger;

import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

public class FlowEntryBuilder {
    private final Logger log = getLogger(getClass());

    private final OFFlowStatsEntry stat;
    private final OFFlowRemoved removed;
    private final OFFlowMod flowMod;

    private final Match match;
    private final List<OFAction> actions;

    private final Dpid dpid;

    public enum FlowType { STAT, REMOVED, MOD }

    private final FlowType type;


    public FlowEntryBuilder(Dpid dpid, OFFlowStatsEntry entry) {
        this.stat = entry;
        this.match = entry.getMatch();
        this.actions = getActions(entry);
        this.dpid = dpid;
        this.removed = null;
        this.flowMod = null;
        this.type = FlowType.STAT;
    }

    public FlowEntryBuilder(Dpid dpid, OFFlowRemoved removed) {
        this.match = removed.getMatch();
        this.removed = removed;

        this.dpid = dpid;
        this.actions = null;
        this.stat = null;
        this.flowMod = null;
        this.type = FlowType.REMOVED;

    }

    public FlowEntryBuilder(Dpid dpid, OFFlowMod fm) {
        this.match = fm.getMatch();
        this.dpid = dpid;
        this.actions = fm.getActions();
        this.type = FlowType.MOD;
        this.flowMod = fm;
        this.stat = null;
        this.removed = null;
    }

    public FlowEntry build(FlowEntryState... state) {
        FlowRule rule;
        switch (this.type) {
            case STAT:
                rule = new DefaultFlowRule(DeviceId.deviceId(Dpid.uri(dpid)),
                                      buildSelector(), buildTreatment(), stat.getPriority(),
                                      stat.getCookie().getValue(), stat.getIdleTimeout(), false);
                return new DefaultFlowEntry(rule, FlowEntryState.ADDED,
                                      stat.getDurationSec(), stat.getPacketCount().getValue(),
                                      stat.getByteCount().getValue());
            case REMOVED:
                rule = new DefaultFlowRule(DeviceId.deviceId(Dpid.uri(dpid)),
                                      buildSelector(), null, removed.getPriority(),
                                      removed.getCookie().getValue(), removed.getIdleTimeout(), false);
                return new DefaultFlowEntry(rule, FlowEntryState.REMOVED, removed.getDurationSec(),
                                      removed.getPacketCount().getValue(), removed.getByteCount().getValue());
            case MOD:
                FlowEntryState flowState = state.length > 0 ? state[0] : FlowEntryState.FAILED;
                rule = new DefaultFlowRule(DeviceId.deviceId(Dpid.uri(dpid)),
                                      buildSelector(), buildTreatment(), flowMod.getPriority(),
                                      flowMod.getCookie().getValue(), flowMod.getIdleTimeout(), false);
                return new DefaultFlowEntry(rule, flowState, 0, 0, 0);
            default:
                log.error("Unknown flow type : {}", this.type);
                return null;
        }

    }

    private List<OFAction> getActions(OFFlowStatsEntry entry) {
        switch (entry.getVersion()) {
            case OF_10:
                return entry.getActions();
            case OF_11:
            case OF_12:
            case OF_13:
                List<OFInstruction> ins = entry.getInstructions();
                for (OFInstruction in : ins) {
                    if (in.getType().equals(OFInstructionType.APPLY_ACTIONS)) {
                        OFInstructionApplyActions apply = (OFInstructionApplyActions) in;
                        return apply.getActions();
                    }
                }
                return Lists.newLinkedList();
            default:
                log.warn("Unknown OF version {}", entry.getVersion());
        }
        return Lists.newLinkedList();
    }

    private TrafficTreatment buildTreatment() {
        TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();
        // If this is a drop rule
        if (actions.size() == 0) {
            builder.drop();
            return builder.build();
        }
        for (OFAction act : actions) {
            switch (act.getType()) {
            case OUTPUT:
                OFActionOutput out = (OFActionOutput) act;
                builder.setOutput(
                        PortNumber.portNumber(out.getPort().getPortNumber()));
                break;
            case SET_VLAN_VID:
                OFActionSetVlanVid vlan = (OFActionSetVlanVid) act;
                builder.setVlanId(VlanId.vlanId(vlan.getVlanVid().getVlan()));
                break;
            case SET_VLAN_PCP:
                OFActionSetVlanPcp pcp = (OFActionSetVlanPcp) act;
                builder.setVlanPcp(pcp.getVlanPcp().getValue());
                break;
            case SET_DL_DST:
                OFActionSetDlDst dldst = (OFActionSetDlDst) act;
                builder.setEthDst(
                        MacAddress.valueOf(dldst.getDlAddr().getLong()));
                break;
            case SET_DL_SRC:
                OFActionSetDlSrc dlsrc = (OFActionSetDlSrc) act;
                builder.setEthSrc(
                        MacAddress.valueOf(dlsrc.getDlAddr().getLong()));

                break;
            case SET_NW_DST:
                OFActionSetNwDst nwdst = (OFActionSetNwDst) act;
                IPv4Address di = nwdst.getNwAddr();
                builder.setIpDst(Ip4Address.valueOf(di.getInt()));
                break;
            case SET_NW_SRC:
                OFActionSetNwSrc nwsrc = (OFActionSetNwSrc) act;
                IPv4Address si = nwsrc.getNwAddr();
                builder.setIpSrc(Ip4Address.valueOf(si.getInt()));
                break;
            case EXPERIMENTER:
                OFActionExperimenter exp = (OFActionExperimenter) act;
                if (exp.getExperimenter() == 0x80005A06 ||
                        exp.getExperimenter() == 0x748771) {
                    OFActionCircuit ct = (OFActionCircuit) exp;
                    builder.setLambda(((OFOxmOchSigidBasic) ct.getField()).getValue().getChannelNumber());
                } else {
                    log.warn("Unsupported OFActionExperimenter {}", exp.getExperimenter());
                }
                break;
            case SET_FIELD:
                OFActionSetField setField = (OFActionSetField) act;
                handleSetField(builder, setField.getField());
                break;
            case POP_MPLS:
                OFActionPopMpls popMpls = (OFActionPopMpls) act;
                builder.popMpls((short) popMpls.getEthertype().getValue());
                break;
            case PUSH_MPLS:
                builder.pushMpls();
                break;
            case COPY_TTL_IN:
                builder.copyTtlIn();
                break;
            case COPY_TTL_OUT:
                builder.copyTtlOut();
                break;
            case DEC_MPLS_TTL:
                builder.decMplsTtl();
                break;
            case DEC_NW_TTL:
                builder.decNwTtl();
                break;
            case GROUP:
                OFActionGroup group = (OFActionGroup) act;
                builder.group(new DefaultGroupId(group.getGroup().getGroupNumber()));
                break;
            case SET_TP_DST:
            case SET_TP_SRC:
            case POP_PBB:
            case POP_VLAN:
            case PUSH_PBB:
            case PUSH_VLAN:
            case SET_MPLS_LABEL:
            case SET_MPLS_TC:
            case SET_MPLS_TTL:
            case SET_NW_ECN:
            case SET_NW_TOS:
            case SET_NW_TTL:
            case SET_QUEUE:
            case STRIP_VLAN:
            case ENQUEUE:
            default:
                log.warn("Action type {} not yet implemented.", act.getType());
            }
        }

        return builder.build();
    }

    private void handleSetField(TrafficTreatment.Builder builder, OFOxm<?> oxm) {
        switch (oxm.getMatchField().id) {
        case VLAN_PCP:
            @SuppressWarnings("unchecked")
            OFOxm<VlanPcp> vlanpcp = (OFOxm<VlanPcp>) oxm;
            builder.setVlanPcp(vlanpcp.getValue().getValue());
            break;
        case VLAN_VID:
            @SuppressWarnings("unchecked")
            OFOxm<OFVlanVidMatch> vlanvid = (OFOxm<OFVlanVidMatch>) oxm;
            builder.setVlanId(VlanId.vlanId(vlanvid.getValue().getVlan()));
            break;
        case ETH_DST:
            @SuppressWarnings("unchecked")
            OFOxm<org.projectfloodlight.openflow.types.MacAddress> ethdst =
                    (OFOxm<org.projectfloodlight.openflow.types.MacAddress>) oxm;
            builder.setEthDst(MacAddress.valueOf(ethdst.getValue().getLong()));
            break;
        case ETH_SRC:
            @SuppressWarnings("unchecked")
            OFOxm<org.projectfloodlight.openflow.types.MacAddress> ethsrc =
                    (OFOxm<org.projectfloodlight.openflow.types.MacAddress>) oxm;
            builder.setEthSrc(MacAddress.valueOf(ethsrc.getValue().getLong()));
            break;
        case IPV4_DST:
            @SuppressWarnings("unchecked")
            OFOxm<IPv4Address> ip4dst = (OFOxm<IPv4Address>) oxm;
            builder.setIpDst(Ip4Address.valueOf(ip4dst.getValue().getInt()));
            break;
        case IPV4_SRC:
            @SuppressWarnings("unchecked")
            OFOxm<IPv4Address> ip4src = (OFOxm<IPv4Address>) oxm;
            builder.setIpSrc(Ip4Address.valueOf(ip4src.getValue().getInt()));
            break;
        case MPLS_LABEL:
            @SuppressWarnings("unchecked")
            OFOxm<U32> labelId = (OFOxm<U32>) oxm;
            builder.setMpls((int) labelId.getValue().getValue());
            break;
        case ARP_OP:
        case ARP_SHA:
        case ARP_SPA:
        case ARP_THA:
        case ARP_TPA:
        case BSN_EGR_PORT_GROUP_ID:
        case BSN_GLOBAL_VRF_ALLOWED:
        case BSN_IN_PORTS_128:
        case BSN_L3_DST_CLASS_ID:
        case BSN_L3_INTERFACE_CLASS_ID:
        case BSN_L3_SRC_CLASS_ID:
        case BSN_LAG_ID:
        case BSN_TCP_FLAGS:
        case BSN_UDF0:
        case BSN_UDF1:
        case BSN_UDF2:
        case BSN_UDF3:
        case BSN_UDF4:
        case BSN_UDF5:
        case BSN_UDF6:
        case BSN_UDF7:
        case BSN_VLAN_XLATE_PORT_GROUP_ID:
        case BSN_VRF:
        case ETH_TYPE:
        case ICMPV4_CODE:
        case ICMPV4_TYPE:
        case ICMPV6_CODE:
        case ICMPV6_TYPE:
        case IN_PHY_PORT:
        case IN_PORT:
        case IPV6_DST:
        case IPV6_FLABEL:
        case IPV6_ND_SLL:
        case IPV6_ND_TARGET:
        case IPV6_ND_TLL:
        case IPV6_SRC:
        case IP_DSCP:
        case IP_ECN:
        case IP_PROTO:
        case METADATA:
        case MPLS_TC:
        case OCH_SIGID:
        case OCH_SIGID_BASIC:
        case OCH_SIGTYPE:
        case OCH_SIGTYPE_BASIC:
        case SCTP_DST:
        case SCTP_SRC:
        case TCP_DST:
        case TCP_SRC:
        case TUNNEL_ID:
        case UDP_DST:
        case UDP_SRC:
        default:
            log.warn("Set field type {} not yet implemented.", oxm.getMatchField().id);
            break;
        }
    }

    private TrafficSelector buildSelector() {
        MacAddress mac;
        Ip4Prefix ip4Prefix;
        Ip6Address ip6Address;
        Ip6Prefix ip6Prefix;

        TrafficSelector.Builder builder = DefaultTrafficSelector.builder();
        for (MatchField<?> field : match.getMatchFields()) {
            switch (field.id) {
            case IN_PORT:
                builder.matchInPort(PortNumber
                        .portNumber(match.get(MatchField.IN_PORT).getPortNumber()));
                break;
            case IN_PHY_PORT:
                builder.matchInPhyPort(PortNumber
                        .portNumber(match.get(MatchField.IN_PHY_PORT).getPortNumber()));
                break;
            case METADATA:
                long metadata =
                    match.get(MatchField.METADATA).getValue().getValue();
                builder.matchMetadata(metadata);
                break;
            case ETH_DST:
                mac = MacAddress.valueOf(match.get(MatchField.ETH_DST).getLong());
                builder.matchEthDst(mac);
                break;
            case ETH_SRC:
                mac = MacAddress.valueOf(match.get(MatchField.ETH_SRC).getLong());
                builder.matchEthSrc(mac);
                break;
            case ETH_TYPE:
                int ethType = match.get(MatchField.ETH_TYPE).getValue();
                builder.matchEthType((short) ethType);
                break;
            case VLAN_VID:
                VlanId vlanId = null;
                if (match.isPartiallyMasked(MatchField.VLAN_VID)) {
                    Masked<OFVlanVidMatch> masked = match.getMasked(MatchField.VLAN_VID);
                    if (masked.getValue().equals(OFVlanVidMatch.PRESENT)
                            && masked.getMask().equals(OFVlanVidMatch.PRESENT)) {
                        vlanId = VlanId.ANY;
                    }
                } else {
                    vlanId = VlanId.vlanId(match.get(MatchField.VLAN_VID).getVlan());
                }
                if (vlanId != null) {
                    builder.matchVlanId(vlanId);
                }
                break;
            case VLAN_PCP:
                byte vlanPcp = match.get(MatchField.VLAN_PCP).getValue();
                builder.matchVlanPcp(vlanPcp);
                break;
            case IP_DSCP:
                byte ipDscp = match.get(MatchField.IP_DSCP).getDscpValue();
                builder.matchIPDscp(ipDscp);
                break;
            case IP_ECN:
                byte ipEcn = match.get(MatchField.IP_ECN).getEcnValue();
                builder.matchIPEcn(ipEcn);
                break;
            case IP_PROTO:
                short proto = match.get(MatchField.IP_PROTO).getIpProtocolNumber();
                builder.matchIPProtocol((byte) proto);
                break;
            case IPV4_SRC:
                if (match.isPartiallyMasked(MatchField.IPV4_SRC)) {
                    Masked<IPv4Address> maskedIp = match.getMasked(MatchField.IPV4_SRC);

                    ip4Prefix = Ip4Prefix.valueOf(
                            maskedIp.getValue().getInt(),
                            maskedIp.getMask().asCidrMaskLength());
                } else {
                    ip4Prefix = Ip4Prefix.valueOf(
                            match.get(MatchField.IPV4_SRC).getInt(),
                            Ip4Prefix.MAX_MASK_LENGTH);
                }

                builder.matchIPSrc(ip4Prefix);
                break;
            case IPV4_DST:
                if (match.isPartiallyMasked(MatchField.IPV4_DST)) {
                    Masked<IPv4Address> maskedIp = match.getMasked(MatchField.IPV4_DST);

                    ip4Prefix = Ip4Prefix.valueOf(
                            maskedIp.getValue().getInt(),
                            maskedIp.getMask().asCidrMaskLength());
                } else {
                    ip4Prefix = Ip4Prefix.valueOf(
                            match.get(MatchField.IPV4_DST).getInt(),
                            Ip4Prefix.MAX_MASK_LENGTH);
                }

                builder.matchIPDst(ip4Prefix);
                break;
            case TCP_SRC:
                builder.matchTcpSrc((short) match.get(MatchField.TCP_SRC).getPort());
                break;
            case TCP_DST:
                builder.matchTcpDst((short) match.get(MatchField.TCP_DST).getPort());
                break;
            case UDP_SRC:
                builder.matchUdpSrc((short) match.get(MatchField.UDP_SRC).getPort());
                break;
            case UDP_DST:
                builder.matchUdpDst((short) match.get(MatchField.UDP_DST).getPort());
                break;
            case SCTP_SRC:
                builder.matchSctpSrc((short) match.get(MatchField.SCTP_SRC).getPort());
                break;
            case SCTP_DST:
                builder.matchSctpDst((short) match.get(MatchField.SCTP_DST).getPort());
                break;
            case ICMPV4_TYPE:
                byte icmpType = (byte) match.get(MatchField.ICMPV4_TYPE).getType();
                builder.matchIcmpType(icmpType);
                break;
            case ICMPV4_CODE:
                byte icmpCode = (byte) match.get(MatchField.ICMPV4_CODE).getCode();
                builder.matchIcmpCode(icmpCode);
                break;
            case IPV6_SRC:
                if (match.isPartiallyMasked(MatchField.IPV6_SRC)) {
                    Masked<IPv6Address> maskedIp = match.getMasked(MatchField.IPV6_SRC);
                    ip6Prefix = Ip6Prefix.valueOf(
                            maskedIp.getValue().getBytes(),
                            maskedIp.getMask().asCidrMaskLength());
                } else {
                    ip6Prefix = Ip6Prefix.valueOf(
                            match.get(MatchField.IPV6_SRC).getBytes(),
                            Ip6Prefix.MAX_MASK_LENGTH);
                }
                builder.matchIPv6Src(ip6Prefix);
                break;
            case IPV6_DST:
                if (match.isPartiallyMasked(MatchField.IPV6_DST)) {
                    Masked<IPv6Address> maskedIp = match.getMasked(MatchField.IPV6_DST);
                    ip6Prefix = Ip6Prefix.valueOf(
                            maskedIp.getValue().getBytes(),
                            maskedIp.getMask().asCidrMaskLength());
                } else {
                    ip6Prefix = Ip6Prefix.valueOf(
                            match.get(MatchField.IPV6_DST).getBytes(),
                            Ip6Prefix.MAX_MASK_LENGTH);
                }
                builder.matchIPv6Dst(ip6Prefix);
                break;
            case IPV6_FLABEL:
                int flowLabel =
                    match.get(MatchField.IPV6_FLABEL).getIPv6FlowLabelValue();
                builder.matchIPv6FlowLabel(flowLabel);
                break;
            case ICMPV6_TYPE:
                byte icmpv6type = (byte) match.get(MatchField.ICMPV6_TYPE).getValue();
                builder.matchIcmpv6Type(icmpv6type);
                break;
            case ICMPV6_CODE:
                byte icmpv6code = (byte) match.get(MatchField.ICMPV6_CODE).getValue();
                builder.matchIcmpv6Code(icmpv6code);
                break;
            case IPV6_ND_TARGET:
                ip6Address =
                    Ip6Address.valueOf(match.get(MatchField.IPV6_ND_TARGET).getBytes());
                builder.matchIPv6NDTargetAddress(ip6Address);
                break;
            case IPV6_ND_SLL:
                mac = MacAddress.valueOf(match.get(MatchField.IPV6_ND_SLL).getLong());
                builder.matchIPv6NDSourceLinkLayerAddress(mac);
                break;
            case IPV6_ND_TLL:
                mac = MacAddress.valueOf(match.get(MatchField.IPV6_ND_TLL).getLong());
                builder.matchIPv6NDTargetLinkLayerAddress(mac);
                break;
            case MPLS_LABEL:
                builder.matchMplsLabel((int) match.get(MatchField.MPLS_LABEL)
                                            .getValue());
                break;
            case OCH_SIGID:
                builder.matchLambda(match.get(MatchField.OCH_SIGID).getChannelNumber());
                break;
            case OCH_SIGTYPE:
                builder.matchOpticalSignalType(match.get(MatchField
                                                                 .OCH_SIGTYPE).getValue());
                break;
            case ARP_OP:
            case ARP_SHA:
            case ARP_SPA:
            case ARP_THA:
            case ARP_TPA:
            case MPLS_TC:
            case TUNNEL_ID:
            default:
                log.warn("Match type {} not yet implemented.", field.id);
            }
        }
        return builder.build();
    }

}
