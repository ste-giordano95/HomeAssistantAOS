package com.stewiet.homeassistantaos;

import android.util.Log;
import android.content.Context;
import android.os.Looper;

import com.augmentos.augmentoslib.AugmentOSSettingsManager;
import com.augmentos.augmentoslib.SmartGlassesAndroidService;
import com.augmentos.augmentoslib.AugmentOSLib;
import com.augmentos.augmentoslib.events.SpeechRecOutputEvent;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

import org.json.JSONException;
import org.json.JSONObject;
import android.os.Handler;

import org.greenrobot.eventbus.Subscribe;


public class HomeAssistantService extends SmartGlassesAndroidService {
    public final String TAG = "HomeAssistantService";

    private Handler transcribeLanguageCheckHandler;
    private String lastTranscribeLanguage = null;

    private String Url = "";
    private String Token = "";

    private String language = "";

    private final List<String> exitTranslations = new ArrayList<>(Arrays.asList(
            "Esci",         // Italiano
            "Exit",        // Inglese
            "Выход",       // Russo
            "退出",         // Cinese
            "Salir",       // Spagnolo
            "終了",         // Giapponese
            "Afsluiten",   // Olandese
            "Poistu",      // Finlandese
            "Quitter",     // Francese
            "Beenden",     // Tedesco
            "종료",         // Coreano
            "Çıkış",       // Turco
            "Sair"         // Portoghese
    ));


    // Our instance of the AugmentOS library
    public AugmentOSLib augmentOSLib;
    public HomeAssistantService() {
        super();
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void setup() {
        augmentOSLib = new AugmentOSLib(this);

        Url = AugmentOSSettingsManager.getSelectSetting(this, "hassioUrl");
        Token = AugmentOSSettingsManager.getSelectSetting(this, "hassioToken");
        language = AugmentOSSettingsManager.getSelectSetting(this, "language");

        // Initialize the language check handler
        transcribeLanguageCheckHandler = new Handler(Looper.getMainLooper());

        // Start periodic language checking
        startTranscribeLanguageCheckTask();
    }

    @Subscribe
    public void onTranscript(SpeechRecOutputEvent event) {
        String text = event.text;
        String languageCode = event.languageCode;
        long time = event.timestamp;
        boolean isFinal = event.isFinal;

        debounceAndShowTranscriptOnGlasses(text, isFinal);
    }

    private final Handler glassesTranscriptDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable glassesTranscriptDebounceRunnable;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private void debounceAndShowTranscriptOnGlasses(String transcript, boolean isFinal) {
        glassesTranscriptDebounceHandler.removeCallbacks(glassesTranscriptDebounceRunnable);
        augmentOSLib.sendDoubleTextWall("Command: ", transcript);

        if (!isFinal) return;

        String cleanedTranscript = transcript.replaceAll("[.,]", "");

        if (exitTranslations.contains(cleanedTranscript)) {
            onDestroy();
            return;
        }

        mainHandler.postDelayed(() -> fetchAndDisplayResponse(cleanedTranscript), 2000); // 2 secondi di attesa
    }

    private void fetchAndDisplayResponse(String transcript) {
        Future<String> futureResponse = executorService.submit(() -> sendQueryToHomeAssistant(transcript));

        executorService.execute(() -> {
            try {
                String response = futureResponse.get();
                augmentOSLib.sendDoubleTextWall("Home Assistant: ", response);
            } catch (InterruptedException | ExecutionException e) {
                Log.e("ERRORE HA", "Errore durante la richiesta", e);
                augmentOSLib.sendDoubleTextWall("Error: ", e.toString());
                Thread.currentThread().interrupt();
            }
        });
    }

    public String sendQueryToHomeAssistant(String query) {
        if (Url == null || Token == null) {
            augmentOSLib.sendDoubleTextWall(getString(R.string.app_name).concat(" :"), "URL and Token not found.");
            return getString(R.string.app_name).concat( " URL and Token not found..");
        }

        try {
            String requestUrl = Url + "/api/conversation/process";
            URL url = new URL(requestUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + Token);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            //Request
            JSONObject jsonRequest = new JSONObject();
            jsonRequest.put("text", query);
            jsonRequest.put("language", language);

            OutputStream os = connection.getOutputStream();
            os.write(jsonRequest.toString().getBytes("UTF-8"));
            os.close();

            //Response
            Scanner scanner = new Scanner(connection.getInputStream());
            String responseBody = scanner.useDelimiter("\\A").next();
            scanner.close();
            connection.disconnect();

            return parseHomeAssistantResponse(responseBody);

        } catch (Exception e) {
            Log.e(TAG, "Errore nella richiesta a Home Assistant", e);
            return "Errore nella comunicazione con Home Assistant.";
        }
    }

    private String parseHomeAssistantResponse(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONObject plainSpeech = Optional.ofNullable(jsonObject.optJSONObject("response"))
                    .map(r -> r.optJSONObject("speech"))
                    .map(s -> s.optJSONObject("plain"))
                    .orElse(null);

            return (plainSpeech != null) ? plainSpeech.optString("speech", "Nessuna risposta")
                    : "Nessuna risposta valida ricevuta.";
        } catch (JSONException e) {
            Log.e(TAG, "Errore nel parsing della risposta JSON", e);
            return "Errore nel parsing della risposta.";
        }
    }

    private final Runnable transcribeLanguageCheckTask = this::checkAndUpdateTranscriptionLanguage;
    private static final long CHECK_INTERVAL_MS = 333; // 3 volte al secondo

    private void startTranscribeLanguageCheckTask() {
        transcribeLanguageCheckHandler.postDelayed(transcribeLanguageCheckTask, 200);
    }

    private void checkAndUpdateTranscriptionLanguage() {
        String currentTranscribeLanguage = getChosenTranscribeLanguage(getApplicationContext());

        if (!currentTranscribeLanguage.equals(lastTranscribeLanguage)) {
            if (lastTranscribeLanguage != null) {
                augmentOSLib.stopTranscription(lastTranscribeLanguage);
            }
            augmentOSLib.requestTranscription(currentTranscribeLanguage);
            lastTranscribeLanguage = currentTranscribeLanguage;
        }

        transcribeLanguageCheckHandler.postDelayed(transcribeLanguageCheckTask, CHECK_INTERVAL_MS);
    }

    public static String getChosenTranscribeLanguage(Context context) {
        String transcribeLanguageString = AugmentOSSettingsManager.getStringSetting(context, "transcribe_language");
        if (transcribeLanguageString.isEmpty()){
            saveChosenTranscribeLanguage(context, "Chinese");
            transcribeLanguageString = "Chinese";
        }
        return transcribeLanguageString;
    }
    public static void saveChosenTranscribeLanguage(Context context, String transcribeLanguageString) {
        AugmentOSSettingsManager.setStringSetting(context, "transcribe_language", transcribeLanguageString);
    }

    @Override
    public void onDestroy() {
        // deInit your augmentOSLib instance onDestroy
        augmentOSLib.deinit();
        super.onDestroy();
    }
}
