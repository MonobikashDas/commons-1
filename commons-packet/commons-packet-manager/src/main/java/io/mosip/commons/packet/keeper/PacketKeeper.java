package io.mosip.commons.packet.keeper;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.commons.khazana.spi.ObjectStoreAdapter;
import io.mosip.commons.packet.constants.PacketUtilityErrorCodes;
import io.mosip.commons.packet.dto.Packet;
import io.mosip.commons.packet.dto.Manifest;
import io.mosip.commons.packet.dto.PacketInfo;
import io.mosip.commons.packet.exception.CryptoException;
import io.mosip.commons.packet.exception.ObjectStoreAdapterException;
import io.mosip.commons.packet.exception.PacketIntegrityFailureException;
import io.mosip.commons.packet.exception.PacketKeeperException;
import io.mosip.commons.packet.spi.IPacketCryptoService;
import io.mosip.commons.packet.util.PacketManagerHelper;
import io.mosip.commons.packet.util.PacketManagerLogger;
import io.mosip.kernel.core.exception.BaseCheckedException;
import io.mosip.kernel.core.exception.BaseUncheckedException;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.core.util.HMACUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

/**
 * The packet keeper is used to store & retrieve packet, creation of audit, encrypt and sign packet.
 * Packet keeper is used to get container information and list of sources from a packet.
 */
@Component
public class PacketKeeper {

    @Autowired
    private ObjectMapper mapper;

    /**
     * The reg proc logger.
     */
    private static Logger LOGGER = PacketManagerLogger.getLogger(PacketKeeper.class);

    private static final String PACKET_MANAGER_ACCOUNT = "PACKET_MANAGER_ACCOUNT";

    @Autowired
    @Qualifier("SwiftAdapter")
    private ObjectStoreAdapter swiftAdapter;

    @Autowired
    @Qualifier("S3Adapter")
    private ObjectStoreAdapter s3Adapter;

    @Autowired
    @Qualifier("PosixAdapter")
    private ObjectStoreAdapter posixAdapter;

    @Value("${objectstore.adapter.name}")
    private String adapterName;

    @Value("${objectstore.crypto.name}")
    private String cryptoName;

    @Autowired
    @Qualifier("OnlinePacketCryptoServiceImpl")
    private IPacketCryptoService onlineCrypto;

    @Autowired
    @Qualifier("OfflinePacketCryptoServiceImpl")
    private IPacketCryptoService offlineCrypto;

    private static final String UNDERSCORE = "_";

    /**
     * Get the manifest information for given packet id
     *
     * @param id : packet id
     * @return : Manifest
     */
    public Manifest getManifest(String id) {
        Manifest manifest = new Manifest();

        Map<String, Object> metaMap = getAdapter().getMetaData(PACKET_MANAGER_ACCOUNT, id, null);

        metaMap.entrySet().forEach(entry -> {
            Map<String, Object> tempMap = (Map<String, Object>) entry.getValue();
            PacketInfo packetInfo = PacketManagerHelper.getPacketInfo(tempMap);
            manifest.getPacketInfos().add(packetInfo);
        });


        return manifest;
    }

    /**
     * Check packet integrity.
     *
     * @param packetInfo : the packet information
     * @return : boolean
     */
    public boolean checkIntegrity(PacketInfo packetInfo, byte[] encryptedSubPacket) {
        String hash = new String(CryptoUtil.encodeBase64(HMACUtils.generateHash(encryptedSubPacket)));
        boolean result = hash.equals(packetInfo.getEncryptedHash());
        LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, packetInfo.getId(), "Integrity check : " + result);
        return result;
    }

    public boolean checkSignature(Packet packet, byte[] encryptedSubPacket) {
        String signature = new String(CryptoUtil.encodeBase64(getCryptoService().sign(packet.getPacket())));
        boolean result = checkIntegrity(packet.getPacketInfo(), encryptedSubPacket) && (signature.equals(packet.getPacketInfo().getSignature()));
        LOGGER.info(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, packet.getPacketInfo().getId(), "Integrity and signature check : " + result);
        return result;
    }

    /**
     * Get packet
     *
     * @param packetInfo : packet info
     * @return : Packet
     */
    public Packet getPacket(PacketInfo packetInfo) throws PacketKeeperException {
        try {
            InputStream is = getAdapter().getObject(PACKET_MANAGER_ACCOUNT, packetInfo.getId(), getName(packetInfo.getId(), packetInfo.getPacketName()));
            byte[] encryptedSubPacket = IOUtils.toByteArray(is);
            byte[] subPacket = getCryptoService().decrypt(packetInfo.getId(), encryptedSubPacket);

            Packet packet = new Packet();
            packet.setPacket(subPacket);
            Map<String, Object> metaInfo = getAdapter().getMetaData(PACKET_MANAGER_ACCOUNT, packetInfo.getId(), getName(packetInfo.getId(), packetInfo.getPacketName()));
            if (metaInfo != null && !metaInfo.isEmpty())
                packet.setPacketInfo(PacketManagerHelper.getPacketInfo(metaInfo));


                if (!checkSignature(packet, encryptedSubPacket)) {
                LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, packet.getPacketInfo().getId(), "Packet Integrity and Signature check failed");
                throw new PacketIntegrityFailureException();
            }


            return packet;
        } catch (Exception e) {
            LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, packetInfo.getId(), e.getStackTrace().toString());
            if (e instanceof BaseCheckedException) {
                BaseCheckedException ex = (BaseCheckedException) e;
                throw new PacketKeeperException(ex.getErrorCode(), ex.getMessage());
            }
            else if (e instanceof BaseUncheckedException) {
                BaseUncheckedException ex = (BaseUncheckedException) e;
                throw new PacketKeeperException(ex.getErrorCode(), ex.getMessage());
            } else
                throw new PacketKeeperException(PacketUtilityErrorCodes.PACKET_KEEPER_GET_ERROR.getErrorCode(),
                    "Failed to get packet from object store : " + e.getMessage(), e);
        }
    }

    /**
     * Put packet into storage/cache
     *
     * @param packet : the Packet
     * @return PacketInfo
     */
    public PacketInfo putPacket(Packet packet) throws PacketKeeperException {
        try {
            // encrypt packet
            byte[] encryptedSubPacket = getCryptoService().encrypt(packet.getPacketInfo().getId(), packet.getPacket());

            // put packet in object store
            boolean response = getAdapter().putObject(PACKET_MANAGER_ACCOUNT,
                    packet.getPacketInfo().getId(), packet.getPacketInfo().getPacketName(), new ByteArrayInputStream(encryptedSubPacket));

            if (response) {
                PacketInfo packetInfo = packet.getPacketInfo();
                // sign encrypted packet
                packetInfo.setSignature(new String(CryptoUtil.encodeBase64(getCryptoService().sign(packet.getPacket()))));
                // generate encrypted packet hash
                packetInfo.setEncryptedHash(new String(CryptoUtil.encodeBase64(HMACUtils.generateHash(encryptedSubPacket))));
                Map<String, Object> metaMap = PacketManagerHelper.getMetaMap(packetInfo);
                metaMap = getAdapter().addObjectMetaData(PACKET_MANAGER_ACCOUNT,
                        packet.getPacketInfo().getId(), packet.getPacketInfo().getPacketName(), metaMap);
                return PacketManagerHelper.getPacketInfo(metaMap);
            } else
                throw new PacketKeeperException(PacketUtilityErrorCodes
                        .PACKET_KEEPER_PUT_ERROR.getErrorCode(), "Unable to store packet in object store");


        } catch (Exception e) {
            LOGGER.error(PacketManagerLogger.SESSIONID, PacketManagerLogger.REGISTRATIONID, packet.getPacketInfo().getId(), e.getStackTrace().toString());
            if (e instanceof BaseCheckedException) {
                BaseCheckedException ex = (BaseCheckedException) e;
                throw new PacketKeeperException(ex.getErrorCode(), ex.getMessage());
            } else if (e instanceof BaseUncheckedException) {
                BaseUncheckedException ex = (BaseUncheckedException) e;
                throw new PacketKeeperException(ex.getErrorCode(), ex.getMessage());
            }
            throw new PacketKeeperException(PacketUtilityErrorCodes.PACKET_KEEPER_PUT_ERROR.getErrorCode(),
                    "Failed to persist packet in object store : " + e.getMessage(), e);
        }
    }

    private ObjectStoreAdapter getAdapter() {
        if (adapterName.equalsIgnoreCase(swiftAdapter.getClass().getSimpleName()))
            return swiftAdapter;
        else if (adapterName.equalsIgnoreCase(posixAdapter.getClass().getSimpleName()))
            return posixAdapter;
        else if (adapterName.equalsIgnoreCase(s3Adapter.getClass().getSimpleName()))
            return s3Adapter;
        else
            throw new ObjectStoreAdapterException();
    }

    private IPacketCryptoService getCryptoService() {
        if (cryptoName.equalsIgnoreCase(onlineCrypto.getClass().getSimpleName()))
            return onlineCrypto;
        else if (cryptoName.equalsIgnoreCase(offlineCrypto.getClass().getSimpleName()))
            return offlineCrypto;
        else
            throw new CryptoException();
    }

    private String getName(String id, String name) {
        return id + UNDERSCORE + name;
    }

}