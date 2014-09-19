package org.onlab.onos.net.flow.instructions;

import static com.google.common.base.Preconditions.checkNotNull;

import org.onlab.onos.net.PortNumber;
import org.onlab.onos.net.flow.instructions.L2ModificationInstruction.L2SubType;
import org.onlab.onos.net.flow.instructions.L2ModificationInstruction.ModEtherInstruction;
import org.onlab.onos.net.flow.instructions.L3ModificationInstruction.L3SubType;
import org.onlab.onos.net.flow.instructions.L3ModificationInstruction.ModIPInstruction;
import org.onlab.packet.IpAddress;
import org.onlab.packet.MacAddress;
import org.onlab.packet.VlanId;

/**
 * Factory class for creating various traffic treatment instructions.
 */
public final class Instructions {


    // Ban construction
    private Instructions() {}

    /**
     * Creates an output instruction using the specified port number. This can
     * include logical ports such as CONTROLLER, FLOOD, etc.
     *
     * @param number port number
     * @return output instruction
     */
    public static OutputInstruction createOutput(final PortNumber number) {
        checkNotNull(number, "PortNumber cannot be null");
        return new OutputInstruction(number);
    }

    /**
     * Creates a drop instruction.
     * @return drop instruction
     */
    public static DropInstruction createDrop() {
        return new DropInstruction();
    }

    /**
     * Creates a l2 src modification.
     * @param addr the mac address to modify to.
     * @return a l2 modification
     */
    public static L2ModificationInstruction modL2Src(MacAddress addr) {
        checkNotNull(addr, "Src l2 address cannot be null");
        return new ModEtherInstruction(L2SubType.L2_SRC, addr);
    }

    /**
     * Creates a L2 dst modification.
     * @param addr the mac address to modify to.
     * @return a L2 modification
     */
    public static L2ModificationInstruction modL2Dst(MacAddress addr) {
        checkNotNull(addr, "Dst l2 address cannot be null");
        return new L2ModificationInstruction.ModEtherInstruction(L2SubType.L2_DST, addr);
    }

    /**
     * Creates a Vlan id modification.
     * @param vlanId the vlan id to modify to.
     * @return a L2 modification
     */
    public static L2ModificationInstruction modVlanId(VlanId vlanId) {
        checkNotNull(vlanId, "VLAN id cannot be null");
        return new L2ModificationInstruction.ModVlanIdInstruction(vlanId);
    }

    /**
     * Creates a Vlan pcp modification.
     * @param vlanPcp the pcp to modify to.
     * @return a L2 modification
     */
    public static L2ModificationInstruction modVlanPcp(Byte vlanPcp) {
        checkNotNull(vlanPcp, "VLAN Pcp cannot be null");
        return new L2ModificationInstruction.ModVlanPcpInstruction(vlanPcp);
    }

    /**
     * Creates a L3 src modification.
     * @param addr the ip address to modify to.
     * @return a L3 modification
     */
    public static L3ModificationInstruction modL3Src(IpAddress addr) {
        checkNotNull(addr, "Src l3 address cannot be null");
        return new ModIPInstruction(L3SubType.L3_SRC, addr);
    }

    /**
     * Creates a L3 dst modification.
     * @param addr the ip address to modify to.
     * @return a L3 modification
     */
    public static L3ModificationInstruction modL3Dst(IpAddress addr) {
        checkNotNull(addr, "Dst l3 address cannot be null");
        return new ModIPInstruction(L3SubType.L3_DST, addr);
    }


    /*
     *  Output instructions
     */

    public static final class DropInstruction implements Instruction {
        @Override
        public Type type() {
            return Type.DROP;
        }
    }


    public static final class OutputInstruction implements Instruction {
        private final PortNumber port;

        private OutputInstruction(PortNumber port) {
            this.port = port;
        }

        public PortNumber port() {
            return port;
        }

        @Override
        public Type type() {
            return Type.OUTPUT;
        }
    }

}