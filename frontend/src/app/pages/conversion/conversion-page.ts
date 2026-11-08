import {Component, DestroyRef, inject, signal }  from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import { finalize } from 'rxjs';
import { PlaylistPreviewApi } from '../../core/api/playlist-preview-api';
import { NewPlaylistPreviewRequest } from '../../core/models/new-playlist-preview-request';
import { PlaylistPreviewResponse } from '../../core/models/playlist-preview-response';
import { PlaylistVisibility } from '../../core/models/playlist-visibility';

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

  readonly form = new FormGroup({
    sourcePlaylist: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/)],
    }),
    destinationName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/\S/)],
    }),
    visibility: new FormControl<PlaylistVisibility>('PRIVATE', {
      nonNullable: true,
    }),
  });

  preparePreview(): void {
    if (this.loading() || this.preview()) {
      return;
    }

    this.form.markAllAsTouched();

    if (this.form.invalid) {
      return;
    }

    const values = this.form.getRawValue();

    const request: NewPlaylistPreviewRequest = {
      sourcePlaylist: values.sourcePlaylist.trim(),
      destinationName: values.destinationName.trim(),
      visibility: values.visibility,
    };

    this.errorMessage.set('');
    this.loading.set(true);
    this.form.disable();

    this.previewApi.prepareNewPlaylist(request)
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
}
