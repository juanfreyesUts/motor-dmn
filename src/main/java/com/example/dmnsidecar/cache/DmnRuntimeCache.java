package com.example.dmnsidecar.cache;

import org.kie.api.runtime.KieContainer;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNRuntime;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DmnRuntimeCache {

    public record Entry(KieContainer container, DMNRuntime runtime, DMNModel model) {}

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    public String computeHash(String xml) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(xml.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public Optional<Entry> get(String hash) {
        return Optional.ofNullable(cache.get(hash));
    }

    public void put(String hash, Entry entry) {
        cache.put(hash, entry);
    }

    public int size() {
        return cache.size();
    }
}
