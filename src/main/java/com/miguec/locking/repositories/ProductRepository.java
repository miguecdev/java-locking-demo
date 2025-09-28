package com.miguec.locking.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.miguec.locking.domain.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
}
