package com.jorge.playlistconverter.config.youtube;


import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.jorge.playlistconverter.youtube.authorization.GoogleAuthService;
import com.jorge.playlistconverter.youtube.YoutubeMusicService;
import com.jorge.playlistconverter.youtube.authorization.YoutubeAuthenticatedClientFactory;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;


@Configuration
public class YoutubeClientConfig {

    @Bean
    public YouTube youtube()throws IOException{
        return new YouTube.Builder(
                new NetHttpTransport.Builder().build(),
                GsonFactory.getDefaultInstance(),
                null
        ).setApplicationName("playlist-converter").build();
    }

    @Bean
    public YoutubeMusicService youtubeMusicService(YouTube youtube, Dotenv dotenv,
                                                   YoutubeAuthenticatedClientFactory authenticatedClientFactory){
        String apiKey = dotenv.get("YOUTUBE_API_KEY");

        return new YoutubeMusicService(youtube, apiKey, authenticatedClientFactory);
    }

    @Bean
    public YoutubeAuthenticatedClientFactory youtubeAuthenticatedClientFactory(GoogleAuthService authService){
        return new YoutubeAuthenticatedClientFactory(authService);
    }
}
