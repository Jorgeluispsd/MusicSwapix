package com.jorge.playlistconverter.service;

import com.jorge.playlistconverter.enums.PlaylistVisibility;
import com.jorge.playlistconverter.model.Song;
import java.util.function.Consumer;

import java.util.List;

public interface MusicService {

    // Leitura paginada já implementada em Spotify e YouTube.
    List<Song> getPlaylistTracks(String playlistId);

    // Retorna uma lista de candidatos; comparação fica no TrackMatcher. YouTube implementado; Spotify pendente da Fase 7.
    List<Song> searchCandidates(Song sourceSong);

    // Fase 6: criar playlist no destino autorizado e retornar o ID.
    String createPlaylist(String name);


    String createPlaylist(String name, PlaylistVisibility visibility);

    // Fase 6: inserir os IDs selecionados na ordem recebida, respeitando o endpoint de cada plataforma.
    void addTracks(String playlistId, List<String> trackIds);

    void addTracks(String playlistId, List<String> trackIds, Consumer<String> onInserted);

    List<Song> getPlaylistTracksAlreadyAdded(String playlistId);



}
