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
});
