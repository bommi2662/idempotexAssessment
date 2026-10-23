package com.fde.processor.repository;

import com.fde.processor.model.entity.AccountLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountLedgerRepository extends JpaRepository<AccountLedger, String> {

    Optional<AccountLedger> findByAccountId(String accountId);
}
