package com.fashionvista.backend.repository;

import com.fashionvista.backend.entity.SapoSyncStatus;
import com.fashionvista.backend.entity.Voucher;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long>, JpaSpecificationExecutor<Voucher> {

    Optional<Voucher> findByCodeIgnoreCase(String code);

    List<Voucher> findByActiveTrueAndSapoSyncStatusNot(SapoSyncStatus sapoSyncStatus);

    List<Voucher> findByActiveTrueAndSapoSyncStatus(SapoSyncStatus sapoSyncStatus);
}


