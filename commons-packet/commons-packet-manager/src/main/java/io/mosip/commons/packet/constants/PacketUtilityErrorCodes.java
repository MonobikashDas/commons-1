package io.mosip.commons.packet.constants;

public enum PacketUtilityErrorCodes {

	UNKNOWN_RESOURCE_EXCEPTION("KER-PUT-001",
			"Unknown resource provided"),
	FILE_NOT_FOUND_IN_DESTINATION("KER-PUT-002", "Unable to Find File in Destination Folder"),
	PACKET_DECRYPTION_FAILURE_EXCEPTION("KER-PUT-003", "Packet decryption failed"),
	API_NOT_ACCESSIBLE_EXCEPTION("KER-PUT-005", "API not accessible"),
	SYS_IO_EXCEPTION("KER-PUT-004", "Unable to Find File in Destination Folder"),
	GET_ALL_IDENTITY_EXCEPTION("KER-PUT-005", "Unable to fetch identity json from all sub packets"),
	SIGNATURE_EXCEPTION("KER-PUT-006", "Failed to generate digital signature");


	private final String errorCode;
	private final String errorMessage;

	private PacketUtilityErrorCodes(final String errorCode, final String errorMessage) {
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public String getErrorMessage() {
		return errorMessage;
	}
}
