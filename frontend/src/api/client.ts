import type {
  ApiResponse,
  BriefVersionResponse,
  CompetitorSnapshotResponse,
  ExportPackageResponse,
  FinalSelectionResponse,
  ImageAssetComplianceReviewResponse,
  ImageAssetResponse,
  ImageVersionResponse,
  ListingTaskDetailResponse,
  ListingTaskSummaryResponse,
  OperationAuditLogResponse,
  PagedResponse,
  SubmitListingTaskResponse,
  TextVersionResponse
} from './types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';

async function parseApiResponse<T>(response: Response): Promise<T> {
  const rawBody = await response.text();
  if (!rawBody) {
    if (response.ok) {
      return undefined as T;
    }
    throw new Error(`请求失败：HTTP ${response.status}`);
  }

  let payload: ApiResponse<T>;
  try {
    payload = JSON.parse(rawBody) as ApiResponse<T>;
  } catch {
    if (!response.ok) {
      throw new Error(`请求失败：HTTP ${response.status}`);
    }
    throw new Error('服务端返回格式异常');
  }

  if (!response.ok || !payload.success) {
    throw new Error(payload.message || payload.code || `请求失败：HTTP ${response.status}`);
  }
  return payload.data;
}

async function sendRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
  try {
    const response = await fetch(`${API_BASE_URL}${path}`, options);
    return parseApiResponse<T>(response);
  } catch (error) {
    if (error instanceof Error) {
      throw error;
    }
    throw new Error('请求失败');
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  return sendRequest<T>(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers ?? {})
    }
  });
}

async function multipartRequest<T>(path: string, formData: FormData): Promise<T> {
  return sendRequest<T>(path, {
    method: 'POST',
    body: formData
  });
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

export interface AuditLogListParams {
  taskId?: string | null;
  action?: string | null;
  operatorId?: string | null;
  targetType?: string | null;
  targetId?: string | null;
  page: number;
  size: number;
}

export interface CreateBriefVersionInput {
  baseBriefVersionId: string;
  createdBy: string;
  targetAudience: string;
  coreSellingPoints: string[];
  targetKeywords: string[];
  forbiddenClaims: string[];
  imageDirectionPrompts: string[];
  complianceNotes: string[];
}

export interface ManualCompetitorSnapshotInput {
  asin: string;
  title: string;
  bulletPoints: string[];
  rating?: number | null;
  reviewCount?: number | null;
  reviewPainPoints: string[];
  keywordSignals: string[];
  sourceName?: string | null;
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

export async function listBriefVersions(taskId: string): Promise<BriefVersionResponse[]> {
  return request<BriefVersionResponse[]>(`/api/v1/listing/${taskId}/briefs`);
}

export async function getLatestBrief(taskId: string): Promise<BriefVersionResponse> {
  return request<BriefVersionResponse>(`/api/v1/listing/${taskId}/briefs/latest`);
}

export async function createBriefVersion(
  taskId: string,
  input: CreateBriefVersionInput
): Promise<BriefVersionResponse> {
  return request<BriefVersionResponse>(`/api/v1/listing/${taskId}/briefs`, {
    method: 'POST',
    body: JSON.stringify(input)
  });
}

export async function approveBriefVersion(
  taskId: string,
  briefVersionId: string,
  approvedBy: string
): Promise<BriefVersionResponse> {
  return request<BriefVersionResponse>(
    `/api/v1/listing/${taskId}/briefs/${briefVersionId}/approve`,
    {
      method: 'POST',
      body: JSON.stringify({ approvedBy })
    }
  );
}

export async function generateTextVersion(taskId: string): Promise<TextVersionResponse> {
  return request<TextVersionResponse>(`/api/v1/listing/${taskId}/versions/text/generate`, {
    method: 'POST'
  });
}

export async function listTextVersions(taskId: string): Promise<TextVersionResponse[]> {
  return request<TextVersionResponse[]>(`/api/v1/listing/${taskId}/versions/text`);
}

export async function generateImageVersion(taskId: string): Promise<ImageVersionResponse> {
  return request<ImageVersionResponse>(`/api/v1/listing/${taskId}/versions/image/generate`, {
    method: 'POST'
  });
}

export async function listImageVersions(taskId: string): Promise<ImageVersionResponse[]> {
  return request<ImageVersionResponse[]>(`/api/v1/listing/${taskId}/versions/image`);
}

export async function listImageAssets(
  taskId: string,
  imageVersionId: string
): Promise<ImageAssetResponse[]> {
  return request<ImageAssetResponse[]>(
    `/api/v1/listing/${taskId}/versions/image/${imageVersionId}/assets`
  );
}

export async function approveFinalSelection(
  taskId: string,
  selectedTextVersionId: string,
  selectedImageVersionId: string
): Promise<FinalSelectionResponse> {
  return request<FinalSelectionResponse>(`/api/v1/listing/${taskId}/final/approve`, {
    method: 'POST',
    body: JSON.stringify({
      selectedTextVersionId,
      selectedImageVersionId
    })
  });
}

export async function approveImageAssetCompliance(
  taskId: string,
  imageVersionId: string,
  assetId: string,
  reviewedBy: string,
  reason: string
): Promise<ImageAssetComplianceReviewResponse> {
  return request<ImageAssetComplianceReviewResponse>(
    `/api/v1/listing/${taskId}/versions/image/${imageVersionId}/assets/${assetId}/compliance/approve`,
    {
      method: 'POST',
      headers: {
        'X-Operator-Id': reviewedBy
      },
      body: JSON.stringify({ reviewedBy, reason })
    }
  );
}

export async function listCompetitors(taskId: string): Promise<CompetitorSnapshotResponse[]> {
  return request<CompetitorSnapshotResponse[]>(`/api/v1/listing/${taskId}/competitors`);
}

export async function listLatestCompetitors(taskId: string): Promise<CompetitorSnapshotResponse[]> {
  return request<CompetitorSnapshotResponse[]>(`/api/v1/listing/${taskId}/competitors/latest`);
}

export async function submitManualCompetitors(
  taskId: string,
  createdBy: string,
  snapshots: ManualCompetitorSnapshotInput[]
): Promise<CompetitorSnapshotResponse[]> {
  return request<CompetitorSnapshotResponse[]>(`/api/v1/listing/${taskId}/competitors/manual`, {
    method: 'POST',
    body: JSON.stringify({ createdBy, snapshots })
  });
}

export async function confirmWarningImageAssetCompliance(
  taskId: string,
  imageVersionId: string,
  assetId: string,
  reviewedBy: string,
  reason: string
): Promise<ImageAssetComplianceReviewResponse> {
  return request<ImageAssetComplianceReviewResponse>(
    `/api/v1/listing/${taskId}/versions/image/${imageVersionId}/assets/${assetId}/compliance/confirm-warning`,
    {
      method: 'POST',
      headers: {
        'X-Operator-Id': reviewedBy
      },
      body: JSON.stringify({ reviewedBy, reason })
    }
  );
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

export async function listAuditLogs(params: AuditLogListParams): Promise<PagedResponse<OperationAuditLogResponse>> {
  return request<PagedResponse<OperationAuditLogResponse>>(
    `/api/v1/listing/audit-logs${query({
      taskId: params.taskId,
      action: params.action,
      operatorId: params.operatorId,
      targetType: params.targetType,
      targetId: params.targetId,
      page: params.page,
      size: params.size
    })}`
  );
}
