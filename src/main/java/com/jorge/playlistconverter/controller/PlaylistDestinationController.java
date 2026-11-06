package com.jorge.playlistconverter.controller;

import com.jorge.playlistconverter.dto.response.PlaylistDestinationResponse;
import com.jorge.playlistconverter.service.PlaylistDestinationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/conversions/destinations")
public class PlaylistDestinationController {

    private final PlaylistDestinationService destinationService;

    public PlaylistDestinationController(PlaylistDestinationService destinationService){
        this.destinationService = Objects.requireNonNull(destinationService);
    }

    @GetMapping
    public List<PlaylistDestinationResponse> findDestinations(@RequestParam(name = "sourcePlaylist",
            required = false) String sourcePlaylist){
        return destinationService.findDestinations(sourcePlaylist).stream()
                .map(job -> new PlaylistDestinationResponse(job.destinationPlaylistId()))
                .toList();
    }
}
