package com.jorge.playlistconverter.errors.spotify;

public class SpotifyPlaylistCreationException extends RuntimeException {

    public SpotifyPlaylistCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
