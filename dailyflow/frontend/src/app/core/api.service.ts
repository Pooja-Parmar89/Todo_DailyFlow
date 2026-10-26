import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Activity, AuthResponse, CalendarItem, Category, Dashboard, PageResponse, ProductivityReport,
  ReminderSummary, Task, TaskQuery, TaskRequest, TaskStatus, User,
} from './models';

/** Thin typed wrapper over the REST API. All paths are relative so the dev proxy / nginx can route them. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // ---- auth / profile
  register(body: { displayName: string; email: string; password: string }) {
    return this.http.post<AuthResponse>('/api/auth/register', body);
  }
  login(body: { email: string; password: string }) {
    return this.http.post<AuthResponse>('/api/auth/login', body);
  }
  logout() { return this.http.post<void>('/api/auth/logout', {}); }
  me() { return this.http.get<User>('/api/auth/me'); }
  updateProfile(body: { displayName: string; emailRemindersEnabled: boolean; reminderLeadMinutes: number }) {
    return this.http.put<User>('/api/users/me', body);
  }
  changePassword(body: { currentPassword: string; newPassword: string }) {
    return this.http.put<AuthResponse>('/api/users/me/password', body);
  }

  // ---- tasks
  listTasks(q: TaskQuery): Observable<PageResponse<Task>> {
    let p = new HttpParams();
    const set = (k: string, v: unknown) => { if (v !== undefined && v !== null && v !== '') p = p.set(k, String(v)); };
    set('q', q.q); set('categoryId', q.categoryId); set('dueFrom', q.dueFrom); set('dueTo', q.dueTo);
    set('page', q.page ?? 0); set('size', q.size ?? 10); set('sort', q.sort ?? 'dueAt'); set('direction', q.direction ?? 'asc');
    if (q.overdue) set('overdue', true);
    (q.priority ?? []).forEach((v) => (p = p.append('priority', v)));
    (q.status ?? []).forEach((v) => (p = p.append('status', v)));
    return this.http.get<PageResponse<Task>>('/api/tasks', { params: p });
  }
  getTask(id: number) { return this.http.get<Task>(`/api/tasks/${id}`); }
  createTask(body: TaskRequest) { return this.http.post<Task>('/api/tasks', body); }
  updateTask(id: number, body: TaskRequest) { return this.http.put<Task>(`/api/tasks/${id}`, body); }
  deleteTask(id: number) { return this.http.delete<void>(`/api/tasks/${id}`); }
  setStatus(id: number, status: TaskStatus) { return this.http.patch<Task>(`/api/tasks/${id}/status`, { status }); }
  completeTask(id: number) { return this.http.post<Task>(`/api/tasks/${id}/complete`, {}); }
  reopenTask(id: number) { return this.http.post<Task>(`/api/tasks/${id}/reopen`, {}); }
  completeOccurrence(id: number, date: string) { return this.http.post(`/api/tasks/${id}/occurrences/${date}/complete`, {}); }
  skipOccurrence(id: number, date: string) { return this.http.post(`/api/tasks/${id}/occurrences/${date}/skip`, {}); }
  rescheduleOccurrence(id: number, date: string, newDate: string) {
    return this.http.post(`/api/tasks/${id}/occurrences/${date}/reschedule`, { newDate });
  }
  resetOccurrence(id: number, date: string) { return this.http.delete<void>(`/api/tasks/${id}/occurrences/${date}`); }

  // ---- categories
  categories() { return this.http.get<Category[]>('/api/categories'); }
  createCategory(b: { name: string; color: string | null; icon: string | null }) { return this.http.post<Category>('/api/categories', b); }
  updateCategory(id: number, b: { name: string; color: string | null; icon: string | null }) { return this.http.put<Category>(`/api/categories/${id}`, b); }
  deleteCategory(id: number) { return this.http.delete<void>(`/api/categories/${id}`); }

  // ---- insights
  dashboard() { return this.http.get<Dashboard>('/api/dashboard'); }
  calendar(from: string, to: string) {
    return this.http.get<CalendarItem[]>('/api/calendar', { params: { from, to } });
  }
  productivity(days: number) { return this.http.get<ProductivityReport>('/api/reports/productivity', { params: { days } }); }
  reminders() { return this.http.get<ReminderSummary>('/api/reminders'); }
  activity(page = 0, size = 15) {
    return this.http.get<PageResponse<Activity>>('/api/activity', { params: { page, size } });
  }
}
