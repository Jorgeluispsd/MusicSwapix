import { UnmatchedTrackResponse } from './playlist-preview-response';

export interface PlaylistConversionResponse {
  sourcePlaylistId: string;
  destinationPlaylistId: string;
  destinationPlaylistUrl: string;
  insertedCount: number;
  skippedCount: number;
  notFoundCount: number;
  notFound: UnmatchedTrackResponse[];
  skippedByUserCount: number;
}
