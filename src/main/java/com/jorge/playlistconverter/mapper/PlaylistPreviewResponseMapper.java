package com.jorge.playlistconverter.mapper;

import com.jorge.playlistconverter.converter.preview.PlaylistPreview;
import com.jorge.playlistconverter.converter.preview.StoredPlaylistPreview;
import com.jorge.playlistconverter.dto.response.MatchedTrackResponse;
import com.jorge.playlistconverter.dto.response.PlaylistPreviewResponse;
import com.jorge.playlistconverter.dto.response.TrackReviewOptionResponse;
import com.jorge.playlistconverter.dto.response.UnmatchedTrackResponse;
import com.jorge.playlistconverter.model.MatchResult;
import com.jorge.playlistconverter.model.TrackReviewOption;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

public class PlaylistPreviewResponseMapper {

    public PlaylistPreviewResponse toResponse(StoredPlaylistPreview stored){
        Objects.requireNonNull(stored, "A prévia armazenada deve ser informada.");

        PlaylistPreview preview = stored.preview();

        List<MatchResult> sourceMatches = preview.matches();

        List<MatchedTrackResponse> matches = IntStream.range(0, sourceMatches.size())
                .filter(position -> sourceMatches.get(position).found())
                .mapToObj(position -> toMatchedTrackResponse(sourceMatches.get(position),
                        preview.existingDestinationIds(), position)).toList();

        List<UnmatchedTrackResponse> notFound = preview.notFoundSongs().stream()
                .map(song -> new UnmatchedTrackResponse(song.id(), song.title(), song.artist())).toList();

        return new PlaylistPreviewResponse(stored.id(), preview.sourcePlaylistId(), preview.destinationPlaylistId(),
                stored.destinationName(), stored.visibility(), stored.expiresAt(), preview.totalCount(),
                preview.foundCount(), preview.alreadyPresentCount(), preview.newTrackCount(),
                preview.repeatedCandidateCount(), notFound.size(), matches, notFound);
    }

    private MatchedTrackResponse toMatchedTrackResponse(MatchResult match, Set<String> existingDestinationIds,
                                                        int trackPosition){
        List<TrackReviewOptionResponse> reviewOptions = match.reviewOptions().stream()
                .map(option -> toReviewOptionResponse(option, existingDestinationIds)).toList();

        return new MatchedTrackResponse(match.sourceSong().id(), match.sourceSong().title(),
                match.sourceSong().artist(), match.matchedId(), match.matchedTitle(), match.confidence(),
                existingDestinationIds.contains(match.matchedId()), reviewOptions, trackPosition, match.artworkUrl());
    }

    private TrackReviewOptionResponse toReviewOptionResponse(TrackReviewOption option,
                                                             Set<String> existingDestinationIds){
        var song = option.song();

        return new TrackReviewOptionResponse(song.id(), song.title(), song.artist(),
                song.durationMs(), option.confidence(), existingDestinationIds.contains(song.id()), song.artworkUrl());
    }
}
