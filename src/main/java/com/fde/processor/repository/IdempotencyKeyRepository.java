package com.fde.processor.repository;

import com.fde.processor.model.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    Optional<IdempotencyKey> findByIdempotencyKey(String idempotencyKey);

    Optional<IdempotencyKey> findByTransactionId(String transactionId);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
