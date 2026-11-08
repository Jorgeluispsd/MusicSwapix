import { TestBed } from '@angular/core/testing';
import { ConversionPage } from './conversion-page';

describe('ConversionPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ConversionPage],
    }).compileComponents();
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
});
