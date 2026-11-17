package com.jorge.playlistconverter.converter;

import com.jorge.playlistconverter.model.Song;

import java.util.regex.Pattern;

public final class YoutubeTrackMetadata {

    private static final Pattern ARTIST_SEPARATOR = Pattern.compile("\\s+[-–—]\\s+");
    private static final Pattern TOPIC_SUFFIX = Pattern.compile("(?i)\\s+-\\s+topic$");
    private static final Pattern PRESENTATION_LABEL = Pattern.compile(
            "(?i)[\\[(]\\s*(?:official\\s+)?"
                    + "(?:audio|music video|video|lyric video|lyrics|visualizer)"
                    + "\\s*[\\])]");

    private YoutubeTrackMetadata(){
    }

    public static Song forSpotify(Song video){
        String title = PRESENTATION_LABEL.matcher(video.title()).replaceAll(" ")
                .replaceAll("\\s+", " ").strip();

        var separator = ARTIST_SEPARATOR.matcher(title);
        String artist = "";

        if (separator.find()){
            artist = title.substring(0, separator.start()).strip();
            title = title.substring(separator.end()).strip();

        }else {
            var topic = TOPIC_SUFFIX.matcher(video.artist() == null ? "" : video.artist());

            if (topic.find()){
                artist =  topic.replaceFirst("").strip();
            }
        }

        return new Song(video.id(), title, artist, video.durationMs(), video.artworkUrl());
    }

}
