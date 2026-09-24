package com.airlineprep.bot.user;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PhoneIdentity {
    private final byte[] key;
    public PhoneIdentity(@Value("${registration.phone-hmac-key:}") String configuredKey,
                         @Value("${telegram.bot.enabled:false}") boolean enabled) {
        byte[] decoded = new byte[0];
        try {
            if (!configuredKey.isBlank()) decoded = Base64.getDecoder().decode(configuredKey);
        } catch (IllegalArgumentException ignored) {
            throw new IllegalStateException("PHONE_IDENTITY_HMAC_KEY must be Base64 with at least 32 random bytes");
        }
        if ((enabled || !configuredKey.isBlank()) && decoded.length < 32) {
            throw new IllegalStateException("PHONE_IDENTITY_HMAC_KEY must be Base64 with at least 32 random bytes");
        }
        key = decoded;
    }
    public boolean configured() { return key.length >= 32; }
    public String hash(String canonicalPhone) { return digest("phone:" + canonicalPhone); }
    public String fingerprint() { return digest("airline-exam-phone-key-v1"); }
    private String digest(String text) {
        if (!configured()) throw new IllegalStateException("Phone identity is not configured");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Phone identity unavailable");
        }
    }
}
