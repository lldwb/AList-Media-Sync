package top.lldwb.alistmediasync.transcode.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import top.lldwb.alistmediasync.transcode.entity.TranscodeTask;

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

    /** 按状态列表批量查询 */
    List<TranscodeTask> findByStatusIn(List<TranscodeTask.TranscodeStatus> statuses);

    /** 按状态列表批量删除 */
    @Modifying
    @Transactional
    @Query("DELETE FROM TranscodeTask t WHERE t.status IN :statuses")
    int deleteByStatusIn(@Param("statuses") List<TranscodeTask.TranscodeStatus> statuses);

    /** 按状态列表计数 */
    @Query("SELECT COUNT(t) FROM TranscodeTask t WHERE t.status IN :statuses")
    long countByStatusIn(@Param("statuses") List<TranscodeTask.TranscodeStatus> statuses);
}
