package com.jorge.playlistconverter.youtube;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ResourceId;
import com.google.api.services.youtube.model.SearchListResponse;
import com.google.api.services.youtube.model.SearchResult;
import com.google.api.services.youtube.model.SearchResultSnippet;
import com.jorge.playlistconverter.errors.youtube.YoutubeQuotaExceededException;
import com.jorge.playlistconverter.errors.youtube.YoutubeSearchException;
import com.jorge.playlistconverter.model.Song;
import com.jorge.playlistconverter.youtube.authorization.YoutubeAuthenticatedClientFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoContentDetails;
import com.google.api.services.youtube.model.VideoListResponse;
import com.google.api.services.youtube.model.Thumbnail;
import com.google.api.services.youtube.model.ThumbnailDetails;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class YoutubeSearchFallbackTest {

    private static final String ORIGINAL_QUERY =
            "Shinedown Diamond Eyes (Boom-Lay Boom-Lay Boom)";

    private static final String SIMPLIFIED_QUERY =
            "Shinedown Diamond Eyes";

    private final Song sourceSong = new Song(
            "source", "Diamond Eyes (Boom-Lay Boom-Lay Boom)",
            "Shinedown", 336112);

    private YouTube youtube;
    private YoutubeMusicService service;
    private YouTube.Videos.List videoDetailsRequest;

    @BeforeEach
    void setUp() throws IOException{
        youtube = mock(YouTube.class);

        YoutubeAuthenticatedClientFactory clientFactory =
                mock(YoutubeAuthenticatedClientFactory.class);

        YouTube.Videos videos = mock(YouTube.Videos.class);
        videoDetailsRequest = mock(YouTube.Videos.List.class, RETURNS_SELF);

        when(youtube.videos()).thenReturn(videos);
        when(videos.list(List.of("contentDetails"))).thenReturn(videoDetailsRequest);
        when(videoDetailsRequest.execute())
                .thenReturn(new VideoListResponse().setItems(List.of()));

        service = new YoutubeMusicService(
                youtube,
                "test-api-key",
                clientFactory);
    }

    @Test
    void shouldSearchSimplifiedTitleAfterEmptyOriginalSearch()
            throws IOException {
        YouTube.Search.List request = searchRequest();

        when(request.execute())
                .thenReturn(emptyResponse(), responseWithCandidate());

        List<Song> result = service.searchCandidates(sourceSong);

        assertEquals(
                List.of(new Song("video-id", "Shinedown - Diamond Eyes",
                        "Shinedown", 0)), result);

        var order = inOrder(request);

        order.verify(request).setQ(ORIGINAL_QUERY);
        order.verify(request).execute();
        order.verify(request).setQ(SIMPLIFIED_QUERY);
        order.verify(request).execute();

        verify(request, times(2)).execute();
    }

    @Test
    void shouldNotSearchAgainWhenOriginalSearchHasCandidates()
            throws IOException {
        YouTube.Search.List request = searchRequest();

        when(request.execute()).thenReturn(responseWithCandidate());

        List<Song> result = service.searchCandidates(sourceSong);

        assertEquals(1, result.size());

        verify(request, times(1)).execute();
        verify(request).setQ(ORIGINAL_QUERY);
        verify(request, never()).setQ(SIMPLIFIED_QUERY);
    }

    @Test
    void shouldNotRepeatUnchangedQuery() throws IOException {
        Song song = new Song("source", "Diamond Eyes",
                "Shinedown", 336112);

        YouTube.Search.List request = searchRequest();

        when(request.execute()).thenReturn(emptyResponse());

        List<Song> result = service.searchCandidates(song);

        assertTrue(result.isEmpty());
        verify(request, times(1)).setQ(SIMPLIFIED_QUERY);
        verify(request, times(1)).execute();
    }

    @Test
    void shouldReturnEmptyAfterBothSearchesHaveNoCandidates()
            throws IOException {
        YouTube.Search.List request = searchRequest();

        when(request.execute()).thenReturn(emptyResponse());

        List<Song> result = service.searchCandidates(sourceSong);

        assertTrue(result.isEmpty());

        verify(request).setQ(ORIGINAL_QUERY);
        verify(request).setQ(SIMPLIFIED_QUERY);
        verify(request, times(2)).execute();
    }

    @Test
    void shouldNotSearchAgainAfterCommunicationFailure()
            throws IOException {
        IOException original = new IOException("Falha simulada");

        YouTube.Search.List request = searchRequest();
        when(request.execute()).thenThrow(original);

        YoutubeSearchException result = assertThrows(
                YoutubeSearchException.class,
                () -> service.searchCandidates(sourceSong));

        assertSame(original, result.getCause());

        verify(request, times(1)).execute();
        verify(request, never()).setQ(SIMPLIFIED_QUERY);
    }

    @Test
    void shouldNotSearchAgainAfterQuotaExceeded() throws IOException {
        GoogleJsonResponseException original = quotaError();

        YouTube.Search.List request = searchRequest();
        when(request.execute()).thenThrow(original);

        YoutubeQuotaExceededException result = assertThrows(
                YoutubeQuotaExceededException.class,
                () -> service.searchCandidates(sourceSong));

        assertSame(original, result.getCause());

        verify(request, times(1)).execute();
        verify(request, never()).setQ(SIMPLIFIED_QUERY);
    }

    @Test
    void shouldPreserveFailureDuringSimplifiedSearch()
            throws IOException {
        IOException original =
                new IOException("Falha na consulta simplificada");

        YouTube.Search.List request = searchRequest();

        when(request.execute())
                .thenReturn(emptyResponse())
                .thenThrow(original);

        YoutubeSearchException result = assertThrows(
                YoutubeSearchException.class,
                () -> service.searchCandidates(sourceSong));

        assertSame(original, result.getCause());

        verify(request).setQ(SIMPLIFIED_QUERY);
        verify(request, times(2)).execute();
    }

    @Test
    void shouldNotSearchUsingEmptySimplifiedTitle() throws IOException {
        Song song = new Song("source", "(Live)",
                "Test Artist", 200000);

        YouTube.Search.List request = searchRequest();

        when(request.execute()).thenReturn(emptyResponse());

        List<Song> result = service.searchCandidates(song);

        assertTrue(result.isEmpty());

        verify(request).setQ("Test Artist (Live)");
        verify(request, times(1)).execute();
    }

    @Test
    void shouldDecodeHtmlEntitiesInSearchResults() throws IOException {
        Song song = new Song(
                "source",
                "Vitória Regia",
                "Franco, The Sir!",
                195582);

        SearchResult candidate = new SearchResult()
                .setId(new ResourceId().setVideoId("WAoCJ1OXGPg"))
                .setSnippet(new SearchResultSnippet()
                        .setTitle(
                                "&quot;VITÓRIA REGIA&quot; ft. Franco The Sir, "
                                        + "M&#39;Dep, Ian Trunks, Yuri Redicopa")
                        .setChannelTitle("IAN TRUNKS"));

        YouTube.Search.List request = searchRequest();

        when(request.execute()).thenReturn(
                new SearchListResponse().setItems(List.of(candidate)));

        List<Song> result = service.searchCandidates(song);

        assertEquals(1, result.size());

        assertEquals(
                "\"VITÓRIA REGIA\" ft. Franco The Sir, "
                        + "M'Dep, Ian Trunks, Yuri Redicopa",
                result.get(0).title());

        assertEquals("IAN TRUNKS", result.get(0).artist());
        verify(request, times(1)).execute();
    }

    @Test
    void shouldLoadCandidateDurationsInOneBatch() throws IOException {
        YouTube.Search.List request = searchRequest();

        SearchResult first = responseWithCandidate().getItems().getFirst();
        SearchResult second = new SearchResult()
                .setId(new ResourceId().setVideoId("second-video"))
                .setSnippet(new SearchResultSnippet()
                        .setTitle("Diamond Eyes")
                        .setChannelTitle("Shinedown - Topic")
                        .setThumbnails(new ThumbnailDetails().setMedium(
                                new Thumbnail().setUrl(
                                        "https://example.com/second-video.jpg"))));

        when(request.execute()).thenReturn(
                new SearchListResponse().setItems(List.of(first, second)));

        when(videoDetailsRequest.execute()).thenReturn(
                new VideoListResponse().setItems(List.of(
                        new Video()
                                .setId("second-video")
                                .setContentDetails(new VideoContentDetails()
                                        .setDuration("PT5M36S")))));

        List<Song> result = service.searchCandidates(sourceSong);

        assertEquals(List.of("video-id", "second-video"),
                result.stream().map(Song::id).toList());
        assertEquals(0, result.get(0).durationMs());
        assertEquals(336_000, result.get(1).durationMs());
        assertEquals("https://example.com/second-video.jpg",
                result.get(1).artworkUrl());

        verify(request, times(1)).execute();
        verify(videoDetailsRequest).setId(List.of("video-id", "second-video"));
        verify(videoDetailsRequest, times(1)).execute();
    }

    private YouTube.Search.List searchRequest() throws IOException {
        YouTube.Search resource = mock(YouTube.Search.class);

        YouTube.Search.List request =
                mock(YouTube.Search.List.class, RETURNS_SELF);

        when(youtube.search()).thenReturn(resource);
        when(resource.list(List.of("snippet"))).thenReturn(request);

        return request;
    }

    private SearchListResponse emptyResponse() {
        return new SearchListResponse().setItems(List.of());
    }

    private SearchListResponse responseWithCandidate() {
        SearchResult candidate = new SearchResult()
                .setId(new ResourceId()
                        .setKind("youtube#video")
                        .setVideoId("video-id"))
                .setSnippet(new SearchResultSnippet()
                        .setTitle("Shinedown - Diamond Eyes")
                        .setChannelTitle("Shinedown"));

        return new SearchListResponse()
                .setItems(List.of(candidate));
    }

    private GoogleJsonResponseException quotaError() {
        GoogleJsonError.ErrorInfo error =
                new GoogleJsonError.ErrorInfo();
        error.setReason("quotaExceeded");

        GoogleJsonError details = new GoogleJsonError();
        details.setCode(403);
        details.setErrors(List.of(error));

        HttpResponseException.Builder response =
                new HttpResponseException.Builder(403,
                        "Forbidden", new HttpHeaders());

        return new GoogleJsonResponseException(response, details);
    }
}
