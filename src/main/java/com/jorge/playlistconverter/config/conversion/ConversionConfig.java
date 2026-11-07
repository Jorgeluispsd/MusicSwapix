package com.jorge.playlistconverter.config.conversion;

import com.jorge.playlistconverter.converter.*;
import com.jorge.playlistconverter.converter.execution.ExistingPlaylistExecutor;
import com.jorge.playlistconverter.converter.execution.NewPlaylistExecutor;
import com.jorge.playlistconverter.converter.history.PlaylistDestinationRegistry;
import com.jorge.playlistconverter.converter.history.SyncRunTracker;
import com.jorge.playlistconverter.converter.history.TrackSyncRecorder;
import com.jorge.playlistconverter.enums.ConversionDirection;
import com.jorge.playlistconverter.matcher.TrackMatcher;
import com.jorge.playlistconverter.spotify.SpotifyMusicService;
import com.jorge.playlistconverter.state.SyncStateStore;
import com.jorge.playlistconverter.youtube.YoutubeMusicService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConversionConfig {

    @Bean
    public TrackMatcher trackMatcher() {
        return new TrackMatcher();
    }

    @Bean
    public PlaylistAnalyzer spotifyToYoutubeAnalyzer(SpotifyMusicService spotifyMusicService,
                                                     YoutubeMusicService youtubeMusicService,
                                                     TrackMatcher trackMatcher){
        return new PlaylistAnalyzer(spotifyMusicService, youtubeMusicService, trackMatcher);
    }

    @Bean
    public PlaylistConverter spotifyToYoutubeConverter(PlaylistAnalyzer spotifyToYoutubeAnalyzer,
                                                       YoutubeMusicService youtubeMusicService,
                                                       NewPlaylistExecutor youtubeNewPlaylistExecutor,
                                                       ExistingPlaylistExecutor youtubeExistingPlaylistExecutor,
                                                       PlaylistConversionValidator playlistConversionValidator) {
        return new PlaylistConverter(spotifyToYoutubeAnalyzer, youtubeMusicService,
                youtubeNewPlaylistExecutor, youtubeExistingPlaylistExecutor, playlistConversionValidator);
    }

    @Bean
    public PlaylistConversionValidator playlistConversionValidator() {
        return new PlaylistConversionValidator();
    }

    @Bean
    public NewPlaylistExecutor youtubeNewPlaylistExecutor(
            YoutubeMusicService youtubeMusicService, PlaylistConversionValidator playlistConversionValidator,
            PlaylistDestinationRegistry spotifyToYoutubeDestinationRegistry, SyncRunTracker syncRunTracker,
            TrackSyncRecorder trackSyncRecorder){
        return new NewPlaylistExecutor(youtubeMusicService, playlistConversionValidator,
                spotifyToYoutubeDestinationRegistry, syncRunTracker,trackSyncRecorder);
    }

    @Bean
    public ExistingPlaylistExecutor youtubeExistingPlaylistExecutor(
            YoutubeMusicService youtubeMusicService, PlaylistConversionValidator playlistConversionValidator,
            PlaylistDestinationRegistry spotifyToYoutubeDestinationRegistry, SyncRunTracker runTracker,
            TrackSyncRecorder trackSyncRecorder){
        return new ExistingPlaylistExecutor(youtubeMusicService, playlistConversionValidator,
                spotifyToYoutubeDestinationRegistry, runTracker, trackSyncRecorder);
    }

    @Bean
    public PlaylistDestinationRegistry spotifyToYoutubeDestinationRegistry(SyncStateStore store,
                                                                           PlaylistConversionValidator playlistConversionValidator){
        return new PlaylistDestinationRegistry(store, ConversionDirection.SPOTIFY_TO_YOUTUBE,
                playlistConversionValidator);
    }

    @Bean
    public SyncRunTracker syncRunTracker(SyncStateStore store){
        return new SyncRunTracker(store);
    }

    @Bean
    public TrackSyncRecorder trackSyncRecorder(SyncStateStore store) {
        return new TrackSyncRecorder(store);
    }
}
