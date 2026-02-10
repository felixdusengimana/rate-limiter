import { formatDuration } from './time-format.util';

describe('TimeFormatUtil - formatDuration', () => {
  it('should format seconds correctly', () => {
    expect(formatDuration(0)).toBe('0 seconds');
    expect(formatDuration(1)).toBe('1 second');
    expect(formatDuration(30)).toBe('30 seconds');
    expect(formatDuration(59)).toBe('59 seconds');
  });

  it('should format minutes correctly', () => {
    expect(formatDuration(60)).toBe('1 minute');
    expect(formatDuration(61)).toBe('1 minute 1 second');
    expect(formatDuration(120)).toBe('2 minutes');
    expect(formatDuration(150)).toBe('2 minutes 30 seconds');
    expect(formatDuration(3599)).toBe('59 minutes 59 seconds');
  });

  it('should format hours correctly', () => {
    expect(formatDuration(3600)).toBe('1 hour');
    expect(formatDuration(3660)).toBe('1 hour 1 minute');
    expect(formatDuration(7200)).toBe('2 hours');
    expect(formatDuration(7260)).toBe('2 hours 1 minute');
    expect(formatDuration(86399)).toBe('23 hours 59 minutes');
  });

  it('should format days correctly', () => {
    expect(formatDuration(86400)).toBe('1 day');
    expect(formatDuration(86400 + 3600)).toBe('1 day 1 hour');
    expect(formatDuration(86400 * 2)).toBe('2 days');
    expect(formatDuration(86400 * 7)).toBe('1 week');
  });

  it('should handle singular and plural forms correctly', () => {
    expect(formatDuration(1)).toContain('second'); // singular
    expect(formatDuration(2)).toContain('seconds'); // plural

    expect(formatDuration(60)).toContain('minute'); // singular
    expect(formatDuration(120)).toContain('minutes'); // plural

    expect(formatDuration(3600)).toContain('hour'); // singular
    expect(formatDuration(7200)).toContain('hours'); // plural

    expect(formatDuration(86400)).toContain('day'); // singular
    expect(formatDuration(86400 * 2)).toContain('days'); // plural
  });

  it('should handle negative numbers as invalid', () => {
    expect(formatDuration(-1)).toBe('invalid');
    expect(formatDuration(-100)).toBe('invalid');
    expect(formatDuration(-86400)).toBe('invalid');
  });

  it('should handle large numbers', () => {
    const thirtyDays = 86400 * 30;
    expect(formatDuration(thirtyDays)).toContain('weeks');

    const oneYear = 86400 * 365;
    const result = formatDuration(oneYear);
    expect(result).toContain('weeks');
  });

  it('should handle edge cases', () => {
    expect(formatDuration(0)).toBe('0 seconds');
    expect(formatDuration(1)).toBe('1 second');
    expect(formatDuration(60)).toBe('1 minute');
    expect(formatDuration(3600)).toBe('1 hour');
    expect(formatDuration(86400)).toBe('1 day');
  });

  it('should format complex durations correctly', () => {
    // 1 day + 2 hours + 3 minutes + 4 seconds
    const complex = 86400 + 7200 + 180 + 4;
    const result = formatDuration(complex);
    expect(result).toContain('1 day');
    expect(result).toContain('2 hours');
  });

  it('should handle maximum safe integer values', () => {
    const maxSeconds = Number.MAX_SAFE_INTEGER;
    const result = formatDuration(maxSeconds);
    expect(result).toBeTruthy();
    expect(result).not.toBe('invalid');
  });
});
