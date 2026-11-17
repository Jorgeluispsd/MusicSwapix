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
import org.springframework.beans.factory.annotation.Qualifier;
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
    public PlaylistConverter spotifyToYoutubeConverter(@Qualifier("spotifyToYoutubeAnalyzer")
                                                           PlaylistAnalyzer spotifyToYoutubeAnalyzer,
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

    @Bean
    public PlaylistAnalyzer youtubeToSpotifyAnalyzer(YoutubeMusicService youtubeMusicService,
                                                     SpotifyMusicService spotifyMusicService,
                                                     TrackMatcher trackMatcher){
        return new PlaylistAnalyzer(youtubeMusicService, spotifyMusicService, trackMatcher,
                YoutubeTrackMetadata::forSpotify);
    }

    @Bean
    public PlaylistConverter youtubeToSpotifyConverter(
            @Qualifier("youtubeToSpotifyAnalyzer") PlaylistAnalyzer analyzer,
            SpotifyMusicService spotifyMusicService,
            PlaylistConversionValidator validator,
            SyncStateStore store,
            SyncRunTracker runTracker,
            TrackSyncRecorder trackSyncRecorder){
        var registry = new PlaylistDestinationRegistry(store, ConversionDirection.YOUTUBE_TO_SPOTIFY, validator);

        var newPlaylistExecutor = new NewPlaylistExecutor(spotifyMusicService, validator,
                registry, runTracker, trackSyncRecorder);

        var existingPlaylistExecutor = new ExistingPlaylistExecutor(spotifyMusicService, validator,
                registry, runTracker, trackSyncRecorder);

        return new PlaylistConverter(analyzer, spotifyMusicService,
                newPlaylistExecutor, existingPlaylistExecutor, validator);
    }
}
