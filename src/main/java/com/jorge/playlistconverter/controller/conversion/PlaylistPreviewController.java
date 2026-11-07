package com.jorge.playlistconverter.controller.conversion;

import com.jorge.playlistconverter.converter.preview.StoredPlaylistPreview;
import com.jorge.playlistconverter.dto.request.ExistingPlaylistPreviewRequest;
import com.jorge.playlistconverter.dto.request.NewPlaylistPreviewRequest;
import com.jorge.playlistconverter.dto.response.PlaylistPreviewResponse;
import com.jorge.playlistconverter.mapper.PlaylistPreviewResponseMapper;
import com.jorge.playlistconverter.service.conversion.PlaylistPreviewService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
@RequestMapping("/api/conversions/previews")
public class PlaylistPreviewController {

    private final PlaylistPreviewService previewService;
    private final PlaylistPreviewResponseMapper responseMapper;

    public PlaylistPreviewController(PlaylistPreviewService previewService,
                                     PlaylistPreviewResponseMapper responseMapper) {
        this.previewService = Objects.requireNonNull(previewService);
        this.responseMapper = Objects.requireNonNull(responseMapper);
    }

    @PostMapping("/new")
    public PlaylistPreviewResponse previewNewPlaylist(@Valid @RequestBody NewPlaylistPreviewRequest request){
        StoredPlaylistPreview stored = previewService.prepareNewPlaylist(request.sourcePlaylist(),
                request.destinationName(), request.visibility());

        return responseMapper.toResponse(stored);
    }

    @PostMapping("/existing")
    public PlaylistPreviewResponse previewExistingPlaylist(@Valid @RequestBody ExistingPlaylistPreviewRequest request){
        StoredPlaylistPreview stored = previewService.prepareExistingPlaylist(request.sourcePlaylist(),
                request.destinationPlaylist());

        return responseMapper.toResponse(stored);
    }
}
