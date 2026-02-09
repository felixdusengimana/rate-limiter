/**
 * Utility for formatting seconds into human-readable time durations.
 */

export function formatDuration(seconds: number): string {
  if (seconds < 0) {
    return 'invalid';
  }

  if (seconds < 60) {
    return `${seconds} second${seconds === 1 ? '' : 's'}`;
  }

  let minutes = Math.floor(seconds / 60);
  let remainingSeconds = seconds % 60;

  if (minutes < 60) {
    if (remainingSeconds > 0) {
      return `${minutes} minute${minutes === 1 ? '' : 's'} ${remainingSeconds} second${remainingSeconds === 1 ? '' : 's'}`;
    }
    return `${minutes} minute${minutes === 1 ? '' : 's'}`;
  }

  let hours = Math.floor(minutes / 60);
  minutes = minutes % 60;

  if (hours < 24) {
    if (minutes > 0) {
      return `${hours} hour${hours === 1 ? '' : 's'} ${minutes} minute${minutes === 1 ? '' : 's'}`;
    }
    return `${hours} hour${hours === 1 ? '' : 's'}`;
  }

  let days = Math.floor(hours / 24);
  hours = hours % 24;

  if (days < 7) {
    if (hours > 0) {
      return `${days} day${days === 1 ? '' : 's'} ${hours} hour${hours === 1 ? '' : 's'}`;
    }
    return `${days} day${days === 1 ? '' : 's'}`;
  }

  let weeks = Math.floor(days / 7);
  days = days % 7;

  if (days > 0) {
    return `${weeks} week${weeks === 1 ? '' : 's'} ${days} day${days === 1 ? '' : 's'}`;
  }
  return `${weeks} week${weeks === 1 ? '' : 's'}`;
}
