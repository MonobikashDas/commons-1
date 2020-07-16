package io.mosip.commons.packet.impl;

import io.mosip.commons.packet.dto.PacketInfo;
import io.mosip.commons.packet.spi.IPacketWriter;
import org.springframework.stereotype.Component;

@Component
public abstract class PacketWriterBase implements IPacketWriter {

    public final PacketInfo persistPacket() {
        // TODO : signature, encryption, zip operations.
        //        //packetKeeper.putPacket();
        return null;
    }
}
