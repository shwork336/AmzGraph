export function displayTime(value?: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-';
}

export function displayValue(value?: string | number | null): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  return String(value);
}

export function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}
