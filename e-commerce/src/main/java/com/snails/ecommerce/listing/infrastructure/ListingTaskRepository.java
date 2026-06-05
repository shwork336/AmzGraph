package com.snails.ecommerce.listing.infrastructure;

import com.snails.ecommerce.listing.domain.ListingTask;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Listing 任务仓储。
 */
public interface ListingTaskRepository extends JpaRepository<ListingTask, String> {
}
