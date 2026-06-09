const OPERATOR_ID_STORAGE_KEY = 'amzgraph.operatorId';
const DEFAULT_OPERATOR_ID = 'operator@example.com';

export function loadOperatorId(): string {
  return localStorage.getItem(OPERATOR_ID_STORAGE_KEY) ?? DEFAULT_OPERATOR_ID;
}

export function normalizeOperatorId(value: string): string {
  return value.trim();
}

export function saveOperatorId(value: string): string {
  const normalized = normalizeOperatorId(value);
  localStorage.setItem(OPERATOR_ID_STORAGE_KEY, normalized);
  return normalized;
}
