import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import { NewPlaylistPreviewRequest } from '../models/new-playlist-preview-request';
import { PlaylistPreviewResponse } from '../models/playlist-preview-response';



@Injectable({
  providedIn: 'root'
})
export class PlaylistPreviewApi {
  private readonly http = inject(HttpClient);

  prepareNewPlaylist(
    request: NewPlaylistPreviewRequest,
  ): Observable<PlaylistPreviewResponse> {
    return this.http.post<PlaylistPreviewResponse>(
      '/api/conversions/previews/new',
      request,
    );
  }
}
