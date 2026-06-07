export interface ApiResponse<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

export interface PagedResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export interface ListingTaskDetailResponse {
  taskId: string;
  status: string;
  textStatus: string;
  imageStatus: string;
  briefStatus: string;
  categoryCode: string;
  categoryTemplateId: string;
  marketplace: string;
  language: string;
  selectedTextVersionId?: string | null;
  selectedImageVersionId?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ListingTaskSummaryResponse {
  taskId: string;
  status: string;
  textStatus: string;
  imageStatus: string;
  briefStatus: string;
  categoryCode: string;
  categoryTemplateId: string;
  marketplace: string;
  language: string;
  selectedTextVersionId?: string | null;
  selectedImageVersionId?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface SubmitListingTaskResponse {
  taskId: string;
}

export interface ExportPackageResponse {
  exportPackageId: string;
  taskId: string;
  selectedTextVersionId: string;
  selectedImageVersionId: string;
  format: string;
  status: string;
  fileUrl?: string | null;
  manifestUrl?: string | null;
  failureReason?: string | null;
  canceledBy?: string | null;
  cancelReason?: string | null;
  canceledAt?: string | null;
  includedAssetIds: string[];
  createdAt: string;
  startedAt?: string | null;
  updatedAt: string;
}

export interface OperationAuditLogResponse {
  auditLogId: string;
  action: string;
  operatorId: string;
  targetType: string;
  targetId: string;
  taskId?: string | null;
  reason?: string | null;
  detailJson?: string | null;
  createdAt: string;
}
