// Copyright (c) 2008 The Board of Trustees of The Leland Stanford Junior University
// Copyright (c) 2011, 2012 Open Networking Foundation
// Copyright (c) 2012, 2013 Big Switch Networks, Inc.
// This library was generated by the LoxiGen Compiler.
// See the file LICENSE.txt which should have been included in the source distribution

// Automatically generated by LOXI from template of_class.java
// Do not modify

package org.projectfloodlight.openflow.protocol.ver12;

import org.projectfloodlight.openflow.protocol.*;
import org.projectfloodlight.openflow.protocol.action.*;
import org.projectfloodlight.openflow.protocol.actionid.*;
import org.projectfloodlight.openflow.protocol.bsntlv.*;
import org.projectfloodlight.openflow.protocol.errormsg.*;
import org.projectfloodlight.openflow.protocol.meterband.*;
import org.projectfloodlight.openflow.protocol.instruction.*;
import org.projectfloodlight.openflow.protocol.instructionid.*;
import org.projectfloodlight.openflow.protocol.match.*;
import org.projectfloodlight.openflow.protocol.oxm.*;
import org.projectfloodlight.openflow.protocol.queueprop.*;
import org.projectfloodlight.openflow.types.*;
import org.projectfloodlight.openflow.util.*;
import org.projectfloodlight.openflow.exceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Set;
import org.jboss.netty.buffer.ChannelBuffer;
import com.google.common.hash.PrimitiveSink;
import com.google.common.hash.Funnel;

class OFOxmIpv6DstMaskedVer12 implements OFOxmIpv6DstMasked {
    private static final Logger logger = LoggerFactory.getLogger(OFOxmIpv6DstMaskedVer12.class);
    // version: 1.2
    final static byte WIRE_VERSION = 3;
    final static int LENGTH = 36;

        private final static IPv6Address DEFAULT_VALUE = IPv6Address.NONE;
        private final static IPv6Address DEFAULT_VALUE_MASK = IPv6Address.NONE;

    // OF message fields
    private final IPv6Address value;
    private final IPv6Address mask;
//
    // Immutable default instance
    final static OFOxmIpv6DstMaskedVer12 DEFAULT = new OFOxmIpv6DstMaskedVer12(
        DEFAULT_VALUE, DEFAULT_VALUE_MASK
    );

    // package private constructor - used by readers, builders, and factory
    OFOxmIpv6DstMaskedVer12(IPv6Address value, IPv6Address mask) {
        this.value = value;
        this.mask = mask;
    }

    // Accessors for OF message fields
    @Override
    public long getTypeLen() {
        return 0x80003720L;
    }

    @Override
    public IPv6Address getValue() {
        return value;
    }

    @Override
    public IPv6Address getMask() {
        return mask;
    }

    @Override
    public MatchField<IPv6Address> getMatchField() {
        return MatchField.IPV6_DST;
    }

    @Override
    public boolean isMasked() {
        return true;
    }

    public OFOxm<IPv6Address> getCanonical() {
        if (IPv6Address.NO_MASK.equals(mask)) {
            return new OFOxmIpv6DstVer12(value);
        } else if(IPv6Address.FULL_MASK.equals(mask)) {
            return null;
        } else {
            return this;
        }
    }

    @Override
    public OFVersion getVersion() {
        return OFVersion.OF_12;
    }



    public OFOxmIpv6DstMasked.Builder createBuilder() {
        return new BuilderWithParent(this);
    }

    static class BuilderWithParent implements OFOxmIpv6DstMasked.Builder {
        final OFOxmIpv6DstMaskedVer12 parentMessage;

        // OF message fields
        private boolean valueSet;
        private IPv6Address value;
        private boolean maskSet;
        private IPv6Address mask;

        BuilderWithParent(OFOxmIpv6DstMaskedVer12 parentMessage) {
            this.parentMessage = parentMessage;
        }

    @Override
    public long getTypeLen() {
        return 0x80003720L;
    }

    @Override
    public IPv6Address getValue() {
        return value;
    }

    @Override
    public OFOxmIpv6DstMasked.Builder setValue(IPv6Address value) {
        this.value = value;
        this.valueSet = true;
        return this;
    }
    @Override
    public IPv6Address getMask() {
        return mask;
    }

    @Override
    public OFOxmIpv6DstMasked.Builder setMask(IPv6Address mask) {
        this.mask = mask;
        this.maskSet = true;
        return this;
    }
    @Override
    public MatchField<IPv6Address> getMatchField() {
        return MatchField.IPV6_DST;
    }

    @Override
    public boolean isMasked() {
        return true;
    }

    @Override
    public OFOxm<IPv6Address> getCanonical()throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Property canonical not supported in version 1.2");
    }

    @Override
    public OFVersion getVersion() {
        return OFVersion.OF_12;
    }



        @Override
        public OFOxmIpv6DstMasked build() {
                IPv6Address value = this.valueSet ? this.value : parentMessage.value;
                if(value == null)
                    throw new NullPointerException("Property value must not be null");
                IPv6Address mask = this.maskSet ? this.mask : parentMessage.mask;
                if(mask == null)
                    throw new NullPointerException("Property mask must not be null");

                //
                return new OFOxmIpv6DstMaskedVer12(
                    value,
                    mask
                );
        }

    }

    static class Builder implements OFOxmIpv6DstMasked.Builder {
        // OF message fields
        private boolean valueSet;
        private IPv6Address value;
        private boolean maskSet;
        private IPv6Address mask;

    @Override
    public long getTypeLen() {
        return 0x80003720L;
    }

    @Override
    public IPv6Address getValue() {
        return value;
    }

    @Override
    public OFOxmIpv6DstMasked.Builder setValue(IPv6Address value) {
        this.value = value;
        this.valueSet = true;
        return this;
    }
    @Override
    public IPv6Address getMask() {
        return mask;
    }

    @Override
    public OFOxmIpv6DstMasked.Builder setMask(IPv6Address mask) {
        this.mask = mask;
        this.maskSet = true;
        return this;
    }
    @Override
    public MatchField<IPv6Address> getMatchField() {
        return MatchField.IPV6_DST;
    }

    @Override
    public boolean isMasked() {
        return true;
    }

    @Override
    public OFOxm<IPv6Address> getCanonical()throws UnsupportedOperationException {
        throw new UnsupportedOperationException("Property canonical not supported in version 1.2");
    }

    @Override
    public OFVersion getVersion() {
        return OFVersion.OF_12;
    }

//
        @Override
        public OFOxmIpv6DstMasked build() {
            IPv6Address value = this.valueSet ? this.value : DEFAULT_VALUE;
            if(value == null)
                throw new NullPointerException("Property value must not be null");
            IPv6Address mask = this.maskSet ? this.mask : DEFAULT_VALUE_MASK;
            if(mask == null)
                throw new NullPointerException("Property mask must not be null");


            return new OFOxmIpv6DstMaskedVer12(
                    value,
                    mask
                );
        }

    }


    final static Reader READER = new Reader();
    static class Reader implements OFMessageReader<OFOxmIpv6DstMasked> {
        @Override
        public OFOxmIpv6DstMasked readFrom(ChannelBuffer bb) throws OFParseError {
            // fixed value property typeLen == 0x80003720L
            int typeLen = bb.readInt();
            if(typeLen != (int) 0x80003720)
                throw new OFParseError("Wrong typeLen: Expected=0x80003720L(0x80003720L), got="+typeLen);
            IPv6Address value = IPv6Address.read16Bytes(bb);
            IPv6Address mask = IPv6Address.read16Bytes(bb);

            OFOxmIpv6DstMaskedVer12 oxmIpv6DstMaskedVer12 = new OFOxmIpv6DstMaskedVer12(
                    value,
                      mask
                    );
            if(logger.isTraceEnabled())
                logger.trace("readFrom - read={}", oxmIpv6DstMaskedVer12);
            return oxmIpv6DstMaskedVer12;
        }
    }

    public void putTo(PrimitiveSink sink) {
        FUNNEL.funnel(this, sink);
    }

    final static OFOxmIpv6DstMaskedVer12Funnel FUNNEL = new OFOxmIpv6DstMaskedVer12Funnel();
    static class OFOxmIpv6DstMaskedVer12Funnel implements Funnel<OFOxmIpv6DstMaskedVer12> {
        private static final long serialVersionUID = 1L;
        @Override
        public void funnel(OFOxmIpv6DstMaskedVer12 message, PrimitiveSink sink) {
            // fixed value property typeLen = 0x80003720L
            sink.putInt((int) 0x80003720);
            message.value.putTo(sink);
            message.mask.putTo(sink);
        }
    }


    public void writeTo(ChannelBuffer bb) {
        WRITER.write(bb, this);
    }

    final static Writer WRITER = new Writer();
    static class Writer implements OFMessageWriter<OFOxmIpv6DstMaskedVer12> {
        @Override
        public void write(ChannelBuffer bb, OFOxmIpv6DstMaskedVer12 message) {
            // fixed value property typeLen = 0x80003720L
            bb.writeInt((int) 0x80003720);
            message.value.write16Bytes(bb);
            message.mask.write16Bytes(bb);


        }
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("OFOxmIpv6DstMaskedVer12(");
        b.append("value=").append(value);
        b.append(", ");
        b.append("mask=").append(mask);
        b.append(")");
        return b.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        OFOxmIpv6DstMaskedVer12 other = (OFOxmIpv6DstMaskedVer12) obj;

        if (value == null) {
            if (other.value != null)
                return false;
        } else if (!value.equals(other.value))
            return false;
        if (mask == null) {
            if (other.mask != null)
                return false;
        } else if (!mask.equals(other.mask))
            return false;
        return true;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;

        result = prime * result + ((value == null) ? 0 : value.hashCode());
        result = prime * result + ((mask == null) ? 0 : mask.hashCode());
        return result;
    }

}