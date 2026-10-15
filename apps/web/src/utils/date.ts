const VN_TIME_ZONE = 'Asia/Ho_Chi_Minh';

export function toVietnamDateInput(date: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: VN_TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).formatToParts(date);

  const value = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find(part => part.type === type)?.value ?? '';

  return `${value('year')}-${value('month')}-${value('day')}`;
}

export function addVietnamDays(days: number, date: Date = new Date()): string {
  const current = toVietnamDateInput(date);
  const [year, month, day] = current.split('-').map(Number);
  return toVietnamDateInput(new Date(Date.UTC(year, month - 1, day + days, 12)));
}
