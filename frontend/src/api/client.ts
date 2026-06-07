import type {
  ApiResponse,
  ExportPackageResponse,
  ListingTaskDetailResponse,
  ListingTaskSummaryResponse,
  OperationAuditLogResponse,
  PagedResponse,
  SubmitListingTaskResponse
} from './types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers ?? {})
    }
  });
  const payload = (await response.json()) as ApiResponse<T>;
  if (!response.ok || !payload.success) {
    throw new Error(payload.message || payload.code || 'Request failed');
  }
  return payload.data;
}

async function multipartRequest<T>(path: string, formData: FormData): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: 'POST',
    body: formData
  });
  const payload = (await response.json()) as ApiResponse<T>;
  if (!response.ok || !payload.success) {
    throw new Error(payload.message || payload.code || 'Request failed');
  }
  return payload.data;
}

function query(params: Record<string, string | number | null | undefined>): string {
  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '') {
      searchParams.set(key, String(value));
    }
  });
  const value = searchParams.toString();
  return value ? `?${value}` : '';
}

export interface ExportListParams {
  format?: string | null;
  status?: string | null;
  page: number;
  size: number;
}

export interface TaskListParams {
  status?: string | null;
  marketplace?: string | null;
  categoryCode?: string | null;
  page: number;
  size: number;
}

export async function listTasks(
  params: TaskListParams
): Promise<PagedResponse<ListingTaskSummaryResponse>> {
  return request<PagedResponse<ListingTaskSummaryResponse>>(
    `/api/v1/listing/tasks${query({
      status: params.status,
      marketplace: params.marketplace,
      categoryCode: params.categoryCode,
      page: params.page,
      size: params.size
    })}`
  );
}

export async function getTaskDetail(taskId: string): Promise<ListingTaskDetailResponse> {
  return request<ListingTaskDetailResponse>(`/api/v1/listing/${taskId}`);
}

export interface SubmitListingTaskInput {
  file: File;
  productImages: File[];
  asins: string[];
  marketplace: string;
  language: string;
}

export async function submitListingTask(input: SubmitListingTaskInput): Promise<SubmitListingTaskResponse> {
  const formData = new FormData();
  formData.append('file', input.file);
  input.productImages.forEach((image) => formData.append('productImages', image));
  input.asins.forEach((asin) => formData.append('asins', asin));
  formData.append('marketplace', input.marketplace);
  formData.append('language', input.language);
  return multipartRequest<SubmitListingTaskResponse>('/api/v1/listing/submit', formData);
}

export async function listExportPackages(
  taskId: string,
  params: ExportListParams
): Promise<PagedResponse<ExportPackageResponse>> {
  return request<PagedResponse<ExportPackageResponse>>(
    `/api/v1/listing/${taskId}/exports${query({
      format: params.format,
      status: params.status,
      page: params.page,
      size: params.size
    })}`
  );
}

export async function createPendingExportPackage(
  taskId: string,
  format: string
): Promise<ExportPackageResponse> {
  return request<ExportPackageResponse>(
    `/api/v1/listing/${taskId}/exports${query({ format })}`,
    { method: 'POST' }
  );
}

export async function runExportPackage(exportPackageId: string): Promise<ExportPackageResponse> {
  return request<ExportPackageResponse>(`/api/v1/listing/export/${exportPackageId}/run`, {
    method: 'POST'
  });
}

export async function retryExportPackage(exportPackageId: string): Promise<ExportPackageResponse> {
  return request<ExportPackageResponse>(`/api/v1/listing/export/${exportPackageId}/retry`, {
    method: 'POST'
  });
}

export async function cancelExportPackage(
  exportPackageId: string,
  operatorId: string,
  cancelReason: string
): Promise<ExportPackageResponse> {
  return request<ExportPackageResponse>(`/api/v1/listing/export/${exportPackageId}/cancel`, {
    method: 'POST',
    headers: {
      'X-Operator-Id': operatorId
    },
    body: JSON.stringify({
      canceledBy: operatorId,
      cancelReason
    })
  });
}

export async function listAuditLogs(
  taskId: string,
  page: number,
  size: number
): Promise<PagedResponse<OperationAuditLogResponse>> {
  return request<PagedResponse<OperationAuditLogResponse>>(
    `/api/v1/listing/audit-logs${query({ taskId, page, size })}`
  );
}
