package io.mosip.commons.packet.facade;

import io.mosip.commons.packet.dto.Document;
import io.mosip.commons.packet.impl.PacketReaderImpl;
import io.mosip.commons.packet.spi.IPacketReader;
import io.mosip.kernel.biometrics.constant.BiometricType;
import io.mosip.kernel.biometrics.entities.BiometricRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The packet Reader facade
 *
 */
@Component
public class PacketReader {

    @Value("${mosip.commons.packet.provider.registration.source}")
    private String sources;

    @Value("${mosip.commons.packet.provider.registration.process}")
    private String processes;

    @Autowired
    private PacketReaderImpl registrationProvider;

    @Autowired(required = false)
    @Lazy
    private IPacketReader referenceProvider;

    /**
     * Get a field from identity file
     *
     * @param id : the registration id
     * @param field : field name to search
     * @param source : the source packet. If not present return default
     * @param process : the process
     * @return String field
     */
    @Cacheable(value = "packets", key = "#id.concat('-').concat(#field).concat('-').concat(#source).concat('-').concat(#process)", condition = "#bypassCache == false")
    public String getField(String id, String field, String source, String process, boolean bypassCache) {
        return getProvider(source, process).getField(id, field, process);
    }

    /**
     * Get fields from identity file
     *
     * @param id : the registration id
     * @param fields : fields to search
     * @param source : the source packet. If not present return default
     * @param process : the process
     * @return Map fields
     */
    @Cacheable(value = "packets", key = "#id.concat('-').concat(#fields).concat(#source).concat(#process)", condition = "#bypassCache == false")
    public Map<String, String> getFields(String id, List<String> fields, String source, String process, boolean bypassCache) {
        return getProvider(source, process).getFields(id, fields, process);
    }

    /**
     * Get document by registration id, document name, source and process
     *
     * @param id : the registration id
     * @param documentName : the document name
     * @param source : the source packet. If not present return default
     * @param process : the process
     * @return Document : document information
     */
    @Cacheable(value = "packets", key = "#id.concat('-').concat(#documentName).concat('-').concat(#source).concat('-').concat(#process)")
    public Document getDocument(String id, String documentName, String source, String process) {
        return getProvider(source, process).getDocument(id, documentName, process);
    }

    /**
     * Get biometric information by registration id, document name, source and process
     *
     * @param id : the registration id
     * @param person : The person (ex - applicant, operator, supervisor, introducer etc)
     * @param modalities : list of biometric modalities
     * @param source : the source packet. If not present return default
     * @param process : the process
     * @return BiometricRecord : the biometric record
     */
    @Cacheable(value = "packets", key = "#id.concat('-').concat(#person).concat('-').concat(#modalities).concat('-').concat(#source).concat('-').concat(#process)")
    public BiometricRecord getBiometric(String id, String person, List<BiometricType> modalities, String source, String process) {
        return getProvider(source, process).getBiometric(id, person, modalities, process);
    }

    /**
     * Get packet meta information by registration id, source and process
     *
     * @param id : the registration id
     * @param source : the source packet. If not present return default
     * @param process : the process
     * @return Map fields
     */
    @Cacheable(value = "packets",  key="{#id.concat('-').concat(#source).concat('-').concat(#process)}", condition = "#bypassCache")
    public Map<String, String> getMetaInfo(String id, String source, String process) {
        return getProvider(source, process).getMetaInfo(id, source, process);
    }

    /**
     * Get all fields from packet by id, source and process
     *
     * @param id : the registration id
     * @param source : the source packet. If not present return default
     * @param process : the process
     * @return Map fields
     */
    @Cacheable(value = "packets", key="{#id.concat('-').concat(#source).concat('-').concat(#process)}")
    private Map<String, Object> getAllFields(String id, String source, String process) {
        return getProvider(source, process).getAll(id, process);
    }

    private IPacketReader getProvider(String source, String process) {
        List<String> sourceList = Arrays.asList(sources.split(","));
        List<String> processList = Arrays.asList(processes.split(","));
        if (sourceList.contains(source) && processList.contains(process))
            return registrationProvider;
        else
            return referenceProvider;
    }

}
