export const taskStatusOptions = [
  { label: '全部状态', value: '' },
  { label: 'WAIT_BRIEF_APPROVE', value: 'WAIT_BRIEF_APPROVE' },
  { label: 'GENERATING', value: 'GENERATING' },
  { label: 'WAIT_FINAL_APPROVE', value: 'WAIT_FINAL_APPROVE' },
  { label: 'COMPLETED', value: 'COMPLETED' },
  { label: 'FAILED', value: 'FAILED' },
  { label: 'CANCELLED', value: 'CANCELLED' }
];

export const marketplaceOptions = [
  { label: '全部站点', value: '' },
  { label: 'US', value: 'US' },
  { label: 'UK', value: 'UK' }
];

export const categoryOptions = [
  { label: '全部类目', value: '' },
  { label: 'CAR_STEREO', value: 'CAR_STEREO' }
];

export const exportFormatOptions = [
  { label: '全部格式', value: '' },
  { label: 'ZIP', value: 'ZIP' },
  { label: 'Markdown', value: 'MARKDOWN' },
  { label: 'Excel', value: 'EXCEL' },
  { label: 'Word', value: 'WORD' }
];

export const exportStatusOptions = [
  { label: '全部状态', value: '' },
  { label: 'PENDING', value: 'PENDING' },
  { label: 'RUNNING', value: 'RUNNING' },
  { label: 'SUCCEEDED', value: 'SUCCEEDED' },
  { label: 'FAILED', value: 'FAILED' },
  { label: 'CANCELED', value: 'CANCELED' }
];

export const auditActionOptions = [
  { label: '全部动作', value: '' },
  { label: '导出取消', value: 'EXPORT_PACKAGE_CANCELED' },
  { label: 'FAIL 合规豁免', value: 'IMAGE_ASSET_COMPLIANCE_APPROVED' },
  { label: 'WARNING 人工确认', value: 'IMAGE_ASSET_WARNING_CONFIRMED' }
];

export const auditTargetTypeOptions = [
  { label: '全部目标', value: '' },
  { label: 'EXPORT_PACKAGE', value: 'EXPORT_PACKAGE' },
  { label: 'IMAGE_ASSET', value: 'IMAGE_ASSET' }
];
