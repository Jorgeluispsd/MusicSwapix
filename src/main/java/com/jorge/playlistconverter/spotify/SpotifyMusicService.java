package com.jorge.playlistconverter.spotify;


import com.jorge.playlistconverter.enums.PlaylistVisibility;
import com.jorge.playlistconverter.errors.OperationInterruptedException;
import com.jorge.playlistconverter.errors.spotify.SpotifyAuthenticationException;
import com.jorge.playlistconverter.errors.spotify.SpotifyPlaylistReadException;
import com.jorge.playlistconverter.errors.spotify.SpotifyRequestLimitException;
import com.jorge.playlistconverter.model.Song;
import com.jorge.playlistconverter.service.MusicService;
import com.jorge.playlistconverter.spotify.authorization.SpotifyAuthService;
import org.apache.hc.core5.http.ParseException;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.exceptions.detailed.TooManyRequestsException;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.specification.PlaylistTrack;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.exceptions.detailed.ForbiddenException;
import se.michaelthelin.spotify.exceptions.detailed.UnauthorizedException;
import se.michaelthelin.spotify.model_objects.specification.Paging;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SpotifyMusicService implements MusicService {

    private static final String READ_SCOPES = "playlist-read-private playlist-read-collaborative";

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
        // TODO (Fase 7): buscar candidatos Spotify por título/artista, mapear para Song e tratar limites; matching permanece no TrackMatcher.
        throw new UnsupportedOperationException("TODO: Fase 7 (conversão inversa)");
    }

    @Override
    public String createPlaylist(String name) {
        return createPlaylist(name, PlaylistVisibility.PRIVATE);
    }

    @Override
    public String createPlaylist(String name, PlaylistVisibility visibility) {
        // TODO (Fase 6): garantir escopo de escrita, criar playlist na conta autorizada e retornar o ID.
        throw new UnsupportedOperationException("A criação de playlists no Spotify ainda não foi implementada.");
    }

    @Override
    public void addTracks(String playlistId, List<String> trackIds) {
        // TODO (Fase 6): converter IDs para URIs Spotify e inserir em lotes na ordem recebida, sem retry cego de escrita.
        throw new UnsupportedOperationException("TODO: Fase 6");
    }

    @Override
    public void addTracks(String playlistId, List<String> trackIds, Consumer<String> onInserted) {
        throw new UnsupportedOperationException("A inserção de músicas no Spotify ainda não foi implementada.");
    }

    @Override
    public List<Song> getPlaylistTracksAlreadyAdded(String playlistId) {
        // TODO (Fase 6): ler itens já presentes no destino, reaproveitando getPlaylistTracks; coordenar a checagem com o histórico H2.
        throw new UnsupportedOperationException("TODO: Fase 5");
    }
}
