import { TestBed } from '@angular/core/testing';
import { ConversionPage } from './conversion-page';
import { provideHttpClient } from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting,} from '@angular/common/http/testing';

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
      visibility: 'UNLISTED',
    });

    expect(component.form.valid).toBe(true);
    expect(component.form.getRawValue()).toEqual({
      sourcePlaylist: 'https://open.spotify.com/playlist/1234567890123456789012',
      destinationName: 'Meu Rock',
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

  function createPageWithPreview(): ConversionPage {
    const component = TestBed.createComponent(ConversionPage).componentInstance;

    component.form.setValue({
      sourcePlaylist: '1234567890123456789012',
      destinationName: 'Meu Rock',
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

    return component;
  }

  it('should show cancellation only while reviewing a preview', () => {
    const fixture = TestBed.createComponent(ConversionPage);
    const component = fixture.componentInstance;
    const compiled = fixture.nativeElement as HTMLElement;

    fixture.detectChanges();

    expect(compiled.querySelector('.cancel-button')).toBeNull();

    component.form.setValue({
      sourcePlaylist: '1234567890123456789012',
      destinationName: 'Meu Rock',
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

    expect(compiled.querySelector('.cancel-button')).toBeNull();
    expect(compiled.querySelector('.preview-result')).toBeNull();
  });
});
