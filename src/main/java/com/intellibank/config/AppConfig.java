package com.intellibank.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private static final Properties properties = new Properties();

    static {
        loadConfig();
    }

    private static void loadConfig() {
        // Try loading config.properties from local directory first, then classpath
        File file = new File("config.properties");
        if (file.exists()) {
            try (InputStream is = new FileInputStream(file)) {
                properties.load(is);
                logger.info("Loaded configuration from local file: config.properties");
            } catch (Exception e) {
                logger.error("Failed to load config.properties from file", e);
            }
        } else {
            try (InputStream is = AppConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
                if (is != null) {
                    properties.load(is);
                    logger.info("Loaded configuration from classpath resource config.properties");
                } else {
                    logger.warn("No config.properties found, relying on environment variables or defaults");
                }
            } catch (Exception e) {
                logger.error("Failed to load config.properties from classpath", e);
            }
        }
    }

    public static String get(String key, String defaultValue) {
        // Check environment variable override (e.g., DB_URL for db.url)
        String envKey = key.toUpperCase().replace('.', '_');
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.trim().isEmpty()) {
            return envValue.trim();
        }
        return properties.getProperty(key, defaultValue);
    }

    public static int getInt(String key, int defaultValue) {
        String val = get(key, null);
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid integer config for key {}: {}. Using default {}", key, val, defaultValue);
            }
        }
        return defaultValue;
    }

    public static String getDbUrl() {
        return get("db.url", "jdbc:mysql://localhost:3306/intellibank_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true");
    }

    public static String getDbUser() {
        return get("db.user", "root");
    }

    public static String getDbPassword() {
        return get("db.password", "12345");
    }

    public static int getServerPort() {
        return getInt("server.port", 8080);
    }

    public static String getJwtSecret() {
        return get("jwt.secret", "c3VwZXJfc2VjdXJlX2p3dF9zZWNyZXRfa2V5X2Zvcl9pbnRlbGxpYmFua19iMnJfYXBwbGljYXRpb25fMjAyNl9zZWN1cmU=");
    }

    public static int getJwtExpiryMinutes() {
        return getInt("jwt.expiry.minutes", 120);
    }

    public static int getMaxLoginAttempts() {
        return getInt("login.max.attempts", 5);
    }

    public static int getLockoutMinutes() {
        return getInt("login.lockout.minutes", 15);
    }
}
