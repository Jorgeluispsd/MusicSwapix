import { MatchedTrackResponse, PlaylistPreviewResponse } from '../../core/models/playlist-preview-response';

export function applyReviewSelections(
  matches: readonly MatchedTrackResponse[],
  selections: Readonly<Record<number, string>>,
  skipped: readonly number[],
) {
  return matches.map((match) => {
    const selected = match.reviewOptions.find(
      (option) => option.destinationTrackId === selections[match.trackPosition],
    );

    const current = selected
      ? {
        ...match,
        destinationTrackId: selected.destinationTrackId,
        destinationTitle: selected.destinationTitle,
        artworkUrl: selected.artworkUrl,
        confidence: selected.confidence,
        alreadyPresent: selected.alreadyPresent,
      }
      : match;

    return {
      ...current,
      skippedByUser: skipped.includes(match.trackPosition),
    };
  });
}

export function summarizeReviewedTracks(
  preview: PlaylistPreviewResponse | null,
  matches: readonly (MatchedTrackResponse & { skippedByUser: boolean })[],
  hasEdits: boolean,
) {
  if (!preview) {
    return { alreadyPresent: 0, newTrackCount: 0 };
  }

  if (!hasEdits) {
    return { alreadyPresent: preview.alreadyPresent, newTrackCount: preview.newTrackCount };
  }

  const included = matches.filter((match) => !match.skippedByUser);
  const newIds = new Set(
    included.filter((match) => !match.alreadyPresent).map((match) => match.destinationTrackId),
  );

  return {
    alreadyPresent: included.filter((match) => match.alreadyPresent).length,
    newTrackCount: newIds.size,
  };
}
