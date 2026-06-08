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
  originalProductUrls: string[];
  competitorAsins: string[];
  selectedTextVersionId?: string | null;
  selectedImageVersionId?: string | null;
  latestBrief?: BriefSummary | null;
  createdAt: string;
  updatedAt: string;
}

export interface BriefSummary {
  briefVersionId: string;
  targetAudience: string;
  approved: boolean;
}

export interface BriefVersionResponse {
  briefVersionId: string;
  taskId: string;
  parentBriefVersionId?: string | null;
  targetAudience: string;
  coreSellingPoints: string[];
  targetKeywords: string[];
  forbiddenClaims: string[];
  imageDirectionPrompts: string[];
  complianceNotes: string[];
  approved: boolean;
  createdBy: string;
  approvedBy?: string | null;
  approvedAt?: string | null;
  createdAt: string;
}

export interface TextVersionResponse {
  versionId: string;
  taskId: string;
  parentVersionId?: string | null;
  briefVersionId?: string | null;
  iterationPrompt?: string | null;
  title?: string | null;
  bulletPoints: string[];
  description?: string | null;
  backendSearchTerms?: string | null;
  targetKeywords: string[];
  complianceWarnings: string[];
  qualityScore?: number | null;
  selected: boolean;
  createdAt: string;
}

export interface ImageVersionResponse {
  versionId: string;
  taskId: string;
  parentVersionId?: string | null;
  briefVersionId?: string | null;
  iterationPrompt?: string | null;
  referenceImageUrl?: string | null;
  inputProductUrls: string[];
  imageProvider?: string | null;
  imageModel?: string | null;
  generationParams?: string | null;
  status: string;
  qualityScore?: number | null;
  selected: boolean;
  createdAt: string;
}

export interface ImageAssetResponse {
  assetId: string;
  imageVersionId: string;
  type: string;
  prompt?: string | null;
  rewrittenPrompt?: string | null;
  generatedImageUrl?: string | null;
  sourceEditableFileUrl?: string | null;
  sizeProfile?: string | null;
  targetWidth?: number | null;
  targetHeight?: number | null;
  complianceStatus?: string | null;
  complianceMethods: string[];
  complianceIssues: string[];
  complianceReviewedBy?: string | null;
  complianceReviewReason?: string | null;
  complianceReviewedAt?: string | null;
  sortOrder?: number | null;
  createdAt: string;
}

export interface FinalSelectionResponse {
  taskId: string;
  status: string;
  selectedTextVersionId: string;
  selectedImageVersionId: string;
  updatedAt: string;
}

export interface ImageAssetComplianceReviewResponse {
  assetId: string;
  imageVersionId: string;
  complianceStatus: string;
  complianceReviewedBy?: string | null;
  complianceReviewReason?: string | null;
  complianceReviewedAt?: string | null;
}

export interface CompetitorSnapshotResponse {
  snapshotId: string;
  taskId: string;
  asin: string;
  title: string;
  bulletPoints: string[];
  rating?: number | null;
  reviewCount?: number | null;
  reviewPainPoints: string[];
  keywordSignals: string[];
  sourceType: string;
  sourceName: string;
  rawResponseFileKey?: string | null;
  capturedAt: string;
  createdBy: string;
  createdAt: string;
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
