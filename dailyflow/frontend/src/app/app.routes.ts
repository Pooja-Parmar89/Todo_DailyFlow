import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './core/auth';

/** Every page is lazy-loaded so the first paint only ships the shell. */
export const routes: Routes = [
  { path: 'login', canActivate: [guestGuard], title: 'Sign in · DailyFlow', loadComponent: () => import('./pages/auth-pages').then((m) => m.LoginPage) },
  { path: 'register', canActivate: [guestGuard], title: 'Create account · DailyFlow', loadComponent: () => import('./pages/auth-pages').then((m) => m.RegisterPage) },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', title: 'Dashboard · DailyFlow', loadComponent: () => import('./pages/dashboard').then((m) => m.DashboardPage) },
      { path: 'tasks', title: 'All tasks · DailyFlow', data: { mode: 'all' }, loadComponent: () => import('./pages/tasks').then((m) => m.TasksPage) },
      { path: 'completed', title: 'Completed · DailyFlow', data: { mode: 'completed' }, loadComponent: () => import('./pages/tasks').then((m) => m.TasksPage) },
      { path: 'overdue', title: 'Overdue · DailyFlow', data: { mode: 'overdue' }, loadComponent: () => import('./pages/tasks').then((m) => m.TasksPage) },
      { path: 'today', title: 'Today · DailyFlow', loadComponent: () => import('./pages/planner').then((m) => m.PlannerPage) },
      { path: 'calendar', title: 'Calendar · DailyFlow', loadComponent: () => import('./pages/calendar').then((m) => m.CalendarPage) },
      { path: 'categories', title: 'Categories · DailyFlow', loadComponent: () => import('./pages/categories').then((m) => m.CategoriesPage) },
      { path: 'reports', title: 'Reports · DailyFlow', loadComponent: () => import('./pages/reports').then((m) => m.ReportsPage) },
      { path: 'profile', title: 'Profile · DailyFlow', loadComponent: () => import('./pages/profile').then((m) => m.ProfilePage) },
    ],
  },
  { path: '**', redirectTo: '' },
];
