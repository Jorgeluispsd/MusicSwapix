package com.jorge.playlistconverter.errors.spotify;

public class SpotifyTrackInsertionException extends RuntimeException {
    public SpotifyTrackInsertionException(String message, Throwable cause) {
        super(message, cause);
    }
}
