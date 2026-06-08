type TaskSubRoute =
  | 'brief'
  | 'compliance'
  | 'competitors'
  | 'export'
  | 'final'
  | 'images'
  | 'text';

function encodedTaskId(taskId: string): string {
  return encodeURIComponent(taskId);
}

export function taskListPath(): string {
  return '/tasks';
}

export function taskCreatePath(): string {
  return '/tasks/new';
}

export function taskDetailPath(taskId: string): string {
  return `/tasks/${encodedTaskId(taskId)}`;
}

export function taskSubPath(taskId: string, subRoute: TaskSubRoute): string {
  return `${taskDetailPath(taskId)}/${subRoute}`;
}
