import {Component, computed,DestroyRef, inject, signal }  from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import { PlaylistPreviewApi } from '../../core/api/playlist-preview-api';
import { PlaylistPreviewResponse } from '../../core/models/playlist-preview-response';
import { PlaylistVisibility } from '../../core/models/playlist-visibility';
import { PlaylistConversionResponse } from '../../core/models/playlist-conversion-response';
import { HttpErrorResponse } from '@angular/common/http';
import { PlaylistDestinationApi } from '../../core/api/playlist-destination-api';
import { PlaylistDestinationResponse } from '../../core/models/playlist-destination-response';
import {distinctUntilChanged, filter, finalize, map, Observable, startWith, takeUntil,} from 'rxjs';

import { applyReviewSelections, summarizeReviewedTracks } from './track-review';

type DestinationMode = 'new' | 'existing';

@Component({
  selector: 'app-conversion-page',
  imports: [ReactiveFormsModule],
  templateUrl: './conversion-page.html',
  styleUrl: './conversion-page.css',
})
export class ConversionPage {
  private readonly previewApi = inject(PlaylistPreviewApi);
  private readonly destinationApi = inject(PlaylistDestinationApi);
  private readonly destroyRef = inject(DestroyRef);

  readonly loading = signal(false);
  readonly preview = signal<PlaylistPreviewResponse | null>(null);
  readonly errorMessage = signal('');
  readonly confirming = signal(false);
  readonly conversionResult = signal<PlaylistConversionResponse | null>(null);
  readonly destinationMode = signal<DestinationMode>('new');
  readonly skippedPositions = signal<readonly number[]>([]);
  readonly loadingDestinations = signal(false);
  readonly savedDestinations = signal<PlaylistDestinationResponse[] | null>(null);
  readonly destinationLookupError = signal('');

  private readonly reviewSelections = signal<Readonly<Record<number, string>>>({});

  readonly reviewedMatches = computed(() => applyReviewSelections(
    this.preview()?.matches ?? [],
    this.reviewSelections(),
    this.skippedPositions(),
  ));

  readonly reviewSummary = computed(() => {
    const hasEdits = Object.keys(this.reviewSelections()).length > 0
      || this.skippedPositions().length > 0;

    return summarizeReviewedTracks(this.preview(), this.reviewedMatches(), hasEdits);
  });

  readonly previewExpired = signal(false);
  private previewExpirationTimer: ReturnType<typeof setTimeout> | undefined;

  readonly canConfirm = computed(() => {
    const stored = this.preview();

    if (!stored || this.loading() || this.previewExpired()) {
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

  constructor() {
    this.destroyRef.onDestroy(() => clearTimeout(this.previewExpirationTimer));

    const sourceControl = this.form.controls.sourcePlaylist;

    sourceControl.valueChanges
      .pipe(
        startWith(sourceControl.value),
        map((value) => value.trim()),
        distinctUntilChanged(),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(() => {
        this.savedDestinations.set(null);
        this.destinationLookupError.set('');
      });
  }

  findSavedDestinations(): void {
    if (
      this.destinationMode() !== 'existing' ||
      this.loading() ||
      this.loadingDestinations() ||
      this.preview() ||
      this.conversionResult()
    ) {
      return;
    }

    const sourceControl = this.form.controls.sourcePlaylist;
    sourceControl.markAsTouched();

    if (sourceControl.invalid) {
      return;
    }

    const source = sourceControl.value.trim();
    this.savedDestinations.set(null);
    this.destinationLookupError.set('');
    this.loadingDestinations.set(true);

    this.destinationApi.findDestinations(source)
      .pipe(
        takeUntil(sourceControl.valueChanges.pipe(
          filter((value) => value.trim() !== source),
        )),
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loadingDestinations.set(false)),
      )
      .subscribe({
        next: (destinations) => this.savedDestinations.set(destinations),
        error: () => this.destinationLookupError.set(
          'Não foi possível consultar os destinos anteriores. Confira a origem e tente novamente.',
        ),
      });
  }

  useSavedDestination(destinationId: string): void {
    if (this.loading() || this.destinationMode() !== 'existing') {
      return;
    }

    this.form.controls.destinationPlaylist.setValue(destinationId);
  }

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
        next: (response) => {
          this.preview.set(response);
          this.watchPreviewExpiration(response.expiresAt);
        },
        error: (error : unknown) => this.errorMessage.set(
          this.prepareErrorMessage(error),
        ),
      });
  }

  cancelPreview(): void {
    const stored = this.preview();

    if (!stored || this.loading()) {
      return;
    }

    if (this.previewExpired()) {
      this.clearPreview();
      this.form.enable();
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

    if (Date.parse(stored.expiresAt) <= Date.now()) {
      this.previewExpired.set(true);
      return;
    }

    this.errorMessage.set('');
    this.loading.set(true);
    this.confirming.set(true);

    this.previewApi.confirmPreview(stored.previewId, this.reviewSelections(), this.skippedPositions(),)
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

          const code = this.apiErrorCode(error);

          if (code === 'PREVIEW_UNAVAILABLE') {
            this.errorMessage.set(
              'Esta prévia expirou ou já foi encerrada. Gere uma nova prévia para continuar.',
            );
            return;
          }

          if (code === 'PREVIEW_CHANGED') {
            this.errorMessage.set(
              'A playlist de destino mudou desde a análise. Gere uma nova prévia para conferir os itens atuais.',
            );
            return;
          }

          if (code === 'YOUTUBE_TRACK_INSERTION_FAILED') {
            const nextStep = stored.destinationPlaylistId !== null
              ? 'Confira a playlist no YouTube e gere uma nova prévia com o mesmo destino.'
              : 'Localize a playlist criada no YouTube, selecione Playlist existente e gere uma nova prévia com o link dela.';

            this.errorMessage.set(
              'A conversão foi interrompida durante a inserção. '
              + 'Algumas músicas podem já ter sido adicionadas. '
              + nextStep,
            );
            return;
          }

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
    if (this.loading() || this.skippedPositions().includes(trackPosition)) {
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
    clearTimeout(this.previewExpirationTimer);
    this.previewExpired.set(false);

    this.preview.set(null);
    this.reviewSelections.set({});
    this.skippedPositions.set([]);
  }

  private watchPreviewExpiration(expiresAt: string): void {
    clearTimeout(this.previewExpirationTimer);
    this.previewExpired.set(false);

    const expirationTime = Date.parse(expiresAt);

    if (!Number.isFinite(expirationTime)) {
      return;
    }

    const checkExpiration = () => {
      const remainingMs = expirationTime - Date.now();

      if (remainingMs <= 0) {
        this.previewExpired.set(true);
        return;
      }

      this.previewExpirationTimer = setTimeout(
        checkExpiration,
        Math.min(remainingMs, 2_147_483_647),
      );
    };

    checkExpiration();
  }

  toggleSkippedTrack(trackPosition: number): void {
    if (this.loading()) {
      return;
    }

    const exists = this.preview()?.matches.some(
      (match) => match.trackPosition === trackPosition,
    );

    if (!exists) {
      return;
    }

    const isSkipped = this.skippedPositions().includes(trackPosition);

    this.skippedPositions.update((positions) => isSkipped
      ? positions.filter((position) => position !== trackPosition)
      : [...positions, trackPosition]);

    const selections = { ...this.reviewSelections() };
    delete selections[trackPosition];
    this.reviewSelections.set(selections);
    this.errorMessage.set('');
  }

  private apiErrorCode(error: unknown): string | undefined {
    if (!(error instanceof HttpErrorResponse)) {
      return undefined;
    }

    const code = error.error?.code;
    return typeof code === 'string' ? code : undefined;
  }

  private prepareErrorMessage(error: unknown): string {
    switch (this.apiErrorCode(error)) {
      case 'YOUTUBE_QUOTA_EXCEEDED': {
        const resetAt = error instanceof HttpErrorResponse
          ? error.headers.get('X-YouTube-Quota-Reset-At')
          : null;

        const resetDate = resetAt ? new Date(resetAt) : null;

        if (!resetDate || Number.isNaN(resetDate.getTime())) {
          return 'A cota da API do YouTube foi atingida. Aguarde a renovação antes de tentar novamente.';
        }

        const localTime = resetDate.toLocaleTimeString('pt-BR', {
          hour: '2-digit',
          minute: '2-digit',
        });

        return 'A cota da API do YouTube foi atingida. ' +
        `A próxima renovação das cotas diárias está prevista para ${localTime}, no seu horário local.`;
      }
      case 'YOUTUBE_SEARCH_RATE_LIMITED':
        return 'O YouTube limitou as buscas. Aguarde um pouco antes de tentar novamente.';
      case 'SPOTIFY_RATE_LIMITED':
        return 'O Spotify limitou as requisições. Aguarde um pouco antes de tentar novamente.';
      case 'INVALID_PLAYLIST_INPUT':
        return 'Confira se os links ou IDs das playlists correspondem às plataformas informadas.';
      default:
        return 'Não foi possível gerar a prévia. Tente novamente em instantes.';
    }
  }
}
