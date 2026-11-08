import {PlaylistVisibility} from './playlist-visibility';

export interface NewPlaylistPreviewRequest{
  sourcePlaylist: string;
  destinationName: string;
  visibility: PlaylistVisibility;
}
