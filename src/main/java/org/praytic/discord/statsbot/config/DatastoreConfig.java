package org.praytic.discord.statsbot.config;

import com.google.api.client.util.Value;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praytic.discord.statsbot.config.properties.GoogleProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@RequiredArgsConstructor
@Slf4j
@Configuration
@EnableConfigurationProperties(GoogleProperties.class)
public class DatastoreConfig {

    private final GoogleProperties googleProperties;

    @Bean
    public Datastore datastore() throws IOException {
        InputStream googleCreds = ClassLoader.getSystemResourceAsStream(googleProperties.getApplicationCredentials());
        if (googleCreds == null) {
            throw new RuntimeException("Google application credentials file does not exist or the path is incorrect.");
        }
        return DatastoreOptions.newBuilder()
                .setProjectId(googleProperties.getProjectId())
                .setCredentials(ServiceAccountCredentials.fromStream(googleCreds))
                .build()
                .getService();
    }
}
