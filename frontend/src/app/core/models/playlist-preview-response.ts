import { PlaylistVisibility } from './playlist-visibility';

export interface TrackReviewOptionResponse {
  destinationTrackId: string;
  destinationTitle: string;
  channelTitle: string;
  durationMs: number;
  confidence: number;
  alreadyPresent: boolean;
  artworkUrl: string | null;
}

export interface MatchedTrackResponse {
  sourceTrackId: string;
  sourceTitle: string;
  sourceArtist: string;
  destinationTrackId: string;
  destinationTitle: string;
  confidence: number;
  alreadyPresent: boolean;
  trackPosition: number;
  reviewOptions: TrackReviewOptionResponse[];
  artworkUrl: string | null;
}

export interface UnmatchedTrackResponse {
  sourceTrackId: string;
  title: string;
  artist: string;
}

export interface PlaylistPreviewResponse {
  previewId: string;
  sourcePlaylistId: string;
  destinationPlaylistId: string | null;
  destinationName: string | null;
  visibility: PlaylistVisibility | null;
  expiresAt: string;
  total: number;
  found: number;
  alreadyPresent: number;
  newTrackCount: number;
  repeatedCandidateCount: number;
  notFoundCount: number;
  matches: MatchedTrackResponse[];
  notFound: UnmatchedTrackResponse[];
}
