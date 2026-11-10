package com.jorge.playlistconverter.dto.response;

public record TrackReviewOptionResponse(String destinationTrackId, String destinationTitle, String channelTitle,
                                        int durationMs, double confidence, boolean alreadyPresent, String artworkUrl) {
}
