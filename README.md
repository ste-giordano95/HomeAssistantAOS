# Home Assistant for AugmentOS Documentation

Overview

The HomeAssistantAOS Service is an Android background service that integrates with [AugmentOS](https://github.com/AugmentOS-Community/AugmentOS) and Home Assistant to process speech recognition input and respond accordingly. It listens for voice commands, sends them to Home Assistant, and displays the response on smart glasses.

## 📌Features

📡 Speech recognition event handling

🔗 Communication with Home Assistant via API

🌍 Dynamic transcription language management, See [LiveCaptionsOnSmartGlasses](https://github.com/AugmentOS-Community/LiveCaptionsOnSmartGlasses)

👓 Smart glasses integration for displaying responses


## 📌Dependencies

com.augmentos.augmentoslib.* (AugmentOS SDK)

org.greenrobot.eventbus.* (EventBus for event handling)

## 🚀 Usage

This service runs in the background, listening for voice commands. When it detects a final transcript, it checks if it matches an exit command. If not, it queries Home Assistant and displays the response on smart glasses.

## ⚠ Notes

Ensure that HomeAssistant Url, Token and languages are set in tpa config before using the service.

The service requires an active internet connection to communicate with Home Assistant.

## 📜 License

This project follows the applicable licensing terms for AugmentOS and Home Assistant integrations.
