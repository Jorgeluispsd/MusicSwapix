import {HttpClient} from '@angular/common/http';
import {inject, Injectable} from '@angular/core';
import {Observable} from 'rxjs';
import { NewPlaylistPreviewRequest } from '../models/new-playlist-preview-request';
import { PlaylistPreviewResponse } from '../models/playlist-preview-response';
import { PlaylistConversionResponse } from '../models/playlist-conversion-response';
import { ExistingPlaylistPreviewRequest } from '../models/existing-playlist-preview-request';

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

  prepareExistingPlaylist(
    request: ExistingPlaylistPreviewRequest,
  ): Observable<PlaylistPreviewResponse> {
    return this.http.post<PlaylistPreviewResponse>(
      '/api/conversions/previews/existing',
      request,
    );
  }

  confirmPreview(previewId: string, selections: Readonly<Record<number, string>> = {},
                 ): Observable<PlaylistConversionResponse> {
    const body = Object.keys(selections).length > 0
      ? { selections }
      : null;

    return this.http.post<PlaylistConversionResponse>(
      `/api/conversions/previews/${encodeURIComponent(previewId)}/confirm`,
      body,
    );
  }

  cancelPreview(previewId: string): Observable<void> {
    return this.http.delete<void>(
      `/api/conversions/previews/${encodeURIComponent(previewId)}`,
    );
  }
}
