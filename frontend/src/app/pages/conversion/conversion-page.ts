import {Component, computed,DestroyRef, inject, signal }  from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import { finalize, Observable } from 'rxjs';
import { PlaylistPreviewApi } from '../../core/api/playlist-preview-api';
import { PlaylistPreviewResponse } from '../../core/models/playlist-preview-response';
import { PlaylistVisibility } from '../../core/models/playlist-visibility';
import { PlaylistConversionResponse } from '../../core/models/playlist-conversion-response';
import { HttpErrorResponse } from '@angular/common/http';

type DestinationMode = 'new' | 'existing';

@Component({
  selector: 'app-conversion-page',
  imports: [ReactiveFormsModule],
  templateUrl: './conversion-page.html',
  styleUrl: './conversion-page.css',
})
export class ConversionPage {
  private readonly previewApi = inject(PlaylistPreviewApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(false);
  readonly preview = signal<PlaylistPreviewResponse | null>(null);
  readonly errorMessage = signal('');
  readonly confirming = signal(false);
  readonly conversionResult = signal<PlaylistConversionResponse | null>(null);
  readonly destinationMode = signal<DestinationMode>('new');

  private readonly reviewSelections = signal<Readonly<Record<number, string>>>({});

  readonly reviewedMatches = computed(() =>{
    const selections = this.reviewSelections();

    return (this.preview()?.matches ?? []).map((match) => {
      const selected  = match.reviewOptions.find(
        (option) => option.destinationTrackId ===selections[match.trackPosition],
      );

      if (!selected){
        return match;
      }

      return{
        ...match,
        destinationTrackId: selected.destinationTrackId,
        destinationTitle: selected.destinationTitle,
        artworkUrl: selected.artworkUrl,
        confidence: selected.confidence,
        alreadyPresent: selected.alreadyPresent,
      };
    });
  });

  readonly reviewSummary = computed(() => {
    const stored = this.preview();

    if (!stored) {
      return { alreadyPresent: 0, newTrackCount: 0 };
    }

    if (Object.keys(this.reviewSelections()).length === 0) {
      return {
        alreadyPresent: stored.alreadyPresent,
        newTrackCount: stored.newTrackCount,
      };
    }

    const matches = this.reviewedMatches();
    const newIds = new Set(
      matches
        .filter((match) => !match.alreadyPresent)
        .map((match) => match.destinationTrackId),
    );

    return {
      alreadyPresent: matches.filter((match) => match.alreadyPresent).length,
      newTrackCount: newIds.size,
    };
  });

  readonly canConfirm = computed(() => {
    const stored = this.preview();

    if (!stored || this.loading()) {
      return false;
    }

    return stored.destinationPlaylistId !== null
      || this.reviewSummary().newTrackCount > 0;
  });

  readonly form = new FormGroup({
    sourcePlaylist: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/)],
    }),
    destinationName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/)],
    }),
    destinationPlaylist: new FormControl('', {
      nonNullable: true,
    }),
    visibility: new FormControl<PlaylistVisibility>('PRIVATE', {
      nonNullable: true,
    }),
  });

  selectDestinationMode(mode: DestinationMode): void {
    if (this.loading() || this.preview() || this.conversionResult()) {
      return;
    }

    this.destinationMode.set(mode);

    const nameControl = this.form.controls.destinationName;
    const playlistControl = this.form.controls.destinationPlaylist;
    const requiredText = [
      Validators.required,
      Validators.pattern(/\S/),
    ];

    nameControl.setValidators(mode === 'new' ? requiredText : []);
    playlistControl.setValidators(mode === 'existing' ? requiredText : []);

    nameControl.updateValueAndValidity();
    playlistControl.updateValueAndValidity();

    this.errorMessage.set('');
  }

  preparePreview(): void {
    if (this.loading() || this.preview() || this.conversionResult()) {
      return;
    }

    this.form.markAllAsTouched();

    if (this.form.invalid) {
      return;
    }

    const request = this.requestPreview();

    this.errorMessage.set('');
    this.loading.set(true);
    this.form.disable();

    request
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.loading.set(false);

          if (!this.preview()) {
            this.form.enable();
          }
        }),
      )
      .subscribe({
        next: (response) => this.preview.set(response),
        error: () => this.errorMessage.set(
          'Não foi possível gerar a prévia. Tente novamente em instantes.',
        ),
      });
  }

  cancelPreview(): void {
    const stored = this.preview();

    if (!stored || this.loading()) {
      return;
    }

    this.errorMessage.set('');
    this.loading.set(true);

    this.previewApi.cancelPreview(stored.previewId)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: () => {
          this.clearPreview();
          this.form.enable();
        },
        error: () => this.errorMessage.set(
          'Não foi possível cancelar a prévia. Tente novamente em instantes.',
        ),
      });
  }

  confirmPreview(): void {
    const stored = this.preview();

    if (!stored || !this.canConfirm()) {
      return;
    }

    this.errorMessage.set('');
    this.loading.set(true);
    this.confirming.set(true);

    this.previewApi.confirmPreview(stored.previewId, this.reviewSelections())
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => {
          this.loading.set(false);
          this.confirming.set(false);
        }),
      )
      .subscribe({
        next: (response) => {
          this.conversionResult.set(response);
          this.clearPreview();
        },
        error: (error: unknown) => {
          if (
            error instanceof HttpErrorResponse &&
            error.status === 400
            && error.error?.code === 'INVALID_TRACK_SELECTION') {
            this.errorMessage.set(
              'Uma das escolhas é inválida. Revise as opções e confirme novamente.',
            );
            return;
          }

          this.clearPreview();
          this.form.enable();
          this.errorMessage.set(
            'Não foi possível confirmar o resultado da conversão. '
            + 'Confira a playlist no YouTube antes de gerar uma nova prévia, '
            + 'pois alterações podem já ter sido feitas.',
          );
        },
      });
  }

  startNewConversion(): void {
    if (this.loading()) {
      return;
    }

    this.conversionResult.set(null);
    this.errorMessage.set('');
    this.form.reset();
    this.form.enable();
  }

  private requestPreview(): Observable<PlaylistPreviewResponse> {
    const values = this.form.getRawValue();
    const sourcePlaylist = values.sourcePlaylist.trim();

    if (this.destinationMode() === 'existing') {
      return this.previewApi.prepareExistingPlaylist({
        sourcePlaylist,
        destinationPlaylist: values.destinationPlaylist.trim(),
      });
    }

    return this.previewApi.prepareNewPlaylist({
      sourcePlaylist,
      destinationName: values.destinationName.trim(),
      visibility: values.visibility,
    });
  }

  selectReviewOption(trackPosition: number, videoId: string): void {
    if (this.loading()) {
      return;
    }

    const match = this.preview()?.matches.find(
      (item) => item.trackPosition === trackPosition,
    );

    if (!match?.reviewOptions.some((option) => option.destinationTrackId === videoId)) {
      return;
    }

    this.reviewSelections.update((current) => ({
      ...current,
      [trackPosition]: videoId,
    }));
    this.errorMessage.set('');
  }

  formatDuration(durationMs: number): string {
    if (durationMs <= 0) {
      return 'Duração indisponível';
    }

    const totalSeconds = Math.floor(durationMs / 1000);
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = String(totalSeconds % 60).padStart(2, '0');

    return `${minutes}:${seconds}`;
  }

  private clearPreview(): void {
    this.preview.set(null);
    this.reviewSelections.set({});
  }
}
