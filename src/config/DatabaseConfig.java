package config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Reads the local database URL and credentials without storing them in source code. */
public class DatabaseConfig {
    private final String url;
    private final String username;
    private final String password;

    private DatabaseConfig(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public static DatabaseConfig load(Path propertiesFile) throws IOException {
        if (!Files.isRegularFile(propertiesFile)) {
            throw new IOException("Database settings not found at " + propertiesFile
                    + ". Copy config/db.properties.example to config/db.properties and set your local credentials.");
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(propertiesFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        String url = required(properties, "database.url");
        String username = required(properties, "database.username");
        String password = properties.getProperty("database.password", "");
        if (!url.startsWith("jdbc:mysql://")) {
            throw new IOException("database.url must start with jdbc:mysql://.");
        }
        return new DatabaseConfig(url, username, password);
    }

    private static String required(Properties properties, String key) throws IOException {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) throw new IOException("Missing required setting: " + key);
        return value.trim();
    }

    public String getUrl() { return url; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
}
