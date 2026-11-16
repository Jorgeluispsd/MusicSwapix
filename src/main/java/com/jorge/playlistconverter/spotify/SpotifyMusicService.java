package com.jorge.playlistconverter.spotify;


import com.google.gson.JsonArray;
import com.jorge.playlistconverter.enums.PlaylistVisibility;
import com.jorge.playlistconverter.errors.OperationInterruptedException;
import com.jorge.playlistconverter.errors.spotify.*;
import com.jorge.playlistconverter.model.Song;
import com.jorge.playlistconverter.service.MusicService;
import com.jorge.playlistconverter.spotify.authorization.SpotifyAuthService;
import org.apache.hc.core5.http.ParseException;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.exceptions.detailed.TooManyRequestsException;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.specification.Playlist;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.exceptions.detailed.ForbiddenException;
import se.michaelthelin.spotify.exceptions.detailed.UnauthorizedException;
import se.michaelthelin.spotify.model_objects.specification.Paging;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class SpotifyMusicService implements MusicService {

    private static final String READ_SCOPES = "playlist-read-private playlist-read-collaborative";
    private static final String WRITE_SCOPES = READ_SCOPES + " playlist-modify-private playlist-modify-public";
    private static final int MAX_TRACKS_PER_INSERTION = 100;

    private final SpotifyAuthService spotifyAuthService;

    public SpotifyMusicService(SpotifyAuthService spotifyAuthService) {
        this.spotifyAuthService = spotifyAuthService;
    }

    @Override
    public List<Song> getPlaylistTracks(String playlistId) {
        try {
            spotifyAuthService.ensureAuthenticated(READ_SCOPES);

        }catch (InterruptedException e){
            Thread.currentThread().interrupt();

            throw new OperationInterruptedException(
                    "Leitura da playlist interrompida durante a autenticação Spotify.", e);

        }catch (NoSuchAlgorithmException e){
            throw new SpotifyAuthenticationException(
                    "Não foi possível gerar o desafio PKCE para autenticar no Spotify.", e);
        }

        return getPlaylistTracksWithApi(playlistId, spotifyAuthService.getSpotifyApi());
    }

    private Paging<PlaylistTrack> requestPlaylistPage(SpotifyApi api, String playlistId,
                                                      int limit, int offset)
            throws IOException, SpotifyWebApiException, ParseException {

        return api.getPlaylistItems(playlistId).
                limit(limit)
                .offset(offset)
                .build()
                .execute();
    }

    private Paging<PlaylistTrack> readPlaylistPage(SpotifyApi api, String playlistId,
                                                   int limit, int offset)
            throws IOException, SpotifyWebApiException, ParseException,
            NoSuchAlgorithmException, InterruptedException{

        spotifyAuthService.ensureAuthenticated(READ_SCOPES);

        try {
            return requestPlaylistPage(api, playlistId, limit, offset);

        }catch (UnauthorizedException e){
            spotifyAuthService.invalidateAccessToken();
            spotifyAuthService.ensureAuthenticated(READ_SCOPES);
        }

        return requestPlaylistPage(api, playlistId, limit, offset);
    }

    private List<Song> getPlaylistTracksWithApi(String playlistId, SpotifyApi api) {
        // Paginação e mapeamento já implementados; a leitura termina quando a página não contém itens.
        List<Song> songs = new ArrayList<>();
        int limit = 50;
        int offset = 0;

        try{
            while (true){
                var paging = readPlaylistPage(api, playlistId, limit, offset);

                PlaylistTrack[] items = paging.getItems();

                if (items == null || items.length == 0){
                    break;
                }

                for (PlaylistTrack item: items){
                    if (item.getItem() != null){
                        IPlaylistItem playlistItem = item.getItem();

                        if (playlistItem instanceof Track spotifyTrack ){
                            String artist = spotifyTrack.getArtists().length > 0
                                    ? spotifyTrack.getArtists()[0].getName()
                                    : "Unknown";

                            songs.add(new Song(
                                    spotifyTrack.getId(),
                                    spotifyTrack.getName(),
                                    artist,
                                    spotifyTrack.getDurationMs()
                            ));
                        }
                    }
                }

                offset += limit;
            }
        } catch (ForbiddenException e) {
            throw new SpotifyPlaylistReadException(
                    "Spotify negou o acesso às faixas da playlist. "
                            + "Verifique as permissões da conta e a disponibilidade da playlist.", e);

        }catch (InterruptedException e){
            Thread.currentThread().interrupt();

            throw new OperationInterruptedException(
                    "Leitura da playlist Spotify interrompida.", e);

        }catch (NoSuchAlgorithmException e){
            throw new SpotifyAuthenticationException(
                    "Não foi possível gerar o desafio PKCE durante a autenticação Spotify.", e);

        }catch (IOException e){
            throw new SpotifyPlaylistReadException(
                    "Falha de comunicação ao consultar as faixas da playlist Spotify.", e);

        }catch (ParseException e) {
            throw new SpotifyPlaylistReadException(
                    "Não foi possível interpretar a resposta da playlist Spotify.", e);

        }catch (TooManyRequestsException e){
            Integer retryAfterSeconds = e.getRetryAfter() > 0 ? e.getRetryAfter() : null;

            String message = retryAfterSeconds != null
                    ? "Spotify limitou as requisições. "
                        + "A leitura da playlist não foi concluída. "
                        + "Aguarde pelo menos " + retryAfterSeconds
                        + " segundos antes de tentar novamente."
                    : "Spotify limitou as requisições. "
                        + "A leitura da playlist não foi concluída. "
                        + "Não foi informado um prazo de espera válido.";

            throw new SpotifyRequestLimitException(message, retryAfterSeconds, e);

        }catch (SpotifyWebApiException e){
            throw new SpotifyPlaylistReadException(
                    "A API do Spotify retornou um erro ao consultar as faixas da playlist.", e);
        }

        return songs;
    }

    @Override
    public List<Song> searchCandidates(Song sourceSong) {
        String query = (sourceSong.title() + " " + sourceSong.artist()).strip();

        try {
            spotifyAuthService.ensureAuthenticated(READ_SCOPES);
            return requestTrackCandidates(query);

        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
            throw new OperationInterruptedException("Busca no Spotify interrompida.", e);

        }catch (NoSuchAlgorithmException e){
            throw new SpotifyAuthenticationException("Não foi possível gerar o desafio PKCE do Spotify.", e);

        }catch (TooManyRequestsException e){
            Integer retryAfter = e.getRetryAfter() > 0 ? e.getRetryAfter() : null;
            throw new SpotifyRequestLimitException("Spotify limitou as buscas.", retryAfter, e);

        }catch (IOException | SpotifyWebApiException | ParseException e){
            throw new SpotifySearchException("Não foi possível buscar faixas no Spotify.", e);
        }
    }

    private List<Song> requestTrackCandidates(String query) throws IOException, SpotifyWebApiException,
            ParseException, NoSuchAlgorithmException, InterruptedException {
        try {
            return searchTracks(query);

        }catch (UnauthorizedException e){
            spotifyAuthService.invalidateAccessToken();
            spotifyAuthService.ensureAuthenticated(READ_SCOPES);
        }

        return searchTracks(query);
    }

    private List<Song> searchTracks(String query) throws IOException, SpotifyWebApiException, ParseException{
        Track[] tracks = spotifyAuthService.getSpotifyApi()
                .searchTracks(query).limit(5).build().execute().getItems();

        if (tracks == null){
            return List.of();
        }

        List<Song> candidates = new ArrayList<>();

        for (Track track : tracks){
            if (track == null || track.getId() == null || track.getId().isBlank()){
                continue;
            }

            var artists = track.getArtists();
            String artist = artists == null || artists.length == 0 ? "" : artists[0].getName();

            Integer durationMs = track.getDurationMs();

            var album = track.getAlbum();
            var imagens = album == null ? null : album.getImages();

            String artworkUrl = imagens == null || imagens.length == 0 ? null : imagens[0].getUrl();

            candidates.add(new Song(track.getId(), track.getName(), artist,
                    durationMs == null ? 0 : durationMs, artworkUrl));
        }

        return candidates;
    }

    @Override
    public String createPlaylist(String name) {
        return createPlaylist(name, PlaylistVisibility.PRIVATE);
    }

    @Override
    public String createPlaylist(String name, PlaylistVisibility visibility) {
        if (name == null || name.isBlank()){
            throw new IllegalArgumentException("Informe o nome da playlist.");
        }

        boolean publicPlaylist = toSpotifyPublicFlag(visibility);

        try {
            spotifyAuthService.ensureAuthenticated(WRITE_SCOPES);

            Playlist playlist = spotifyAuthService.getSpotifyApi().createPlaylist(name.strip())
                    .public_(publicPlaylist).build().execute();

            if (playlist == null || playlist.getId() == null || playlist.getId().isBlank()){
                throw new SpotifyPlaylistCreationException("O Spotify não retornou o ID da playlist criada.", null);
            }

            return playlist.getId();

        }catch (InterruptedException e){
            Thread.currentThread().interrupt();
            throw new OperationInterruptedException("Criação da playlist Spotify interrompida.", e);

        }catch (NoSuchAlgorithmException e){
            throw new SpotifyAuthenticationException("Não foi possível gerar o desafio PKCE do Spotify.", e);

        }catch (TooManyRequestsException e){
            Integer retryAfter = e.getRetryAfter() > 0 ? e.getRetryAfter() : null;
            throw new SpotifyRequestLimitException(
                    "Spotify limitou as requisições durante a criação da playlist.", retryAfter, e);

        }catch (IOException | SpotifyWebApiException | ParseException e){
            throw new SpotifyPlaylistCreationException(
                    "Não foi possível criar a playlist no Spotify.", e);
        }
    }

    private boolean toSpotifyPublicFlag(PlaylistVisibility visibility){
        if (visibility == null){
            throw new IllegalArgumentException("Informe a visibilidade da playlist.");
        }

        return switch (visibility){
            case PRIVATE -> false;
            case PUBLIC -> true;
            case UNLISTED -> throw new IllegalArgumentException("O Spotify aceita playlists privadas ou públicas.");
        };
    }

    @Override
    public void addTracks(String playlistId, List<String> trackIds) {
        addTracks(playlistId, trackIds, trackId -> {});
    }

    @Override
    public void addTracks(String playlistId, List<String> trackIds, Consumer<String> onInserted) {
        if (playlistId == null || playlistId.isBlank()){
            throw new IllegalArgumentException("Informe o ID da playlist de destino.");
        }

        Objects.requireNonNull(trackIds, "A lista de faixas deve ser informada.");
        Objects.requireNonNull(onInserted, "A ação após a inserção deve ser informada.");

        if (trackIds.stream().anyMatch(id -> id == null || id.isBlank())){
            throw new IllegalArgumentException("Todos os IDs das faixas devem ser informados.");
        }

        if (trackIds.isEmpty()){
            return;
        }

        try {
            spotifyAuthService.ensureAuthenticated(WRITE_SCOPES);

            for (int start = 0; start < trackIds.size(); start += MAX_TRACKS_PER_INSERTION){
                int end = Math.min(start + MAX_TRACKS_PER_INSERTION, trackIds.size());
                List<String> batch = trackIds.subList(start, end);

                insertTrackBath(playlistId, batch);
                batch.forEach(onInserted);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OperationInterruptedException(
                    "Inserção de faixas no Spotify interrompida.", e);
        } catch (NoSuchAlgorithmException e) {
            throw new SpotifyAuthenticationException(
                    "Não foi possível gerar o desafio PKCE do Spotify.", e);
        } catch (TooManyRequestsException e) {
            Integer retryAfter = e.getRetryAfter() > 0 ? e.getRetryAfter() : null;
            throw new SpotifyRequestLimitException(
                    "Spotify limitou as requisições durante a inserção de faixas.",
                    retryAfter, e);
        } catch (IOException | SpotifyWebApiException | ParseException e) {
            throw new SpotifyTrackInsertionException(
                    "Não foi possível concluir a inserção de faixas no Spotify.", e);
        }
    }

    private void insertTrackBath(String playlistId, List<String> trackIds) throws IOException,
            SpotifyWebApiException, ParseException{
        JsonArray uris = new JsonArray();
        trackIds.forEach(id -> uris.add("spotify:track:" + id));

        var snapshot = spotifyAuthService.getSpotifyApi().addItemsToPlaylist(playlistId, uris)
                .build().execute();

        if (snapshot == null || snapshot.getSnapshotId() == null || snapshot.getSnapshotId().isBlank()){
            throw new SpotifyTrackInsertionException(
                    "O Spotify não retornou a confirmação do lote de faixas.", null);
        }
    }

    @Override
    public List<Song> getPlaylistTracksAlreadyAdded(String playlistId) {
        return getPlaylistTracks(playlistId);
    }
}
