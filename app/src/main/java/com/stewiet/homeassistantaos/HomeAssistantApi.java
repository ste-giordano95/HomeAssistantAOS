package com.stewiet.homeassistantaos;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Scanner;

public class HomeAssistantApi {
    private static final String TAG = "HomeAssistantApi";

    public String sendQueryToHomeAssistant(String url, String token, String language, String query) {
        if (url == null || token == null) {
            return "URL and Token not found.";
        }

        try {
            String requestUrl = url + "/api/conversation/process";
            HttpURLConnection connection = (HttpURLConnection) new URL(requestUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            JSONObject jsonRequest = new JSONObject();
            jsonRequest.put("text", query);
            jsonRequest.put("language", language);

            OutputStream os = connection.getOutputStream();
            os.write(jsonRequest.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

            Scanner scanner = new Scanner(connection.getInputStream());
            String responseBody = scanner.useDelimiter("\\A").next();
            scanner.close();
            connection.disconnect();

            return parseHomeAssistantResponse(responseBody);
        } catch (Exception e) {
            Log.e(TAG, "Error in request to Home Assistant", e);
            return "Error communicating with Home Assistant.";
        }
    }

    private String parseHomeAssistantResponse(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONObject plainSpeech = Optional.ofNullable(jsonObject.optJSONObject("response"))
                    .map(r -> r.optJSONObject("speech"))
                    .map(s -> s.optJSONObject("plain"))
                    .orElse(null);

            return (plainSpeech != null) ? plainSpeech.optString("speech", "no response received")
                    : "no valid response received.";
        } catch (JSONException e) {
            Log.e(TAG, "Error on parsing json ", e);
            return "Error on parsing json.";
        }
    }
}
