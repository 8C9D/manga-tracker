export function chaptersBehind(latest: string | null, lastRead: string | null): number {
  if (!latest || !lastRead) return 0;
  const diff = parseFloat(latest) - parseFloat(lastRead);
  return diff > 0 ? Math.floor(diff) : 0;
}

export function daysUntil(date: string | null): string {
  if (!date) return '';
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = new Date(date + 'T00:00:00');
  const diff = Math.round((target.getTime() - today.getTime()) / 86400000);
  if (diff < 0) return 'overdue';
  if (diff === 0) return 'today';
  if (diff === 1) return 'tomorrow';
  return `in ${diff} days`;
}
