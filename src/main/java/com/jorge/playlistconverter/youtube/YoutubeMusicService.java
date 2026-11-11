package com.jorge.playlistconverter.youtube;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import com.jorge.playlistconverter.enums.PlaylistVisibility;
import com.jorge.playlistconverter.errors.youtube.*;
import com.jorge.playlistconverter.model.Song;
import com.jorge.playlistconverter.service.MusicService;
import com.jorge.playlistconverter.youtube.authorization.YoutubeAuthenticatedClientFactory;
import lombok.extern.slf4j.Slf4j;

import static org.apache.commons.text.StringEscapeUtils.unescapeHtml4;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class YoutubeMusicService implements MusicService {

    private final YouTube youtube;
    private final String apiKey;
    private final YoutubeAuthenticatedClientFactory authenticatedClientFactory;

    public YoutubeMusicService(YouTube youtube, String apiKey,
                               YoutubeAuthenticatedClientFactory authenticatedClientFactory) {
        this.youtube = youtube;
        this.apiKey = apiKey;
        this.authenticatedClientFactory = authenticatedClientFactory;
    }

    @Override
    public List<Song> getPlaylistTracks(String playlistId) {
        return readPlaylistTracks(youtube, playlistId, apiKey);
    }

    private List<Song> readPlaylistTracks(YouTube client, String playlistId, String requestApiKey){
        //Leitura implementada com playlistItems.list e nextPageToken, em páginas de até 50 itens.
        try {
            List<Song> songs = new ArrayList<>();
            String pageToken = null;

            do {
                YouTube.PlaylistItems.List request = client.playlistItems()
                        .list(List.of("snippet"))
                        .setPlaylistId(playlistId)
                        .setMaxResults(50L)
                        .setKey(requestApiKey);

                if (pageToken != null) {
                    request.setPageToken(pageToken);
                }

                PlaylistItemListResponse response = request.execute();

                for (PlaylistItem item : response.getItems()) {
                    songs.add(new Song(
                            item.getSnippet().getResourceId().getVideoId(),
                            unescapeHtml4(item.getSnippet().getTitle()),
                            unescapeHtml4(item.getSnippet().getVideoOwnerChannelTitle()),
                            0,
                            readArtworkUrl(item.getSnippet().getThumbnails())
                    ));
                }

                pageToken = response.getNextPageToken();

            } while (pageToken != null);

            return songs;

        }catch (GoogleJsonResponseException e){
            if (isQuotaExceeded(e)){
                throw new YoutubeQuotaExceededException("A quota da API do YouTube foi esgotada. "
                        + "A leitura da playlist não foi concluída. "
                        + "Consulte a quota do projeto antes de tentar novamente.", e);
            }

            throw new YoutubePlaylistReadException("A API do YouTube recusou a leitura da playlist. "
                    + "Código HTTP: " + e.getStatusCode() + ".", e);

        }catch (IOException e){
            throw new YoutubePlaylistReadException(
                    "Falha de comunicação ao consultar a playlist do YouTube.", e);
        }
    }

    @Override
    public List<Song> searchCandidates(Song sourceSong){
        String query = sourceSong.artist() + " " + sourceSong.title();

        List<Song> candidates = searchByQuery(query);

        if (!candidates.isEmpty()){
            return withVideoDurations(candidates);
        }

        String simplifiedTitle = simplifySearchTitle(sourceSong.title());
        String simplifiedQuery = sourceSong.artist() + " " + simplifiedTitle;

        if (simplifiedTitle.isBlank() || simplifiedQuery.equals(query)){
            return candidates;
        }

        return withVideoDurations(searchByQuery(simplifiedQuery));
    }

    private List<Song> searchByQuery(String query){
        try {

            YouTube.Search.List request = youtube.search()
                    .list(List.of("snippet"))
                    .setQ(query)
                    .setType(List.of("video"))
                    .setMaxResults(5L)
                    .setKey(apiKey);

            SearchListResponse response = request.execute();

            return response.getItems().stream()
                    .map(item -> new Song(
                            item.getId().getVideoId(),
                            unescapeHtml4(item.getSnippet().getTitle()),
                            unescapeHtml4(item.getSnippet().getChannelTitle()),
                            0,
                            readArtworkUrl(item.getSnippet().getThumbnails())
                    ))
                    .toList();

        }catch (GoogleJsonResponseException e){
            log.debug("Busca do YouTube recusada. HTTP: {}", e.getStatusCode());

            if (isQuotaExceeded(e)){
                throw new YoutubeQuotaExceededException("A quota da API do YouTube foi esgotada. "
                        + "A busca de candidatos não foi concluída. "
                        + "Consulte a quota do projeto antes de tentar novamente.", e);
            }

            if (e.getStatusCode() == 429) {
                throw new YoutubeSearchRateLimitException(e);
            }

            throw new YoutubeSearchException(
                    "A API do YouTube recusou a busca de candidatos. "
                            + "Código HTTP: " + e.getStatusCode() + ".", e);

        } catch (IOException e) {
            log.debug("Falha de comunicação na busca do YouTube. Tipo: {}", e.getClass().getSimpleName());
            throw new YoutubeSearchException("Falha de comunicação ao buscar candidatos no YouTube.", e);
        }
        // Os candidatos retornados são comparados pelo TrackMatcher fora deste serviço.
    }

    private boolean isQuotaExceeded(GoogleJsonResponseException exception){
        GoogleJsonError details = exception.getDetails();

        if (details == null || details.getErrors() == null){
            return false;
        }

        return details.getErrors().stream()
                .anyMatch(error -> error != null && "quotaExceeded".equals(error.getReason()));
    }

    @Override
    public String createPlaylist(String name){
        return createPlaylist(name, PlaylistVisibility.PRIVATE);
    }

    public String createPlaylist(String name, PlaylistVisibility visibility) {
        if (name == null || name.isBlank()){
            throw new IllegalArgumentException("O nome da playlist deve ser informado.");
        }

        Objects.requireNonNull(visibility,
                "A visibilidade da playlist deve ser informada.");

        YouTube authenticatedYoutube = authenticatedClientFactory.create();

        Playlist playlist = new Playlist()
                .setSnippet(new PlaylistSnippet().setTitle(name.strip()))
                .setStatus(new PlaylistStatus().setPrivacyStatus(visibility.getApiValue()));

        try {
            Playlist created = authenticatedYoutube.playlists()
                    .insert(List.of("snippet", "status"), playlist)
                    .execute();

            if (created == null
                    || created.getId() == null
                    || created.getId().isBlank()){
                throw new YoutubePlaylistCreationException("O YouTube não retornou um ID válido "
                        + "após a solicitação de criação da playlist.");
            }

            return created.getId();

        }catch (GoogleJsonResponseException e){
            if (isQuotaExceeded(e)){
                throw new YoutubeQuotaExceededException(
                        "A quota da API do YouTube foi esgotada. "
                                + "Não foi possível concluir a criação da playlist.", e);
            }

            throw new YoutubePlaylistCreationException(
                    "A API do YouTube recusou a criação da playlist. "
                            + "Código HTTP: " + e.getStatusCode() + ".", e);

        }catch (IOException e){
            throw new YoutubePlaylistCreationException(
                    "Falha de comunicação durante a criação da playlist. "
                            + "Verifique o destino antes de tentar novamente.", e);
        }
    }

    @Override
    public void addTracks(String playlistId, List<String> trackIds) {
        addTracks(playlistId, trackIds, ignored -> {});
    }

    @Override
    public void addTracks(String playlistId, List<String> trackIds, Consumer<String> onInserted) {
        validateTrackInsertion(playlistId, trackIds);

        Objects.requireNonNull(onInserted, "A ação após a inserção deve ser informada.");

        if (trackIds.isEmpty()){
            return;
        }

        YouTube authenticatedYoutube = authenticatedClientFactory.create();

        int confirmedInsertions = 0;

        try {
            for (String trackId : trackIds){
                insertTrack(authenticatedYoutube, playlistId,
                        trackId, confirmedInsertions);

                confirmedInsertions++;
                onInserted.accept(trackId);
            }

        }catch (GoogleJsonResponseException e){
            if (isQuotaExceeded(e)){
                throw new YoutubeQuotaExceededException(
                        "A quota da API do YouTube foi esgotada. "
                                + "Inserções anteriores confirmadas: "
                                + confirmedInsertions
                                + ". A operação foi interrompida.", e);
            }

            throw new YoutubeTrackInsertionException("A API do YouTube recusou a inserção de um vídeo. "
                    + "Código HTTP: " + e.getStatusCode()
                    + ". Inserções anteriores confirmadas: "
                    + confirmedInsertions + ".", e);

        }catch (IOException e){
            throw new YoutubeTrackInsertionException(
                    "Falha de comunicação durante a inserção de vídeos. "
                            + "Inserções anteriores confirmadas: "
                            + confirmedInsertions
                            + ". Verifique o destino antes de tentar novamente.", e);

        }
    }

    private void validateTrackInsertion(String playlistId, List<String> trackIds){
        if (playlistId == null || playlistId.isBlank()){
            throw new IllegalArgumentException(
                    "O ID da playlist deve ser informado.");
        }

        if (trackIds == null){
            throw new IllegalArgumentException(
                    "A lista de vídeos deve ser informada.");
        }

        boolean hasInvalidId = trackIds.stream()
                .anyMatch(id -> id == null || id.isBlank());

        if (hasInvalidId){
            throw new IllegalArgumentException(
                    "A lista contém um ID de vídeo inválido.");
        }

    }

    private void insertTrack(YouTube authenticatedYoutube, String playlistId,
                             String trackId, int confirmedInsertions) throws IOException{

        PlaylistItem item = new PlaylistItem()
                .setSnippet(new PlaylistItemSnippet()
                        .setPlaylistId(playlistId)
                        .setResourceId(new ResourceId()
                                .setKind("youtube#video")
                                .setVideoId(trackId)));

        PlaylistItem inserted = authenticatedYoutube.playlistItems()
                .insert(List.of("snippet"), item).execute();


        if (inserted == null
                || inserted.getId() == null
                || inserted.getId().isBlank()){
            throw new YoutubeTrackInsertionException(
                    "O YouTube não retornou um ID válido "
                            + "após a solicitação de inserção. "
                            + "Inserções anteriores confirmadas: "
                            + confirmedInsertions
                            + ". Verifique o destino antes de tentar novamente.");
        }
    }

    @Override
    public List<Song> getPlaylistTracksAlreadyAdded(String playlistId) {
        if (playlistId == null || playlistId.isBlank()){
            throw new IllegalArgumentException(
                    "O ID da playlist de destino deve ser informado.");
        }

        YouTube authenticatedYoutube = authenticatedClientFactory.create();

        return readPlaylistTracks(authenticatedYoutube, playlistId, null);
    }

    private String simplifySearchTitle(String title) {
        return title
                .replaceAll("\\([^)]*\\)|\\[[^]]*\\]", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private List<Song> withVideoDurations(List<Song> candidates){
        if (candidates.isEmpty()){
            return candidates;
        }

        List<String> videoIds = candidates.stream().map(Song::id).distinct().toList();

        Map<String, Integer> durationsById = new HashMap<>();

        try {
            VideoListResponse response = youtube.videos().list(List.of("contentDetails"))
                    .setId(videoIds).setKey(apiKey).execute();

            if (response.getItems() != null){
                for (Video video : response.getItems()){
                    durationsById.put(video.getId(), readDurationMs(video));
                }
            }

        }catch (IOException e){
            log.debug("Duração dos candidatos indisponível. Tipo: {}", e.getClass().getSimpleName());
            return candidates;
        }

        return candidates.stream().map(song -> new Song(song.id(), song.title(), song.artist(),
                durationsById.getOrDefault(song.id(), 0),song.artworkUrl())).toList();
    }

    private int readDurationMs(Video video){
        VideoContentDetails details = video.getContentDetails();

        if (details == null || details.getDuration() == null){
            return 0;
        }

        try {
            return Math.toIntExact(Duration.parse(details.getDuration()).toMillis());

        }catch (DateTimeParseException | ArithmeticException e){
            return 0;
        }
    }

    private String readArtworkUrl(ThumbnailDetails thumbnails){
        if (thumbnails == null){
            return null;
        }

        Thumbnail image = thumbnails.getMedium();
        if (image == null){
            image = thumbnails.getDefault();
        }

        return image == null ? null : image.getUrl();
    }
}
