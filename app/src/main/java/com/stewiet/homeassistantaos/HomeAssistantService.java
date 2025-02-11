package com.stewiet.homeassistantaos;

import android.util.Log;
import android.content.Context;

import com.augmentos.augmentoslib.AugmentOSSettingsManager;
import com.augmentos.augmentoslib.SmartGlassesAndroidService;
import com.augmentos.augmentoslib.AugmentOSLib;
import com.augmentos.augmentoslib.events.SpeechRecOutputEvent;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import org.json.JSONObject;
import android.os.Handler;
import android.os.Looper;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;


public class HomeAssistantService extends SmartGlassesAndroidService {
    public final String TAG = "HomeAssistantService";

    private Handler transcribeLanguageCheckHandler;
    private String lastTranscribeLanguage = null;

    private String Url = "";
    private String Token = "";

    private String language = "";

    List<String> exitTranslations = new ArrayList<>(Arrays.asList(
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

        // Create AugmentOSLib instance
        Log.d(TAG, "Servizio creato.");

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

        if (isFinal){
            Log.d(TAG, "Live Captions got final: " + text);
        }

        debounceAndShowTranscriptOnGlasses(text, isFinal);
    }

    private final Handler glassesTranscriptDebounceHandler = new Handler(Looper.getMainLooper());
    private Runnable glassesTranscriptDebounceRunnable;

    private void debounceAndShowTranscriptOnGlasses(String transcript, boolean isFinal) {
        glassesTranscriptDebounceHandler.removeCallbacks(glassesTranscriptDebounceRunnable);
        long currentTime = System.currentTimeMillis();
        augmentOSLib.sendDoubleTextWall("Domanda: ", transcript);

        if (isFinal) {

            if(!exitTranslations.contains(transcript.replaceAll("[.,]", "")))
            {

                Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(() -> {
                    ExecutorService executor = Executors.newSingleThreadExecutor();
                    Future<String>   future = executor.submit(() -> this.sendQueryToHomeAssistant(transcript.replaceAll("[.,]", "")));
                    executor.execute(() -> {
                        try {
                            String response = future.get();  // Aspetta la risposta
                            Log.d("RISPOSTA HA", response);
                            augmentOSLib.sendDoubleTextWall("Home Assistant: ", response);
                        } catch (Exception e) {
                            augmentOSLib.sendDoubleTextWall("Errore durante la richiesta", e.toString());
                            Log.e("ERRORE HA", "Errore durante la richiesta", e);
                        }
                    });

                    executor.shutdown();
                }, 2000); // 10 secondi di attesa
            }else{
                onDestroy();
            }
        }
    }
    public String sendQueryToHomeAssistant(String query) {
        if (Url == null || Token == null) {
            augmentOSLib.sendDoubleTextWall("Home Assistant: ", "Home Assistant URL e Token non sono impostati.");
            return "Home Assistant URL e Token non sono impostati.";
        }

        try {
            String requestUrl = Url + "/api/conversation/process";
            URL url = new URL(requestUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + Token);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            // 🔹 Corpo della richiesta JSON
            JSONObject jsonRequest = new JSONObject();
            jsonRequest.put("text", query);
            jsonRequest.put("language", language);

            OutputStream os = connection.getOutputStream();
            os.write(jsonRequest.toString().getBytes("UTF-8"));
            os.close();

            // 🔹 Legge la risposta JSON di Home Assistant
            Scanner scanner = new Scanner(connection.getInputStream());
            String responseBody = scanner.useDelimiter("\\A").next();
            scanner.close();
            connection.disconnect();

            // 🔹 Estrai il testo dalla risposta JSON
            return parseHomeAssistantResponse(responseBody);

        } catch (Exception e) {
            Log.e(TAG, "Errore nella richiesta a Home Assistant", e);
            return "Errore nella comunicazione con Home Assistant.";
        }
    }

    // 🔹 Estrae il testo della risposta JSON di Home Assistant
    private String parseHomeAssistantResponse(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            JSONObject response = jsonObject.optJSONObject("response");
            if (response != null) {
                JSONObject speech = response.optJSONObject("speech");
                if (speech != null) {
                    JSONObject plain = speech.optJSONObject("plain");
                    if (plain != null) {
                        return plain.optString("speech", "Nessuna risposta");
                    }
                }
            }
            return "Nessuna risposta valida ricevuta.";
        } catch (Exception e) {
            Log.e(TAG, "Errore nel parsing della risposta JSON", e);
            return "Errore nel parsing della risposta.";
        }
    }

    private void startTranscribeLanguageCheckTask() {
        transcribeLanguageCheckHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Get the currently selected transcription language
                String currentTranscribeLanguage = getChosenTranscribeLanguage(getApplicationContext());

                // If the language has changed or this is the first call
                if (lastTranscribeLanguage == null || !lastTranscribeLanguage.equals(currentTranscribeLanguage)) {
                    if (lastTranscribeLanguage != null) {
                        augmentOSLib.stopTranscription(lastTranscribeLanguage);
                    }
                    augmentOSLib.requestTranscription(currentTranscribeLanguage);

                    lastTranscribeLanguage = currentTranscribeLanguage;
                }

                // Schedule the next check
                transcribeLanguageCheckHandler.postDelayed(this, 333); // Approximately 3 times a second
            }
        }, 200);
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
