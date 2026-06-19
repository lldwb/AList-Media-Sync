package top.lldwb.alistmediasync.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.lldwb.alistmediasync.entity.SyncTask;

import java.util.List;

/**
 * 同步任务 Repository
 *
 * @author AList-Media-Sync
 */
@Repository
public interface SyncTaskRepository extends JpaRepository<SyncTask, Long> {

    /** 查询所有已启用的同步任务 */
    List<SyncTask> findByEnabledTrue();

    /** 按同步模式查询 */
    List<SyncTask> findBySyncMode(SyncTask.SyncMode syncMode);

    /** 查询目标路径相同的运行中的任务（冲突检测） */
    List<SyncTask> findByTargetPath(String targetPath);
}
