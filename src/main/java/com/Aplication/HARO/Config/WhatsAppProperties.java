package com.Aplication.HARO.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "whatsapp")
public class WhatsAppProperties {

    private boolean enabled = false;
    private String apiBaseUrl = "https://graph.facebook.com";
    private String apiVersion = "v21.0";
    private String phoneNumberId;
    private String accessToken;
    private String defaultTo;
    private boolean restrictToDefault = false;
    private String defaultLanguageCode = "es_CO";
    private String webhookVerifyToken;
    private String appSecret;
    private boolean botEnabled = false;
    private String botWelcomeReply = "👋 ¡Bienvenido a CEA HARO! Soy tu asistente virtual. Escribe MENU para continuar.";
    private String botFallbackReply = "🤖 Gracias por escribir a CEA HARO. Escribe MENU para ver opciones.";
    private int connectTimeoutMs = 10_000;
    private int readTimeoutMs = 20_000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public void setApiBaseUrl(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public void setPhoneNumberId(String phoneNumberId) {
        this.phoneNumberId = phoneNumberId;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getDefaultTo() {
        return defaultTo;
    }

    public void setDefaultTo(String defaultTo) {
        this.defaultTo = defaultTo;
    }

    public boolean isRestrictToDefault() {
        return restrictToDefault;
    }

    public void setRestrictToDefault(boolean restrictToDefault) {
        this.restrictToDefault = restrictToDefault;
    }

    public String getDefaultLanguageCode() {
        return defaultLanguageCode;
    }

    public void setDefaultLanguageCode(String defaultLanguageCode) {
        this.defaultLanguageCode = defaultLanguageCode;
    }

    public String getWebhookVerifyToken() {
        return webhookVerifyToken;
    }

    public void setWebhookVerifyToken(String webhookVerifyToken) {
        this.webhookVerifyToken = webhookVerifyToken;
    }

    public String getAppSecret() {
        return appSecret;
    }

    public void setAppSecret(String appSecret) {
        this.appSecret = appSecret;
    }

    public boolean isBotEnabled() {
        return botEnabled;
    }

    public void setBotEnabled(boolean botEnabled) {
        this.botEnabled = botEnabled;
    }

    public String getBotWelcomeReply() {
        return botWelcomeReply;
    }

    public void setBotWelcomeReply(String botWelcomeReply) {
        this.botWelcomeReply = botWelcomeReply;
    }

    public String getBotFallbackReply() {
        return botFallbackReply;
    }

    public void setBotFallbackReply(String botFallbackReply) {
        this.botFallbackReply = botFallbackReply;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}

