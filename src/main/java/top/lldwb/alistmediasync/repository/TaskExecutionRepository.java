package top.lldwb.alistmediasync.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import top.lldwb.alistmediasync.entity.TaskExecution;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务执行记录 Repository
 *
 * @author AList-Media-Sync
 */
@Repository
public interface TaskExecutionRepository extends JpaRepository<TaskExecution, Long> {

    /** 按状态和任务类型查询 */
    List<TaskExecution> findByStatusAndTaskType(TaskExecution.ExecutionStatus status, TaskExecution.TaskType taskType);

    /** 按同步任务和状态查询 */
    List<TaskExecution> findBySyncTaskIdAndStatus(Long syncTaskId, TaskExecution.ExecutionStatus status);

    /** 查询同步任务的所有执行记录（按时间倒序） */
    List<TaskExecution> findBySyncTaskIdOrderByStartTimeDesc(Long syncTaskId);

    /** 按时间范围过滤 */
    List<TaskExecution> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /** 删除过期记录 */
    @Modifying
    @Transactional
    @Query("DELETE FROM TaskExecution e WHERE e.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);

    /** 批量更新运行中状态为中断 */
    @Modifying
    @Transactional
    @Query("UPDATE TaskExecution e SET e.status = 'INTERRUPTED' WHERE e.status = 'RUNNING'")
    int markAllRunningAsInterrupted();
}
