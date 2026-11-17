import { Component, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ConversionPage } from './pages/conversion/conversion-page';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app.html',
  styleUrl: './app.css',
  host: {
    '[attr.data-theme]': 'theme()',
  },
})
export class App {
  readonly theme = signal<'youtube' | 'spotify'>('youtube');

  selectTheme(theme: 'youtube' | 'spotify', outlet: RouterOutlet): void {
    const page = outlet.isActivated ? outlet.component : null;

    if (page instanceof ConversionPage) {
      const direction = theme === 'spotify' ? 'youtube-to-spotify' : 'spotify-to-youtube';
      page.selectDirection(direction);

      if (page.direction() !== direction) {
        return;
      }
    }

    this.theme.set(theme);
  }

  syncConversionDirection(page: unknown): void {
    if (page instanceof ConversionPage) {
      page.selectDirection(
        this.theme() === 'spotify' ? 'youtube-to-spotify' : 'spotify-to-youtube',
      );
    }
  }
}


