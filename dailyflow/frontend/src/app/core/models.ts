export type Priority = 'HIGH' | 'MEDIUM' | 'LOW';
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'COMPLETED';
export type RecurrenceType = 'NONE' | 'DAILY' | 'WEEKLY' | 'CUSTOM';

export const PRIORITIES: Priority[] = ['HIGH', 'MEDIUM', 'LOW'];
export const STATUSES: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'COMPLETED'];
export const STATUS_LABEL: Record<TaskStatus, string> = {
  TODO: 'To do',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
};

export interface User {
  id: number;
  email: string;
  displayName: string;
  emailRemindersEnabled: boolean;
  reminderLeadMinutes: number;
}
export interface AuthResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  user: User;
}
export interface CategorySummary { id: number; name: string; color: string; icon: string | null }
export interface Category extends CategorySummary { defaultCategory: boolean; taskCount: number }

export interface Task {
  id: number;
  title: string;
  description: string | null;
  category: CategorySummary | null;
  priority: Priority;
  status: TaskStatus;
  startAt: string | null;
  dueAt: string | null;
  completedAt: string | null;
  createdAt: string;
  updatedAt: string;
  overdue: boolean;
  recurrenceType: RecurrenceType;
  recurrenceInterval: number | null;
  recurrenceEndDate: string | null;
}
export interface TaskRequest {
  title: string;
  description: string | null;
  categoryId: number | null;
  priority: Priority;
  status: TaskStatus;
  startAt: string | null;
  dueAt: string | null;
  recurrenceType: RecurrenceType;
  recurrenceInterval: number | null;
  recurrenceEndDate: string | null;
}
export interface PageResponse<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }

export interface TaskQuery {
  q?: string;
  categoryId?: number | null;
  priority?: Priority[];
  status?: TaskStatus[];
  dueFrom?: string;
  dueTo?: string;
  overdue?: boolean;
  page?: number;
  size?: number;
  sort?: string;
  direction?: 'asc' | 'desc';
}

export interface CalendarItem {
  key: string;
  taskId: number;
  title: string;
  categoryId: number | null;
  categoryName: string | null;
  categoryColor: string | null;
  priority: Priority;
  status: TaskStatus;
  dueAt: string;
  recurring: boolean;
  occurrenceDate: string | null;
  occurrenceStatus: string | null;
  originalDate: string | null;
  overdue: boolean;
}
export interface DailyCount { date: string; completed: number; created: number }
export interface Dashboard {
  total: number; completed: number; pending: number; inProgress: number; overdue: number;
  dueToday: number; dueTodayCompleted: number; completionPercentage: number;
  upcoming: CalendarItem[]; recentlyCreated: Task[]; recentlyUpdated: Task[]; weekly: DailyCount[];
}
export interface NamedCount { name: string; count: number }
export interface ProductivityReport {
  from: string; to: string; totalCompleted: number; totalCreated: number; averageCompletedPerDay: number;
  currentStreakDays: number; daily: DailyCount[]; byCategory: NamedCount[]; byPriority: NamedCount[];
}
export interface ReminderSummary { overdueCount: number; dueSoon: CalendarItem[]; leadMinutes: number }
export interface Activity { id: number; taskId: number | null; taskTitle: string | null; action: string; summary: string; createdAt: string }
export interface ApiError { status: number; error: string; message: string; fieldErrors?: Record<string, string> }
