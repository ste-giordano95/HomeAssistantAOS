package com.stewiet.homeassistantaos;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class LauncherActivity extends AppCompatActivity {

    private static final String TAG = "LauncherActivity";
    private static final String TARGET_PACKAGE = "com.stewiet.homeassistantaos";
    private static final String DEEP_LINK_SCHEME = "homeassistantaos";
    private static final String DEEP_LINK_HOST = "open";
    private static final String FALLBACK_URL = "https://augmentos.org";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }
}