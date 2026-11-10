package com.jorge.playlistconverter.matcher;

import com.jorge.playlistconverter.model.Song;

public record RankedTrackCandidate(Song song, double confidence,
                                   double rankingScore, boolean channelMatchesArtist) {
}
