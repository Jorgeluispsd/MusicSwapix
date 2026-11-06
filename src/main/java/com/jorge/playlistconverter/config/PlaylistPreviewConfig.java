package com.jorge.playlistconverter.config;

import com.jorge.playlistconverter.converter.PlaylistConversionValidator;
import com.jorge.playlistconverter.converter.PlaylistConverter;
import com.jorge.playlistconverter.converter.history.PlaylistDestinationRegistry;
import com.jorge.playlistconverter.converter.preview.InMemoryPlaylistPreviewStore;

import com.jorge.playlistconverter.input.SpotifyPlaylistParser;
import com.jorge.playlistconverter.input.YoutubePlaylistParser;
import com.jorge.playlistconverter.mapper.PlaylistConversionResponseMapper;
import com.jorge.playlistconverter.mapper.PlaylistPreviewResponseMapper;
import com.jorge.playlistconverter.service.PlaylistCancellationService;
import com.jorge.playlistconverter.service.PlaylistConfirmationService;
import com.jorge.playlistconverter.service.PlaylistDestinationService;
import com.jorge.playlistconverter.service.PlaylistPreviewService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

@Configuration
public class PlaylistPreviewConfig {

    @Bean
    public InMemoryPlaylistPreviewStore playlistPreviewStore(
            @Value("${playlist.converter.preview-lifetime:PT10M}") String lifetime){
        return new InMemoryPlaylistPreviewStore(Clock.systemUTC(), Duration.parse(lifetime));
    }

    @Bean
    public PlaylistPreviewService playlistPreviewService(PlaylistConverter spotifyToYoutubeConverter,
                                                         InMemoryPlaylistPreviewStore playlistPreviewStore,
                                                         SpotifyPlaylistParser spotifyPlaylistParser,
                                                         YoutubePlaylistParser youtubePlaylistParser,
                                                         PlaylistConversionValidator validator) {
        return new PlaylistPreviewService(spotifyToYoutubeConverter, playlistPreviewStore,
                spotifyPlaylistParser, youtubePlaylistParser, validator);
    }

    @Bean
    public PlaylistConfirmationService playlistConfirmationService(PlaylistConverter spotifyToYoutubeConverter,
                                                                   InMemoryPlaylistPreviewStore playlistPreviewStore) {
        return new PlaylistConfirmationService(spotifyToYoutubeConverter, playlistPreviewStore);
    }

    @Bean
    public PlaylistPreviewResponseMapper playlistPreviewResponseMapper() {
        return new PlaylistPreviewResponseMapper();
    }

    @Bean
    public PlaylistConversionResponseMapper playlistConversionResponseMapper() {
        return new PlaylistConversionResponseMapper();
    }

    @Bean
    public PlaylistCancellationService playlistCancellationService(InMemoryPlaylistPreviewStore previewStore){
        return new PlaylistCancellationService(previewStore);
    }

    @Bean
    public PlaylistDestinationService playlistDestinationService(
            SpotifyPlaylistParser spotifyPlaylistParser,
            PlaylistDestinationRegistry spotifyToYoutubeDestinationRegistry){
        return new PlaylistDestinationService(spotifyPlaylistParser, spotifyToYoutubeDestinationRegistry);
    }
}
