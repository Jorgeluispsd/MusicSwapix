package com.jorge.playlistconverter.converter.preview;

import com.jorge.playlistconverter.model.MatchResult;
import com.jorge.playlistconverter.model.Song;

import java.util.List;
import java.util.Set;

public record PlaylistPreview(String sourcePlaylistId, String destinationPlaylistId,
                              List<MatchResult> matches, Set<String> existingDestinationIds
) {
    public PlaylistPreview{
        matches = List.copyOf(matches);
        existingDestinationIds = Set.copyOf(existingDestinationIds);
    }

    public PlaylistPreview(String sourcePlaylistId, List<MatchResult> matches){
        this(sourcePlaylistId, null, matches, Set.of());
    }

    public int totalCount(){
        return matches.size();
    }

    public long foundCount(){
        return matches.stream()
                .filter(MatchResult::found)
                .count();
    }

    public List<Song> notFoundSongs() {
        return matches.stream()
                .filter(match -> !match.found() && !match.skippedByUser())
                .map(MatchResult::sourceSong)
                .toList();
    }

    public long alreadyPresentCount(){
        return matches.stream()
                .filter(MatchResult::found)
                .map(MatchResult::matchedId)
                .filter(existingDestinationIds::contains)
                .count();
    }

    public List<String> newTrackIds(){
        return matches.stream()
                .filter(MatchResult::found)
                .map(MatchResult::matchedId)
                .filter(id -> !existingDestinationIds.contains(id))
                .distinct()
                .toList();
    }

    public int newTrackCount(){
        return newTrackIds().size();
    }

    public long repeatedCandidateCount(){
        return foundCount() - alreadyPresentCount() - newTrackCount();
    }
}
