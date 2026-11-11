import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { PlaylistDestinationApi } from './playlist-destination-api';

describe('PlaylistDestinationApi', () => {
  let api: PlaylistDestinationApi;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    api = TestBed.inject(PlaylistDestinationApi);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('shouldFindSavedDestinationsUsingSourcePlaylistParameter', async () => {
    const source = 'https://open.spotify.com/playlist/1234567890123456789012?si=example';
    const expected = [
      { destinationPlaylistId: 'PL-first' },
      { destinationPlaylistId: 'PL-second' },
    ];

    const responsePromise = firstValueFrom(api.findDestinations(source));

    const request = httpTesting.expectOne(
      (pending) => pending.url === '/api/conversions/destinations',
    );

    expect(request.request.method).toBe('GET');
    expect(request.request.params.get('sourcePlaylist')).toBe(source);

    request.flush(expected);

    expect(await responsePromise).toEqual(expected);
  });

  it('shouldReturnEmptyListWhenNoDestinationsAreSaved', async () => {
    const responsePromise = firstValueFrom(
      api.findDestinations('1234567890123456789012'),
    );

    const request = httpTesting.expectOne(
      (pending) => pending.url === '/api/conversions/destinations',
    );

    request.flush([]);

    expect(await responsePromise).toEqual([]);
  });
});
