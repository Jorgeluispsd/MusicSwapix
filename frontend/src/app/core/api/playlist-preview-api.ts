import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import { NewPlaylistPreviewRequest } from '../models/new-playlist-preview-request';
import { PlaylistPreviewResponse } from '../models/playlist-preview-response';
import { PlaylistConversionResponse } from '../models/playlist-conversion-response';

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

  confirmPreview(previewId: string): Observable<PlaylistConversionResponse> {
    return this.http.post<PlaylistConversionResponse>(
      `/api/conversions/previews/${encodeURIComponent(previewId)}/confirm`,
      null,
    );
  }

  cancelPreview(previewId: string): Observable<void> {
    return this.http.delete<void>(
      `/api/conversions/previews/${encodeURIComponent(previewId)}`,
    );
  }
}
