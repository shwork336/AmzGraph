import type { TagProps } from 'naive-ui';

export type StatusTagType = NonNullable<TagProps['type']>;

export function statusTagType(status?: string | null): StatusTagType {
  if (status === 'COMPLETED' || status === 'APPROVED' || status === 'SUCCEEDED' || status === 'PASS') {
    return 'success';
  }
  if (status === 'FAILED' || status === 'REJECTED' || status === 'FAIL') {
    return 'error';
  }
  if (status === 'WARNING' || status === 'CANCELED' || status === 'CANCELLED' || status?.includes('WAIT')) {
    return 'warning';
  }
  if (status === 'RUNNING' || status === 'GENERATING') {
    return 'info';
  }
  return 'default';
}
