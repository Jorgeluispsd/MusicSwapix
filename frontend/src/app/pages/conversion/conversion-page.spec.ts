import { TestBed } from '@angular/core/testing';
import { ConversionPage } from './conversion-page';
import { provideHttpClient } from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting,} from '@angular/common/http/testing';
import { vi } from 'vitest';

describe('ConversionPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConversionPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    }).compileComponents();
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
  });

  it('should start with required fields empty and private visibility', () => {
    const component = TestBed.createComponent(ConversionPage).componentInstance;

    expect(component.form.invalid).toBe(true);
    expect(component.form.controls.visibility.value).toBe('PRIVATE');
  });

  it.each(['sourcePlaylist', 'destinationName'] as const)(
    'should reject whitespace-only content in %s',
    (field) => {
      const component = TestBed.createComponent(ConversionPage).componentInstance;
      const control = component.form.controls[field];

      control.setValue('   ');

      expect(control.invalid).toBe(true);
    },
  );

  it('should accept filled fields and preserve selected visibility', () => {
    const component = TestBed.createComponent(ConversionPage).componentInstance;

    component.form.setValue({
      sourcePlaylist: 'https://open.spotify.com/playlist/1234567890123456789012',
      destinationName: 'Meu Rock',
      destinationPlaylist: '',
      visibility: 'UNLISTED',
    });

    expect(component.form.valid).toBe(true);
    expect(component.form.getRawValue()).toEqual({
      sourcePlaylist: 'https://open.spotify.com/playlist/1234567890123456789012',
      destinationName: 'Meu Rock',
      destinationPlaylist: '',
      visibility: 'UNLISTED',
    });
  });

  it('should show the source error only after the field is touched', () => {
    const fixture = TestBed.createComponent(ConversionPage);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('#source-error')).toBeNull();

    const sourceInput = compiled.querySelector<HTMLInputElement>('#source-playlist')!;
    sourceInput.dispatchEvent(new Event('blur'));
    fixture.detectChanges();

    expect(compiled.querySelector('#source-error')?.textContent).toContain(
      'Informe o link ou o ID da playlist de origem.',
    );
  });

  it('should reject invalid submission before sending a request', () => {
    const component = TestBed.createComponent(ConversionPage).componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);

    component.preparePreview();

    expect(component.form.controls.sourcePlaylist.touched).toBe(true);
    expect(component.form.controls.destinationName.touched).toBe(true);
    expect(component.loading()).toBe(false);

    httpTesting.expectNone('/api/conversions/previews/new');
  });

  it('should prevent duplicate submission and release the form after failure', () => {
    const component = TestBed.createComponent(ConversionPage).componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);

    component.form.setValue({
      sourcePlaylist: ' 1234567890123456789012 ',
      destinationName: ' Meu Rock ',
      destinationPlaylist: '',
      visibility: 'PRIVATE',
    });

    component.preparePreview();
    component.preparePreview();

    expect(component.loading()).toBe(true);
    expect(component.form.disabled).toBe(true);

    const request = httpTesting.expectOne('/api/conversions/previews/new');

    expect(request.request.body).toEqual({
      sourcePlaylist: '1234567890123456789012',
      destinationName: 'Meu Rock',
      visibility: 'PRIVATE',
    });

    request.flush({}, { status: 503, statusText: 'Service Unavailable' });

    expect(component.loading()).toBe(false);
    expect(component.form.enabled).toBe(true);
    expect(component.preview()).toBeNull();
    expect(component.errorMessage()).toBe(
      'Não foi possível gerar a prévia. Tente novamente em instantes.',
    );
  });

  it('should cancel the stored preview and return to preparation', () => {
    const component = createPageWithPreview();
    const httpTesting = TestBed.inject(HttpTestingController);
    const previewId = component.preview()!.previewId;

    component.cancelPreview();
    component.cancelPreview();

    expect(component.loading()).toBe(true);

    const request = httpTesting.expectOne(
      `/api/conversions/previews/${previewId}`,
    );

    expect(request.request.method).toBe('DELETE');

    request.flush(null, { status: 204, statusText: 'No Content' });

    expect(component.preview()).toBeNull();
    expect(component.loading()).toBe(false);
    expect(component.form.enabled).toBe(true);
    expect(component.form.controls.destinationName.value).toBe('Meu Rock');
    expect(component.errorMessage()).toBe('');
  });

  it('should preserve the preview when cancellation fails', () => {
    const component = createPageWithPreview();
    const httpTesting = TestBed.inject(HttpTestingController);
    const original = component.preview()!;

    component.cancelPreview();

    httpTesting.expectOne(
      `/api/conversions/previews/${original.previewId}`,
    ).flush({}, { status: 503, statusText: 'Service Unavailable' });

    expect(component.preview()).toBe(original);
    expect(component.loading()).toBe(false);
    expect(component.form.disabled).toBe(true);
    expect(component.errorMessage()).toBe(
      'Não foi possível cancelar a prévia. Tente novamente em instantes.',
    );
  });

  it('should ignore cancellation when no preview exists', () => {
    const component = TestBed.createComponent(ConversionPage).componentInstance;

    component.cancelPreview();

    expect(component.loading()).toBe(false);
    TestBed.inject(HttpTestingController).expectNone(
      (request) => request.method === 'DELETE',
    );
  });

  function createPageWithPreview(expiresAt = '2099-01-01T12:00:00Z',): ConversionPage {
    const component = TestBed.createComponent(ConversionPage).componentInstance;

    component.form.setValue({
      sourcePlaylist: '1234567890123456789012',
      destinationName: 'Meu Rock',
      destinationPlaylist: '',
      visibility: 'PRIVATE',
    });

    component.preparePreview();

    TestBed.inject(HttpTestingController)
      .expectOne('/api/conversions/previews/new')
      .flush({
        previewId: '3ebf4e4c-7ebd-4c52-b5b0-d943d4647206',
        sourcePlaylistId: '1234567890123456789012',
        destinationPlaylistId: null,
        destinationName: 'Meu Rock',
        visibility: 'PRIVATE',
        expiresAt: expiresAt,
        total: 1,
        found: 1,
        alreadyPresent: 0,
        newTrackCount: 1,
        repeatedCandidateCount: 0,
        notFoundCount: 0,
        matches: [
          {
            sourceTrackId: 'source-track',
            sourceTitle: 'Song A',
            sourceArtist: 'Artist A',
            destinationTrackId: 'video-A',
            destinationTitle: 'Video A',
            confidence: 0.95,
            alreadyPresent: false,
            trackPosition: 0,
            reviewOptions: [],
          },
        ],
        notFound: [],
      });

    return component;
  }

  it('should show cancellation only while reviewing a preview', () => {
    const fixture = TestBed.createComponent(ConversionPage);
    const component = fixture.componentInstance;
    const compiled = fixture.nativeElement as HTMLElement;

    fixture.detectChanges();

    expect(compiled.querySelector('.preview-result .cancel-button'),).toBeNull();

    component.form.setValue({
      sourcePlaylist: '1234567890123456789012',
      destinationName: 'Meu Rock',
      destinationPlaylist: '',
      visibility: 'PRIVATE',
    });

    component.preparePreview();

    TestBed.inject(HttpTestingController)
      .expectOne('/api/conversions/previews/new')
      .flush({
        previewId: '3ebf4e4c-7ebd-4c52-b5b0-d943d4647206',
        sourcePlaylistId: '1234567890123456789012',
        destinationPlaylistId: null,
        destinationName: 'Meu Rock',
        visibility: 'PRIVATE',
        expiresAt: '2099-01-01T12:00:00Z',
        total: 0,
        found: 0,
        alreadyPresent: 0,
        newTrackCount: 0,
        repeatedCandidateCount: 0,
        notFoundCount: 0,
        matches: [],
        notFound: [],
      });

    fixture.detectChanges();

    const cancelButton = compiled.querySelector<HTMLButtonElement>(
      '.preview-result .cancel-button',
    );

    expect(cancelButton).not.toBeNull();

    cancelButton!.click();

    TestBed.inject(HttpTestingController)
      .expectOne(
        '/api/conversions/previews/3ebf4e4c-7ebd-4c52-b5b0-d943d4647206',
      )
      .flush(null, { status: 204, statusText: 'No Content' });

    fixture.detectChanges();

    expect(compiled.querySelector('.preview-result .cancel-button'),).toBeNull();
    expect(compiled.querySelector('.preview-result')).toBeNull();
  });

  it('should confirm once and prevent cancellation while confirming', () => {
    const component = createPageWithPreview();
    const httpTesting = TestBed.inject(HttpTestingController);
    const previewId = component.preview()!.previewId;

    component.confirmPreview();
    component.confirmPreview();
    component.cancelPreview();

    expect(component.loading()).toBe(true);
    expect(component.confirming()).toBe(true);

    const request = httpTesting.expectOne(
      `/api/conversions/previews/${previewId}/confirm`,
    );

    httpTesting.expectNone(
      (pending) => pending.method === 'DELETE',
    );

    request.flush({
      sourcePlaylistId: '1234567890123456789012',
      destinationPlaylistId: 'new-destination',
      destinationPlaylistUrl: 'https://www.youtube.com/playlist?list=new-destination',
      insertedCount: 1,
      skippedCount: 0,
      notFoundCount: 0,
      notFound: [],
    });

    expect(component.preview()).toBeNull();
    expect(component.conversionResult()?.insertedCount).toBe(1);
    expect(component.loading()).toBe(false);
    expect(component.confirming()).toBe(false);

    component.startNewConversion();

    expect(component.conversionResult()).toBeNull();
    expect(component.form.enabled).toBe(true);
    expect(component.form.controls.sourcePlaylist.value).toBe('');
    expect(component.form.controls.visibility.value).toBe('PRIVATE');
  });

  it('should discard the consumed preview after confirmation failure', () => {
    const component = createPageWithPreview();
    const httpTesting = TestBed.inject(HttpTestingController);
    const previewId = component.preview()!.previewId;

    component.confirmPreview();

    httpTesting.expectOne(
      `/api/conversions/previews/${previewId}/confirm`,
    ).flush({}, { status: 502, statusText: 'Bad Gateway' });

    expect(component.preview()).toBeNull();
    expect(component.conversionResult()).toBeNull();
    expect(component.form.enabled).toBe(true);
    expect(component.loading()).toBe(false);
    expect(component.errorMessage()).toContain('Confira a playlist no YouTube');

    component.confirmPreview();

    httpTesting.expectNone(
      (pending) => pending.method === 'POST',
    );
  });

  it('should prevent confirmation when the preview has no tracks to add', () => {
    const component = createPageWithPreview();
    const httpTesting = TestBed.inject(HttpTestingController);

    component.preview.set({
      ...component.preview()!,
      newTrackCount: 0,
    });

    component.confirmPreview();

    expect(component.loading()).toBe(false);
    httpTesting.expectNone(
      (pending) => pending.method === 'POST',
    );
  });

  it('shouldRequireDestinationInsteadOfNameForExistingPlaylist', () => {
    const component = TestBed.createComponent(ConversionPage).componentInstance;

    component.selectDestinationMode('existing');

    component.form.patchValue({
      sourcePlaylist: 'source-playlist',
      destinationName: '',
      destinationPlaylist: '   ',
    });

    expect(component.form.controls.destinationName.valid).toBe(true);
    expect(component.form.controls.destinationPlaylist.invalid).toBe(true);
    expect(component.form.invalid).toBe(true);

    component.form.controls.destinationPlaylist.setValue('PL-destination');

    expect(component.form.valid).toBe(true);

    component.selectDestinationMode('new');

    expect(component.form.controls.destinationName.invalid).toBe(true);
    expect(component.form.invalid).toBe(true);
  });

  it('shouldSendExistingDestinationWithoutNameOrVisibility', () => {
    const component = TestBed.createComponent(ConversionPage).componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);

    component.selectDestinationMode('existing');

    component.form.patchValue({
      sourcePlaylist: ' source-playlist ',
      destinationPlaylist: ' PL-destination ',
    });

    component.preparePreview();

    const request = httpTesting.expectOne(
      '/api/conversions/previews/existing',
    );

    expect(request.request.body).toEqual({
      sourcePlaylist: 'source-playlist',
      destinationPlaylist: 'PL-destination',
    });

    httpTesting.expectNone('/api/conversions/previews/new');

    request.flush({}, {
      status: 503,
      statusText: 'Service Unavailable',
    });

    expect(component.form.enabled).toBe(true);
    expect(component.loading()).toBe(false);
  });

  it('shouldShowFieldsForTheSelectedDestinationMode', () => {
    const fixture = TestBed.createComponent(ConversionPage);
    const compiled = fixture.nativeElement as HTMLElement;

    fixture.detectChanges();

    expect(compiled.querySelector('#destination-name')).not.toBeNull();
    expect(compiled.querySelector('#visibility')).not.toBeNull();
    expect(compiled.querySelector('#destination-playlist')).toBeNull();

    compiled
      .querySelector<HTMLButtonElement>('#existing-destination-mode')!
      .click();

    fixture.detectChanges();

    expect(compiled.querySelector('#destination-name')).toBeNull();
    expect(compiled.querySelector('#visibility')).toBeNull();
    expect(compiled.querySelector('#destination-playlist')).not.toBeNull();
  });

  it('shouldConfirmExistingPlaylistWhenNoNewTracksAreNeeded', () => {
    const component = createPageWithPreview();
    const httpTesting = TestBed.inject(HttpTestingController);

    component.preview.set({
      ...component.preview()!,
      destinationPlaylistId: 'PL-existing',
      destinationName: null,
      visibility: null,
      alreadyPresent: 1,
      newTrackCount: 0,
      matches: component.preview()!.matches.map((match) => ({
        ...match,
        alreadyPresent: true,
      })),
    });

    const previewId = component.preview()!.previewId;

    component.confirmPreview();

    const request = httpTesting.expectOne(
      `/api/conversions/previews/${previewId}/confirm`,
    );

    request.flush({
      sourcePlaylistId: '1234567890123456789012',
      destinationPlaylistId: 'PL-existing',
      destinationPlaylistUrl:
        'https://www.youtube.com/playlist?list=PL-existing',
      insertedCount: 0,
      skippedCount: 1,
      notFoundCount: 0,
      notFound: [],
    });

    expect(component.preview()).toBeNull();
    expect(component.conversionResult()?.insertedCount).toBe(0);
    expect(component.conversionResult()?.skippedCount).toBe(1);
  });

  it('shouldKeepReviewOpenAfterAnInvalidSelection', () => {
    const component = createPageWithPreview();
    const httpTesting = TestBed.inject(HttpTestingController);
    const original = component.preview()!;

    const stored = {
      ...original,
      destinationPlaylistId: 'PL-existing',
      matches: [{
        ...original.matches[0],
        trackPosition: 1,
        reviewOptions: [
          {
            destinationTrackId: 'video-A',
            destinationTitle: 'Video A',
            channelTitle: 'Artist A',
            durationMs: 200_000,
            confidence: 0.95,
            alreadyPresent: false,
            artworkUrl: null,
          },
          {
            destinationTrackId: 'video-B',
            destinationTitle: 'Video B',
            channelTitle: 'Label',
            durationMs: 202_000,
            confidence: 0.94,
            alreadyPresent: true,
            artworkUrl: null,
          },
        ],
      }],
    };

    component.preview.set(stored);
    component.selectReviewOption(1, 'video-B');

    expect(component.reviewSummary()).toEqual({
      alreadyPresent: 1,
      newTrackCount: 0,
    });

    const url = `/api/conversions/previews/${stored.previewId}/confirm`;

    component.confirmPreview();

    const rejected = httpTesting.expectOne(url);
    expect(rejected.request.body).toEqual({
      selections: { 1: 'video-B' },
      skippedPositions: [],
    });

    rejected.flush(
      { code: 'INVALID_TRACK_SELECTION' },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(component.preview()).toBe(stored);
    expect(component.loading()).toBe(false);
    expect(component.errorMessage()).toContain('Revise');

    component.selectReviewOption(1, 'video-A');
    component.confirmPreview();

    const corrected = httpTesting.expectOne(url);
    expect(corrected.request.body).toEqual({
      selections: { 1: 'video-A' },
      skippedPositions: [],
    });

    corrected.flush({
      sourcePlaylistId: stored.sourcePlaylistId,
      destinationPlaylistId: 'PL-existing',
      destinationPlaylistUrl: 'https://www.youtube.com/playlist?list=PL-existing',
      insertedCount: 1,
      skippedCount: 0,
      notFoundCount: 0,
      notFound: [],
      skippedByUserCount: 0,
    });

    expect(component.preview()).toBeNull();
    expect(component.conversionResult()?.insertedCount).toBe(1);
  });

  it('shouldSkipAndRestoreTrackBeforeConfirmation', () => {
    const component = createPageWithPreview();

    expect(component.reviewSummary().newTrackCount).toBe(1);

    component.toggleSkippedTrack(0);

    expect(component.skippedPositions()).toEqual([0]);
    expect(component.reviewSummary().newTrackCount).toBe(0);
    expect(component.reviewedMatches()[0].skippedByUser).toBe(true);
    expect(component.canConfirm()).toBe(false);

    component.toggleSkippedTrack(0);

    expect(component.skippedPositions()).toEqual([]);
    expect(component.reviewSummary().newTrackCount).toBe(1);
    expect(component.canConfirm()).toBe(true);
  });

  it('shouldSendSkippedPositionsWhenConfirmingExistingPlaylist', () => {
    const component = createPageWithPreview();
    const httpTesting = TestBed.inject(HttpTestingController);

    component.preview.set({
      ...component.preview()!,
      destinationPlaylistId: 'PL-existing',
    });

    const previewId = component.preview()!.previewId;

    component.toggleSkippedTrack(0);
    component.confirmPreview();

    const request = httpTesting.expectOne(
      `/api/conversions/previews/${previewId}/confirm`,
    );

    expect(request.request.body).toEqual({
      selections: {},
      skippedPositions: [0],
    });

    request.flush({
      sourcePlaylistId: 'source-playlist',
      destinationPlaylistId: 'PL-existing',
      destinationPlaylistUrl: 'https://www.youtube.com/playlist?list=PL-existing',
      insertedCount: 0,
      skippedCount: 0,
      skippedByUserCount: 1,
      notFoundCount: 0,
      notFound: [],
    });

    expect(component.conversionResult()?.skippedByUserCount).toBe(1);
    expect(component.skippedPositions()).toEqual([]);
  });

  it('shouldExplainYouTubeQuotaFailureDuringPreparation', () => {
    const component = TestBed.createComponent(ConversionPage).componentInstance;
    const httpTesting = TestBed.inject(HttpTestingController);

    component.form.patchValue({
      sourcePlaylist: 'source-playlist',
      destinationName: 'Minha playlist',
    });

    component.preparePreview();

    httpTesting.expectOne('/api/conversions/previews/new').flush(
      { code: 'YOUTUBE_QUOTA_EXCEEDED' },
      {
        status: 429, statusText: 'Too Many Requests', headers: {
          'X-YouTube-Quota-Reset-At': '2026-11-11T08:00:00Z',
        },
      }
    );

    const expectedTime = new Date('2026-11-11T08:00:00Z')
      .toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });

    expect(component.errorMessage()).toContain(
      `prevista para ${expectedTime}, no seu horário local`,
    );

    expect(component.form.enabled).toBe(true);
    expect(component.preview()).toBeNull();
  });

  it('shouldAskForNewPreviewWhenConfirmationIsUnavailable', () => {
    const component = createPageWithPreview();
    const httpTesting = TestBed.inject(HttpTestingController);
    const previewId = component.preview()!.previewId;

    component.confirmPreview();

    httpTesting.expectOne(`/api/conversions/previews/${previewId}/confirm`).flush(
      { code: 'PREVIEW_UNAVAILABLE' },
      { status: 404, statusText: 'Not Found' },
    );

    expect(component.errorMessage()).toContain('Gere uma nova prévia');
    expect(component.preview()).toBeNull();
    expect(component.form.enabled).toBe(true);
  });

  it('shouldBlockConfirmationWhenPreviewExpiresOnScreen', () => {
    vi.useFakeTimers();

    try {
      const expiresAt = new Date(Date.now() + 1_000).toISOString();
      const component = createPageWithPreview(expiresAt);
      const httpTesting = TestBed.inject(HttpTestingController);

      expect(component.canConfirm()).toBe(true);

      vi.advanceTimersByTime(1_000);

      expect(component.previewExpired()).toBe(true);
      expect(component.canConfirm()).toBe(false);

      component.confirmPreview();
      component.cancelPreview();

      httpTesting.expectNone((request) =>
        request.method === 'POST' || request.method === 'DELETE',
      );
      expect(component.preview()).toBeNull();
      expect(component.form.enabled).toBe(true);
    } finally {
      vi.useRealTimers();
    }
  });
});
