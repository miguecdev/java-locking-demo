package com.miguec.locking.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.miguec.locking.domain.ProductWithVersion;

@Repository
public interface ProductWithVersionRepository extends JpaRepository<ProductWithVersion, Long> {
}
