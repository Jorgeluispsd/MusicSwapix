package com.jorge.playlistconverter.config.conversion;

import com.jorge.playlistconverter.input.SpotifyPlaylistParser;
import com.jorge.playlistconverter.input.YoutubePlaylistParser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlaylistInputConfig {

    @Bean
    public SpotifyPlaylistParser spotifyPlaylistParser() {
        return new SpotifyPlaylistParser();
    }

    @Bean
    public YoutubePlaylistParser youtubePlaylistParser() {
        return new YoutubePlaylistParser();
    }
}
