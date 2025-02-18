/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.telkom.co.ke.almoptics.utilities;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import net.minidev.json.JSONObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author jgithu
 */
public class DumpsIntegrator {

    private final Logger LOGGER = LogManager.getLogger(com.telkom.co.ke.almoptics.utilities.DumpsIntegrator.class.getName());

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
            this.LOGGER.info("| Charge ResponseCode: " + connection.getResponseCode() + " | " + response.toString());
            ObjectMapper mapper = new ObjectMapper();
            Map<String, String> map = (Map<String, String>) mapper.readValue(response.toString(), Map.class);
            JSONObject responseFromReceiver = new JSONObject(map);
            String responseDesc = mapper.writeValueAsString(responseFromReceiver.get("response"));
            Map<String, String> mapresponseDesc = (Map<String, String>) mapper.readValue(responseDesc, Map.class);
            JSONObject mapresponseDescObject = new JSONObject(mapresponseDesc);
            String parameters = mapper.writeValueAsString(mapresponseDescObject.get("parameters"));
            Map<String, String> mapparameters = (Map<String, String>) mapper.readValue(parameters, Map.class);
            JSONObject chargesObject = new JSONObject(mapparameters);
            if (responseFromReceiver.containsKey("responseCode")) {
                if (responseFromReceiver.getAsString("responseCode").equalsIgnoreCase("00")) {
                    responseMessage.put("responseCode", "00");
                    responseMessage.put("responseMessage", "SUCCESS");
                    responseMessage.put("charges", chargesObject.getAsString("charges"));
                    responseMessage.put("txnAmount", chargesObject.getAsString("amount"));
                } else {
                    responseMessage.put("responseCode", responseFromReceiver.getAsString("responseCode"));
                    responseMessage.put("responseMessage", "FAILED");
                }
            } else {
                responseMessage.put("responseCode", Integer.valueOf(connection.getResponseCode()));
                responseMessage.put("responseMessage", "FAILED");
            }
        } catch (Exception ex) {
            responseMessage.put("responseMessage", ex);
            responseMessage.put("responseCode", "01");
            this.LOGGER.info("EXCEPTION" + ex.getMessage());
        }
        return responseMessage;
    }
}
