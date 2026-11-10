package com.jorge.playlistconverter.dto.response;

import java.util.List;

public record MatchedTrackResponse(String sourceTrackId, String sourceTitle, String sourceArtist,
                                   String destinationTrackId, String destinationTitle,
                                   double confidence, boolean alreadyPresent,
                                   List<TrackReviewOptionResponse> reviewOptions, int trackPosition, String artworkUrl) {

    public MatchedTrackResponse{
        reviewOptions = List.copyOf(reviewOptions);
    }
}
