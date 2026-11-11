import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { App } from './app';
import { routes } from './app.routes';
import { of } from 'rxjs';
import { ConversionHistoryApi } from './core/api/conversion-history-api';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter(routes),
        {
          provide: ConversionHistoryApi,
          useValue: { findHistory: () => of({ entries: [], totalCount: 0 }) },
        },
      ],
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);

    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the MusicSwapix brand', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const compiled = fixture.nativeElement as HTMLElement;

    expect(compiled.querySelector('.brand')?.textContent).toContain('MusicSwapix');
  });

  it.each([
    ['/converter', 'Qual playlist vamos levar?'],
    ['/historico', 'Histórico de conversões'],
  ])('should render the page and highlight navigation for %s', async (path, heading) => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);

    fixture.detectChanges();
    await router.navigateByUrl(path);
    await fixture.whenStable();
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const activeLink = compiled.querySelector<HTMLAnchorElement>(
      '.app-nav a[aria-current="page"]',
    );

    expect(compiled.querySelector('h2')?.textContent).toContain(heading);
    expect(activeLink?.getAttribute('href')).toBe(path);
    expect(compiled.querySelector('.brand')?.textContent).toContain('MusicSwapix');
    expect(compiled.querySelector('.ambient')).not.toBeNull();
  });

  it('should redirect the initial address to conversion', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);

    fixture.detectChanges();
    await router.navigateByUrl('/');
    await fixture.whenStable();

    expect(router.url).toBe('/converter');
  });

  it('should redirect an unknown address to conversion', async () => {
    const fixture = TestBed.createComponent(App);
    const router = TestBed.inject(Router);

    fixture.detectChanges();
    await router.navigateByUrl('/pagina-inexistente');
    await fixture.whenStable();

    expect(router.url).toBe('/converter');
  });
});
