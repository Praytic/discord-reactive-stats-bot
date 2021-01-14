package org.praytic.discord.statsbot.config;

import com.mewna.catnip.Catnip;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praytic.discord.statsbot.config.properties.BotProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@RequiredArgsConstructor
@Configuration
@EnableConfigurationProperties(BotProperties.class)
public class DiscordConfig {

    private final BotProperties botProperties;

    @Bean
    public Catnip catnip() {
        Catnip catnip = Catnip.catnip(botProperties.getToken());
        return catnip.connect();
    }
}
