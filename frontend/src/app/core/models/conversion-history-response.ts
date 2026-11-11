export interface ConversionHistoryResponse {
  executionId: number;
  sourcePlaylistId: string;
  destinationPlaylistId: string;
  destinationPlaylistUrl: string | null;
  direction: string;
  status: string;
  startedAt: string | null;
  completedAt: string | null;
}
