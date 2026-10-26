/** Local-date helpers. Dates are sent as plain local ISO strings (the server stores local date-times). */
const pad = (n: number) => String(n).padStart(2, '0');
export function isoDate(d: Date): string { return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`; }
export function addDays(d: Date, n: number): Date { const c = new Date(d); c.setDate(c.getDate() + n); return c; }
export function parseIsoDate(s: string): Date { const [y, m, d] = s.split('-').map(Number); return new Date(y, m - 1, d); }
export function startOfWeek(d: Date): Date { const c = new Date(d); const diff = (c.getDay() + 6) % 7; c.setDate(c.getDate() - diff); c.setHours(0, 0, 0, 0); return c; }
/** "2026-10-21T10:00:00" -> "2026-10-21T10:00" for <input type="datetime-local">. */
export function toInput(v: string | null | undefined): string { return v ? v.slice(0, 16) : ''; }
export function fromInput(v: string | null | undefined): string | null { return v ? (v.length === 16 ? v + ':00' : v) : null; }
export function fmtDateTime(v: string | null | undefined): string {
  if (!v) return '—';
  return new Date(v).toLocaleString(undefined, { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}
export function fmtDate(v: string | null | undefined): string {
  if (!v) return '—';
  const d = v.length === 10 ? parseIsoDate(v) : new Date(v);
  return d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' });
}
export function fmtTime(v: string): string { return new Date(v).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' }); }
export function initials(name: string | undefined): string {
  return (name ?? '?').split(/\s+/).filter(Boolean).slice(0, 2).map((s) => s[0]!.toUpperCase()).join('');
}
