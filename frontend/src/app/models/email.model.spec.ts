import { isExpiredDueDate, todayIsoDate } from './email.model';

describe('isExpiredDueDate', () => {
  it('treats dates before today as expired', () => {
    expect(isExpiredDueDate('2026-09-07', '2026-09-08')).toBeTrue();
  });

  it('does not treat today or future as expired', () => {
    expect(isExpiredDueDate('2026-09-08', '2026-09-08')).toBeFalse();
    expect(isExpiredDueDate('2026-09-09', '2026-09-08')).toBeFalse();
  });

  it('ignores missing due dates', () => {
    expect(isExpiredDueDate(null, '2026-09-08')).toBeFalse();
  });

  it('formats local today as YYYY-MM-DD', () => {
    expect(todayIsoDate(new Date(2026, 8, 8))).toBe('2026-09-08');
  });
});
