package com.zain.bh.alm.financials.client;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DumpsClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(DumpsClient.class);

    public JSONObject dumpsUpdate(JSONObject request) {
        JSONObject responseMessage = new JSONObject();
        try {
            InputStream is;
            HttpURLConnection connection = null;
            URL url = new URL("http://10.22.28.93:8080/ALM_Inventory_Management/dump/postDumpDetails");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
            wr.writeBytes(request.toString());
            wr.flush();
            wr.close();
            if (connection.getResponseCode() < 400) {
                is = connection.getInputStream();
            } else {
                is = connection.getErrorStream();
            }
            BufferedReader rd = new BufferedReader(new InputStreamReader(is));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = rd.readLine()) != null) {
                response.append(line);
            }
            LOGGER.info("Charge ResponseCode: {} | {}", connection.getResponseCode(), response.toString());
            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> map = (Map<String, String>) mapper.readValue(response.toString(), Map.class);
            JSONObject responseFromReceiver = new JSONObject(map);
            String responseDesc = mapper.writeValueAsString(responseFromReceiver.get("response"));
            Map<String, String> mapresponseDesc = (Map<String, String>) mapper.readValue(responseDesc, Map.class);
            JSONObject mapresponseDescObject = new JSONObject(mapresponseDesc);
            String parameters = mapper.writeValueAsString(mapresponseDescObject.get("parameters"));
            Map<String, String> mapparameters = (Map<String, String>) mapper.readValue(parameters, Map.class);
            JSONObject chargesObject = new JSONObject(mapparameters);
            if (responseFromReceiver.has("responseCode")) {
                if (responseFromReceiver.getString("responseCode").equalsIgnoreCase("00")) {
                    responseMessage.put("responseCode", "00");
                    responseMessage.put("responseMessage", "SUCCESS");
                    responseMessage.put("charges", chargesObject.getString("charges"));
                    responseMessage.put("txnAmount", chargesObject.getString("amount"));
                } else {
                    responseMessage.put("responseCode", responseFromReceiver.getString("responseCode"));
                    responseMessage.put("responseMessage", "FAILED");
                }
            } else {
                responseMessage.put("responseCode", Integer.valueOf(connection.getResponseCode()));
                responseMessage.put("responseMessage", "FAILED");
            }
        } catch (Exception ex) {
            responseMessage.put("responseMessage", ex.getMessage());
            responseMessage.put("responseCode", "01");
            LOGGER.error("Exception in dumpsUpdate: {}", ex.getMessage());
        }
        return responseMessage;
    }
}
