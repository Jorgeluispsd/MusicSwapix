package com.jorge.playlistconverter.converter;

import com.jorge.playlistconverter.matcher.TrackMatcher;
import com.jorge.playlistconverter.model.MatchResult;
import com.jorge.playlistconverter.model.Song;
import com.jorge.playlistconverter.service.MusicService;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
public class PlaylistAnalyzer {

    private final MusicService source;
    private final MusicService destination;
    private final TrackMatcher matcher;

    public PlaylistAnalyzer(MusicService source, MusicService destination,
                            TrackMatcher matcher) {
        this.source = Objects.requireNonNull(source);
        this.destination = Objects.requireNonNull(destination);
        this.matcher = Objects.requireNonNull(matcher);
    }

    public List<MatchResult> analyze(String sourcePlaylistId) {
        return analyze(sourcePlaylistId, List.of());
    }


    public List<MatchResult> analyze(String sourcePlaylistId, List<Song> existingSongs) {
        if (sourcePlaylistId == null || sourcePlaylistId.isBlank()){
            throw new IllegalArgumentException("O ID da playlist de origem deve ser informado.");
        }

        List<Song> songs = source.getPlaylistTracks(sourcePlaylistId);
        List<MatchResult> results = new ArrayList<>();

        for (Song song : songs){
            results.add(analyzeSong(song, existingSongs));
        }

        return List.copyOf(results);
    }

    private MatchResult analyzeSong(Song song, List<Song> existingSongs){
        if (existingSongs.isEmpty()){
            return searchMatch(song);
        }

        MatchResult existingMatch = matcher.findExistingMatch(song, existingSongs);

        if (existingMatch.found()){
            log.debug("Correspondência já presente no destino: {}",
                    existingMatch);

            return existingMatch;
        }

        return searchMatch(song);
    }

    private MatchResult searchMatch(Song song){
        log.debug("Origem da análise: {}", song);

        List<Song> candidates = destination.searchCandidates(song);

        for (int index = 0; index < candidates.size(); index++){
            log.debug("Candidato {}: {}", index + 1,
                    candidates.get(index));
        }

        MatchResult result = matcher.findBestMatch(song, candidates);

        log.debug("Resultado da análise: {}", result);

        return result;
    }
}
