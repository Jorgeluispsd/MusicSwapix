package com.jorge.playlistconverter.errors.conversion;

public class NoTracksToInsertException extends IllegalArgumentException {
    public NoTracksToInsertException(String message) {
        super(message);
    }
}
