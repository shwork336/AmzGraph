import { reactive } from 'vue';
import type { PagedResponse } from '../api/types';

export interface PaginationState {
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export function createPaginationState(size = 10): PaginationState {
  return reactive({
    page: 1,
    size,
    totalItems: 0,
    totalPages: 0
  });
}

export function requestPage(page: number): number {
  return page - 1;
}

export function resetPaginationPage(pagination: PaginationState): void {
  pagination.page = 1;
}

export function syncPagination<T>(pagination: PaginationState, page: PagedResponse<T>): void {
  pagination.totalItems = page.totalItems;
  pagination.totalPages = page.totalPages;
}
