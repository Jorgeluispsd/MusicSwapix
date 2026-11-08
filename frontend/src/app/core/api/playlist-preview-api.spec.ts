import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { NewPlaylistPreviewRequest } from '../models/new-playlist-preview-request';
import { PlaylistPreviewResponse } from '../models/playlist-preview-response';
import { PlaylistPreviewApi } from './playlist-preview-api';
import { PlaylistConversionResponse } from '../models/playlist-conversion-response';

describe('PlaylistPreviewApi', () => {
  let api: PlaylistPreviewApi;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    api = TestBed.inject(PlaylistPreviewApi);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('shouldPostNewPlaylistRequestAndReturnPreview', async () => {
    const payload: NewPlaylistPreviewRequest = {
      sourcePlaylist: '1234567890123456789012',
      destinationName: 'Meu Rock',
      visibility: 'PRIVATE',
    };

    const expected: PlaylistPreviewResponse = {
      previewId: '3ebf4e4c-7ebd-4c52-b5b0-d943d4647206',
      sourcePlaylistId: payload.sourcePlaylist,
      destinationPlaylistId: null,
      destinationName: payload.destinationName,
      visibility: 'PRIVATE',
      expiresAt: '2026-11-08T18:10:00Z',
      total: 0,
      found: 0,
      alreadyPresent: 0,
      newTrackCount: 0,
      repeatedCandidateCount: 0,
      notFoundCount: 0,
      matches: [],
      notFound: [],
    };

    const responsePromise = firstValueFrom(api.prepareNewPlaylist(payload));

    const request = httpTesting.expectOne(
      '/api/conversions/previews/new',
    );

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);

    request.flush(expected);

    expect(await responsePromise).toEqual(expected);
  });

  it('shouldPreserveBackendValidationError', async () => {
    const payload: NewPlaylistPreviewRequest = {
      sourcePlaylist: 'origem-invalida',
      destinationName: 'Meu Rock',
      visibility: 'PRIVATE',
    };

    const backendError = {
      status: 400,
      code: 'INVALID_PLAYLIST_INPUT',
      message: 'Informe um link ou ID válido para a plataforma da playlist.',
      path: '/api/conversions/previews/new',
      fieldErrors: [],
    };

    const responsePromise = firstValueFrom(api.prepareNewPlaylist(payload));

    const rejection = expect(responsePromise).rejects.toMatchObject({
      status: 400,
      error: backendError,
    });

    const request = httpTesting.expectOne(
      '/api/conversions/previews/new',
    );

    request.flush(backendError, {
      status: 400,
      statusText: 'Bad Request',
    });

    await rejection;
  });

  it('shouldConfirmPreviewByIdWithoutSendingRequestBody', async () => {
    const previewId = '3ebf4e4c-7ebd-4c52-b5b0-d943d4647206';

    const expected: PlaylistConversionResponse = {
      sourcePlaylistId: 'source-playlist',
      destinationPlaylistId: 'destination-playlist',
      destinationPlaylistUrl:
        'https://www.youtube.com/playlist?list=destination-playlist',
      insertedCount: 2,
      skippedCount: 0,
      notFoundCount: 0,
      notFound: [],
    };

    const responsePromise = firstValueFrom(api.confirmPreview(previewId));

    const request = httpTesting.expectOne(
      `/api/conversions/previews/${previewId}/confirm`,
    );

    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();

    request.flush(expected);

    expect(await responsePromise).toEqual(expected);
  });

  it('shouldCancelPreviewByIdAndAcceptNoContent', async () => {
    const previewId = '3ebf4e4c-7ebd-4c52-b5b0-d943d4647206';

    const responsePromise = firstValueFrom(api.cancelPreview(previewId));

    const request = httpTesting.expectOne(
      `/api/conversions/previews/${previewId}`,
    );

    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toBeNull();

    request.flush(null, {
      status: 204,
      statusText: 'No Content',
    });

    await responsePromise;
  });

  it('shouldPreserveUnavailablePreviewErrorWithoutRetryingConfirmation', async () => {
    const previewId = '3ebf4e4c-7ebd-4c52-b5b0-d943d4647206';

    const backendError = {
      status: 404,
      code: 'PREVIEW_UNAVAILABLE',
      message: 'A prévia não está disponível para confirmação. Gere uma nova prévia.',
      path: `/api/conversions/previews/${previewId}/confirm`,
      fieldErrors: [],
    };

    const responsePromise = firstValueFrom(api.confirmPreview(previewId));

    const rejection = expect(responsePromise).rejects.toMatchObject({
      status: 404,
      error: backendError,
    });

    const request = httpTesting.expectOne(
      `/api/conversions/previews/${previewId}/confirm`,
    );

    request.flush(backendError, {
      status: 404,
      statusText: 'Not Found',
    });

    await rejection;
  });
});
