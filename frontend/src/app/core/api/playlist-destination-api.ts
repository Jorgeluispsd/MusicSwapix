import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { PlaylistDestinationResponse } from '../models/playlist-destination-response';

@Injectable({
  providedIn: 'root',
})
export class PlaylistDestinationApi {
  private readonly http = inject(HttpClient);

  findDestinations(
    sourcePlaylist: string,
  ): Observable<PlaylistDestinationResponse[]> {
    return this.http.get<PlaylistDestinationResponse[]>(
      '/api/conversions/destinations',
      {
        params: { sourcePlaylist },
      },
    );
  }
}
