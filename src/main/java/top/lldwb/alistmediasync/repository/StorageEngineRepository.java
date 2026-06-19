package top.lldwb.alistmediasync.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.lldwb.alistmediasync.entity.StorageEngine;

/**
 * 存储引擎 Repository
 *
 * @author AList-Media-Sync
 */
@Repository
public interface StorageEngineRepository extends JpaRepository<StorageEngine, Long> {
}
