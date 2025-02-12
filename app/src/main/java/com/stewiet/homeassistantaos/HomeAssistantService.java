package com.stewiet.homeassistantaos;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.augmentos.augmentoslib.*;
import com.augmentos.augmentoslib.events.SpeechRecOutputEvent;

import org.greenrobot.eventbus.Subscribe;

public class HomeAssistantService extends SmartGlassesAndroidService {
    private static final long CHECK_INTERVAL_MS = 333;

    private Handler transcribeLanguageCheckHandler;
    private String lastTranscribeLanguage = null;

    private String url, token, language, exitWord;
    private AugmentOSLib augmentOSLib;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final HomeAssistantApi homeAssistantApi;

    public HomeAssistantService() {
        this.homeAssistantApi = new HomeAssistantApi();
    }

    @Override
    public void setup() {
        augmentOSLib = new AugmentOSLib(this);
        loadSettings();
        transcribeLanguageCheckHandler = new Handler(Looper.getMainLooper());
        startTranscribeLanguageCheckTask();
    }

    private void loadSettings() {
        url = AugmentOSSettingsManager.getSelectSetting(this, "hassioUrl");
        token = AugmentOSSettingsManager.getSelectSetting(this, "hassioToken");
        language = AugmentOSSettingsManager.getSelectSetting(this, "language");
        exitWord = AugmentOSSettingsManager.getSelectSetting(this, "exitWord");
    }

    @Subscribe
    public void onTranscript(SpeechRecOutputEvent event) {
        processTranscript(event.text, event.isFinal);
    }

    private void processTranscript(String transcript, boolean isFinal) {
        augmentOSLib.sendDoubleTextWall("Command:", transcript);
        if (!isFinal) return;

        String cleanedTranscript = transcript.replaceAll("[.,]", "");
        if (exitWord.equalsIgnoreCase(cleanedTranscript)) {
            onDestroy();
            return;
        }

        mainHandler.postDelayed(() -> fetchAndDisplayResponse(cleanedTranscript), 2000);
    }

    private void fetchAndDisplayResponse(String transcript) {
            String response =  homeAssistantApi.sendQueryToHomeAssistant(url, token, language, transcript);
            augmentOSLib.sendDoubleTextWall("Home Assistant:", response);
    }

    private void startTranscribeLanguageCheckTask() {
        transcribeLanguageCheckHandler.postDelayed(this::checkAndUpdateTranscriptionLanguage, 200);
    }

    private void checkAndUpdateTranscriptionLanguage() {
        String currentTranscribeLanguage = getChosenTranscribeLanguage(getApplicationContext());
        if (!currentTranscribeLanguage.equals(lastTranscribeLanguage)) {
            if (lastTranscribeLanguage != null) augmentOSLib.stopTranscription(lastTranscribeLanguage);
            augmentOSLib.requestTranscription(currentTranscribeLanguage);
            lastTranscribeLanguage = currentTranscribeLanguage;
        }
        transcribeLanguageCheckHandler.postDelayed(this::checkAndUpdateTranscriptionLanguage, CHECK_INTERVAL_MS);
    }

    public static String getChosenTranscribeLanguage(Context context) {
        String transcribeLanguage = AugmentOSSettingsManager.getStringSetting(context, "transcribe_language");
        if (transcribeLanguage.isEmpty()) {
            saveChosenTranscribeLanguage(context, "Chinese");
            return "Chinese";
        }
        return transcribeLanguage;
    }

    public static void saveChosenTranscribeLanguage(Context context, String transcribeLanguage) {
        AugmentOSSettingsManager.setStringSetting(context, "transcribe_language", transcribeLanguage);
    }

    @Override
    public void onDestroy() {
        augmentOSLib.deinit();
        super.onDestroy();
    }
}