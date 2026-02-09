import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'clients', pathMatch: 'full' },
  { path: 'clients', loadComponent: () => import('./features/clients/clients.component').then(m => m.ClientsComponent) },
  { path: 'limits', loadComponent: () => import('./features/limits/limits.component').then(m => m.LimitsComponent) },
  { path: 'notify', loadComponent: () => import('./features/notify/notify.component').then(m => m.NotifyComponent) },
  { path: '**', redirectTo: 'clients' },
];
