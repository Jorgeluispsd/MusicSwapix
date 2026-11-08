import {Component, computed,DestroyRef, inject, signal }  from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import { finalize, Observable } from 'rxjs';
import { PlaylistPreviewApi } from '../../core/api/playlist-preview-api';
import { PlaylistPreviewResponse } from '../../core/models/playlist-preview-response';
import { PlaylistVisibility } from '../../core/models/playlist-visibility';
import { PlaylistConversionResponse } from '../../core/models/playlist-conversion-response';

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

  readonly canConfirm = computed(() => {
    const stored = this.preview();

    if (!stored || this.loading()) {
      return false;
    }

    return stored.destinationPlaylistId !== null
      || stored.newTrackCount > 0;
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
          this.preview.set(null);
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

    this.previewApi.confirmPreview(stored.previewId)
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
          this.preview.set(null);
        },
        error: () => {
          this.preview.set(null);
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
}
