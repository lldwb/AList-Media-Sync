package top.lldwb.alistmediasync.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import top.lldwb.alistmediasync.entity.TranscodeTask;

import java.util.List;

/**
 * 转码任务 Repository
 *
 * @author AList-Media-Sync
 */
@Repository
public interface TranscodeTaskRepository extends JpaRepository<TranscodeTask, Long> {

    /** 按转码状态查询 */
    List<TranscodeTask> findByStatus(TranscodeTask.TranscodeStatus status);

    /** 按同步任务查询关联的转码任务 */
    List<TranscodeTask> findBySyncTaskId(Long syncTaskId);
}
