package org.praytic.discord.statsbot.config;

import com.google.api.client.util.Value;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import lombok.RequiredArgsConstructor;
import org.praytic.discord.statsbot.config.properties.GoogleProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;

@RequiredArgsConstructor
@Configuration
@EnableConfigurationProperties(GoogleProperties.class)
public class DatastoreConfig {

    private final GoogleProperties googleProperties;

    @Bean
    public Datastore datastore() throws IOException {
        return DatastoreOptions.newBuilder()
                .setProjectId(googleProperties.getProjectId())
                .setCredentials(ServiceAccountCredentials.fromStream(
                        new FileInputStream(googleProperties.getApplicationCredentials())))
                .build()
                .getService();
    }
}
