package com.zain.bh.alm.financials.util;

import org.json.JSONObject;

public final class ResponseBuilder {

    private static final String RESPONSE_CODE = "responseCode";
    private static final String RESPONSE_MESSAGE = "responseMessage";

    private ResponseBuilder() {
    }

    public static JSONObject success(String message) {
        JSONObject response = new JSONObject();
        response.put(RESPONSE_CODE, "0");
        response.put(RESPONSE_MESSAGE, message);
        return response;
    }

    public static JSONObject error(String message) {
        JSONObject response = new JSONObject();
        response.put(RESPONSE_CODE, "1");
        response.put(RESPONSE_MESSAGE, message);
        return response;
    }

    public static JSONObject customCode(String code, String message) {
        JSONObject response = new JSONObject();
        response.put(RESPONSE_CODE, code);
        response.put(RESPONSE_MESSAGE, message);
        return response;
    }
}
