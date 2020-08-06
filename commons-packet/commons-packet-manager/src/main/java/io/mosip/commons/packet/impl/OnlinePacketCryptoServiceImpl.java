package io.mosip.commons.packet.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;

import io.mosip.commons.packet.dto.packet.CryptomanagerRequestDto;
import io.mosip.commons.packet.dto.packet.CryptomanagerResponseDto;
import io.mosip.commons.packet.exception.ApiNotAccessibleException;
import io.mosip.commons.packet.exception.PacketDecryptionFailureException;
import io.mosip.kernel.core.exception.ServiceError;
import io.mosip.kernel.core.util.CryptoUtil;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.commons.packet.dto.SignRequestDto;
import io.mosip.commons.packet.exception.SignatureException;
import io.mosip.commons.packet.spi.IPacketCryptoService;
import io.mosip.kernel.core.http.RequestWrapper;
import io.mosip.kernel.core.util.DateUtils;

@Component
@Qualifier("OnlinePacketCryptoServiceImpl")
public class OnlinePacketCryptoServiceImpl implements IPacketCryptoService {

    /**
     * The Constant APPLICATION_ID.
     */
    public static final String APPLICATION_ID = "REGISTRATION";
    private static final String DECRYPT_SERVICE_ID = "mosip.cryptomanager.decrypt";
    private static final String IO_EXCEPTION = "Exception while reading packet inputStream";
    private static final String DATE_TIME_EXCEPTION = "Error while parsing packet timestamp";

    @Value("${mosip.utc-datetime-pattern}")
    private String DATETIME_PATTERN;

    @Value("${mosip.kernel.cryptomanager.request_version}")
    private String APPLICATION_VERSION;

    @Value("${mosip.kernel.registrationcenterid.length}")
    private int centerIdLength;

    @Value("${CRYPTOMANAGER_DECRYPT}")
    private String cryptomanagerDecryptUrl;

    @Value("${mosip.kernel.machineid.length}")
    private int machineIdLength;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper mapper;

    @Value("${CRYPTOMANAGER_ENCRYPT}")
    private String cryptomanagerEncryptUrl;

    @Value("${KEYMANAGER_SIGN}")
    private String keymanagerSignUrl;

    @Override
    public byte[] sign(byte[] packet) {
        try {
            String packetData = new String(packet, StandardCharsets.UTF_8);
            SignRequestDto dto = new SignRequestDto();
            dto.setData(packetData);
            RequestWrapper<SignRequestDto> request = new RequestWrapper<>();
            request.setRequest(dto);
            request.setMetadata(null);
            DateTimeFormatter format = DateTimeFormatter.ofPattern(DATETIME_PATTERN);
            LocalDateTime localdatetime = LocalDateTime
                    .parse(DateUtils.getUTCCurrentDateTimeString(DATETIME_PATTERN), format);
            request.setRequesttime(localdatetime);
            HttpEntity<RequestWrapper<SignRequestDto>> httpEntity = new HttpEntity<>(request);
            ResponseEntity<String> response = restTemplate.exchange(keymanagerSignUrl, HttpMethod.POST, httpEntity,
                    String.class);
            LinkedHashMap responseMap = (LinkedHashMap) mapper.readValue(response.getBody(), LinkedHashMap.class).get("response");//.get("signature");
            if (responseMap != null && responseMap.size() > 0)
                return responseMap.get("signature").toString().getBytes();
            else
                throw new SignatureException();
        } catch (IOException e) {
            new SignatureException(e);
        }
        return null;
    }

    @Override
    public byte[] encrypt(String id, byte[] packet) {
        byte[] encryptedPacket = null;

        try {
            String centerId = id.substring(0, centerIdLength);
            String machineId = id.substring(centerIdLength, centerIdLength + machineIdLength);
            String refId = centerId + "_" + machineId;
            String packetString = CryptoUtil.encodeBase64String(packet);
            CryptomanagerRequestDto cryptomanagerRequestDto = new CryptomanagerRequestDto();
            RequestWrapper<CryptomanagerRequestDto> request = new RequestWrapper<>();
            cryptomanagerRequestDto.setApplicationId(APPLICATION_ID);
            cryptomanagerRequestDto.setData(packetString);
            cryptomanagerRequestDto.setReferenceId(refId);
            // setLocal Date Time
            if (id.length() > 14) {
                String packetCreatedDateTime = id.substring(id.length() - 14);
                String formattedDate = packetCreatedDateTime.substring(0, 8) + "T"
                        + packetCreatedDateTime.substring(packetCreatedDateTime.length() - 6);

                cryptomanagerRequestDto.setTimeStamp(
                        LocalDateTime.parse(formattedDate, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")));
            } else {
                throw new PacketDecryptionFailureException("Packet Encryption Failed-Invalid Packet format");
            }
            request.setId(DECRYPT_SERVICE_ID);
            request.setMetadata(null);
            request.setRequest(cryptomanagerRequestDto);
            DateTimeFormatter format = DateTimeFormatter.ofPattern(DATETIME_PATTERN);
            LocalDateTime localdatetime = LocalDateTime
                    .parse(DateUtils.getUTCCurrentDateTimeString(DATETIME_PATTERN), format);
            request.setRequesttime(localdatetime);
            request.setVersion(APPLICATION_VERSION);
            HttpEntity<RequestWrapper<CryptomanagerRequestDto>> httpEntity = new HttpEntity<>(request);

            ResponseEntity<String> response = restTemplate.exchange(cryptomanagerEncryptUrl, HttpMethod.POST, httpEntity, String.class);

            CryptomanagerResponseDto responseObject = mapper.readValue(response.getBody(), CryptomanagerResponseDto.class);

            if (responseObject != null &&
                    responseObject.getErrors() != null && !responseObject.getErrors().isEmpty()) {
                ServiceError error = responseObject.getErrors().get(0);
                throw new PacketDecryptionFailureException(error.getMessage());
            }
            encryptedPacket = responseObject.getResponse().getData().getBytes();

        } catch (IOException e) {
            throw new PacketDecryptionFailureException(IO_EXCEPTION, e);
        } catch (DateTimeParseException e) {
            throw new PacketDecryptionFailureException(DATE_TIME_EXCEPTION);
        } catch (Exception e) {
            if (e.getCause() instanceof HttpClientErrorException) {
                HttpClientErrorException httpClientException = (HttpClientErrorException) e.getCause();
                throw new ApiNotAccessibleException(httpClientException.getResponseBodyAsString());
            } else if (e.getCause() instanceof HttpServerErrorException) {
                HttpServerErrorException httpServerException = (HttpServerErrorException) e.getCause();
                throw new ApiNotAccessibleException(httpServerException.getResponseBodyAsString());
            } else {
                throw new PacketDecryptionFailureException(e);
            }

        }
        return encryptedPacket;
    }

    @Override
    public byte[] decrypt(String id, byte[] packet) {
        byte[] decryptedPacket = null;

        try {
            String centerId = id.substring(0, centerIdLength);
            String machineId = id.substring(centerIdLength, centerIdLength + machineIdLength);
            String refId = centerId + "_" + machineId;
            String packetString = IOUtils.toString(packet, "UTF-8");
            CryptomanagerRequestDto cryptomanagerRequestDto = new CryptomanagerRequestDto();
            RequestWrapper<CryptomanagerRequestDto> request = new RequestWrapper<>();
            cryptomanagerRequestDto.setApplicationId(APPLICATION_ID);
            cryptomanagerRequestDto.setData(packetString);
            cryptomanagerRequestDto.setReferenceId(refId);
            // setLocal Date Time
            if (id.length() > 14) {
                String packetCreatedDateTime = id.substring(id.length() - 14);
                String formattedDate = packetCreatedDateTime.substring(0, 8) + "T"
                        + packetCreatedDateTime.substring(packetCreatedDateTime.length() - 6);

                cryptomanagerRequestDto.setTimeStamp(
                        LocalDateTime.parse(formattedDate, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")));
            } else {
                throw new PacketDecryptionFailureException("Packet DecryptionFailed-Invalid Packet format");
            }
            request.setId(DECRYPT_SERVICE_ID);
            request.setMetadata(null);
            request.setRequest(cryptomanagerRequestDto);
            DateTimeFormatter format = DateTimeFormatter.ofPattern(DATETIME_PATTERN);
            LocalDateTime localdatetime = LocalDateTime
                    .parse(DateUtils.getUTCCurrentDateTimeString(DATETIME_PATTERN), format);
            request.setRequesttime(localdatetime);
            request.setVersion(APPLICATION_VERSION);
            HttpEntity<RequestWrapper<CryptomanagerRequestDto>> httpEntity = new HttpEntity<>(request);

            ResponseEntity<String> response = restTemplate.exchange(cryptomanagerDecryptUrl, HttpMethod.POST, httpEntity, String.class);

            CryptomanagerResponseDto responseObject = mapper.readValue(response.getBody(), CryptomanagerResponseDto.class);

            if (responseObject != null &&
                    responseObject.getErrors() != null && !responseObject.getErrors().isEmpty()) {
                ServiceError error = responseObject.getErrors().get(0);
                throw new PacketDecryptionFailureException(error.getMessage());
            }
            decryptedPacket = CryptoUtil.decodeBase64(responseObject.getResponse().getData());

        } catch (IOException e) {
            throw new PacketDecryptionFailureException(IO_EXCEPTION, e);
        } catch (DateTimeParseException e) {
            throw new PacketDecryptionFailureException(DATE_TIME_EXCEPTION);
        } catch (Exception e) {
            if (e.getCause() instanceof HttpClientErrorException) {
                HttpClientErrorException httpClientException = (HttpClientErrorException) e.getCause();
                throw new ApiNotAccessibleException(httpClientException.getResponseBodyAsString());
            } else if (e.getCause() instanceof HttpServerErrorException) {
                HttpServerErrorException httpServerException = (HttpServerErrorException) e.getCause();
                throw new ApiNotAccessibleException(httpServerException.getResponseBodyAsString());
            } else {
                throw new PacketDecryptionFailureException(e);
            }

        }
        return decryptedPacket;
    }

    @Override
    public boolean verify(byte[] packet) {
        // TODO Auto-generated method stub
        return false;
    }

}
