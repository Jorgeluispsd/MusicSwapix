import { Routes } from '@angular/router';
import { ConversionPage } from './pages/conversion/conversion-page';
import { HistoryPage } from './pages/history/history-page';


export const routes: Routes = [
  {
    path: '',
    redirectTo: 'converter',
    pathMatch: 'full',
  },
  {
    path: 'converter',
    component: ConversionPage,
  },
  {
    path: 'historico',
    component: HistoryPage,
  },
  {
    path: '**',
    redirectTo: 'converter',
  }
];
