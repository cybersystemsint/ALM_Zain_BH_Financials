package com.zain.bh.alm.financials.dto;

public class ApiResponseDTO {
	private String responseCode;
	private String responseMessage;

	public ApiResponseDTO() {
	}

	public ApiResponseDTO(String responseCode, String responseMessage) {
		this.responseCode = responseCode;
		this.responseMessage = responseMessage;
	}

	public String getResponseCode() {
		return responseCode;
	}

	public void setResponseCode(String responseCode) {
		this.responseCode = responseCode;
	}

	public String getResponseMessage() {
		return responseMessage;
	}

	public void setResponseMessage(String responseMessage) {
		this.responseMessage = responseMessage;
	}

	@Override
	public String toString() {
		return "ApiResponseDTO [responseCode=" + responseCode + ", responseMessage=" + responseMessage + "]";
	}

}
