package com.zain.bh.alm.financials.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Httpcall {

    private static final Logger LOGGER = LogManager.getLogger(Httpcall.class);

    public HashMap<String, Object> httpPOST(String message, String httpsURL) {
        HashMap<String, Object> responseMap = new HashMap<>();
        try {
            URL obj = new URL(httpsURL);
            HttpURLConnection conn = (HttpURLConnection) obj.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setReadTimeout(45000);
            conn.setConnectTimeout(30000);
            conn.setRequestProperty("content-type", "application/json");

            try (OutputStreamWriter osw = new OutputStreamWriter(conn.getOutputStream())) {
                osw.write(message);
                osw.flush();
            }

            InputStreamReader in = getInputStream(conn);
            String res = readResponse(in);
            int conresponse = conn.getResponseCode();

            LOGGER.info("MESSAGE_FROM_MQ  RESPONSE {}", res);

            responseMap.put("mqresponse", res);
            responseMap.put("mqresponseCode", conresponse);
            if (conresponse == 200) {
                responseMap.put("mqStatus", "000");
                responseMap.put("mqStatusDescription", "Successful");
            } else {
                responseMap.put("mqStatus", "057");
                responseMap.put("mqStatusDescription", "Not Successful, response code (" + conresponse + ")");
            }

        } catch (Exception ex) {
            LOGGER.error("HTTP POST error", ex);
            if (ex.toString().contains("TimeoutException")) {
                responseMap.put("mqStatus", "091");
                responseMap.put("mqStatusDescription", "Not Successful TimeoutException: Cannot reach USSD Receiver");
            } else {
                responseMap.put("mqStatus", "057");
                responseMap.put("mqStatusDescription", "Not Successful");
            }
        }

        return responseMap;
    }

    private InputStreamReader getInputStream(HttpURLConnection conn) throws IOException {
        try {
            return new InputStreamReader(conn.getInputStream(), "UTF-8");
        } catch (IOException e) {
            LOGGER.warn("Failed to get input stream, using error stream", e);
            return new InputStreamReader(conn.getErrorStream(), "UTF-8");
        }
    }

    private String readResponse(InputStreamReader in) throws IOException {
        String res = "";
        try (Reader reader = new BufferedReader(in)) {
            for (int c; (c = reader.read()) >= 0; res = res + ((char) c))
                ;
        }
        return res;
    }
}
