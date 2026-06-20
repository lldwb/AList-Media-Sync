package top.lldwb.alistmediasync.sync.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import top.lldwb.alistmediasync.sync.dto.sync.SyncTaskCreateDTO;
import top.lldwb.alistmediasync.sync.dto.sync.SyncTaskUpdateDTO;
import top.lldwb.alistmediasync.sync.dto.sync.SyncTaskVO;
import top.lldwb.alistmediasync.sync.dto.sync.TaskExecutionVO;
import top.lldwb.alistmediasync.sync.entity.SyncTask;
import top.lldwb.alistmediasync.sync.entity.TaskExecution;
import top.lldwb.alistmediasync.sync.repository.SyncTaskRepository;
import top.lldwb.alistmediasync.storage.repository.StorageEngineRepository;
import top.lldwb.alistmediasync.sync.repository.TaskExecutionRepository;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * 同步任务 CRUD 管理服务
 * <p>
 * 负责同步任务的创建、查询、更新、删除和启用/禁用。
 * 手动触发时检查是否有运行中的执行记录。
 * </p>
 *
 * @author AList-Media-Sync
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncTaskManageService {

    private final SyncTaskRepository repository;
    private final StorageEngineRepository storageEngineRepository;
    private final TaskExecutionRepository taskExecutionRepository;
    private final ScheduleService scheduleService;

    /**
     * 创建同步任务
     */
    @Transactional
    public SyncTaskVO create(SyncTaskCreateDTO dto) {
        SyncTask entity = new SyncTask();
        entity.setName(dto.getName());
        entity.setSourceEngine(storageEngineRepository.getReferenceById(dto.getSourceEngineId()));
        entity.setTargetEngine(storageEngineRepository.getReferenceById(dto.getTargetEngineId()));
        entity.setSourcePath(dto.getSourcePath());
        entity.setTargetPath(dto.getTargetPath());
        entity.setSyncMode(dto.getSyncMode());
        entity.setTranscodeEnabled(dto.getTranscodeEnabled());
        entity.setTargetFormat(dto.getTargetFormat());
        entity.setConflictStrategy(dto.getConflictStrategy());
        entity.setExcludePatterns(dto.getExcludePatterns());
        entity.setScheduleType(dto.getScheduleType());
        entity.setCronExpression(dto.getCronExpression());
        entity.setIntervalSeconds(dto.getIntervalSeconds());
        entity.setEnabled(false); // 创建后默认禁用，需手动启用
        entity = repository.save(entity);
        log.info("同步任务已创建：{} ({} -> {})", entity.getName(), entity.getSourcePath(), entity.getTargetPath());
        return SyncTaskVO.from(entity);
    }

    /**
     * 更新同步任务
     */
    @Transactional
    public SyncTaskVO update(Long id, SyncTaskUpdateDTO dto) {
        SyncTask entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("同步任务不存在：id=" + id));

        if (dto.getName() != null) entity.setName(dto.getName());
        if (dto.getSourceEngineId() != null) entity.setSourceEngine(storageEngineRepository.getReferenceById(dto.getSourceEngineId()));
        if (dto.getTargetEngineId() != null) entity.setTargetEngine(storageEngineRepository.getReferenceById(dto.getTargetEngineId()));
        if (dto.getSourcePath() != null) entity.setSourcePath(dto.getSourcePath());
        if (dto.getTargetPath() != null) entity.setTargetPath(dto.getTargetPath());
        if (dto.getSyncMode() != null) entity.setSyncMode(dto.getSyncMode());
        if (dto.getTranscodeEnabled() != null) entity.setTranscodeEnabled(dto.getTranscodeEnabled());
        if (dto.getTargetFormat() != null) entity.setTargetFormat(dto.getTargetFormat());
        if (dto.getConflictStrategy() != null) entity.setConflictStrategy(dto.getConflictStrategy());
        if (dto.getExcludePatterns() != null) entity.setExcludePatterns(dto.getExcludePatterns());
        if (dto.getScheduleType() != null) entity.setScheduleType(dto.getScheduleType());
        if (dto.getCronExpression() != null) entity.setCronExpression(dto.getCronExpression());
        if (dto.getIntervalSeconds() != null) entity.setIntervalSeconds(dto.getIntervalSeconds());

        entity = repository.save(entity);
        log.info("同步任务已更新：{}", entity.getName());
        return SyncTaskVO.from(entity);
    }

    /**
     * 删除同步任务
     */
    @Transactional
    public void delete(Long id) {
        SyncTask entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("同步任务不存在：id=" + id));
        // 先取消调度
        scheduleService.unregisterSchedule(id);
        repository.delete(entity);
        log.info("同步任务已删除：{}", entity.getName());
    }

    /**
     * 查询同步任务详情
     */
    public SyncTaskVO getById(Long id) {
        return repository.findById(id)
            .map(SyncTaskVO::from)
            .orElseThrow(() -> new NoSuchElementException("同步任务不存在：id=" + id));
    }

    /**
     * 查询所有同步任务
     */
    public List<SyncTaskVO> listAll() {
        return repository.findAll().stream()
            .map(SyncTaskVO::from)
            .toList();
    }

    /**
     * 启用定时调度
     */
    @Transactional
    public SyncTaskVO enable(Long id) {
        SyncTask entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("同步任务不存在：id=" + id));
        entity.setEnabled(true);
        entity = repository.save(entity);
        scheduleService.registerSchedule(entity);
        log.info("同步任务已启用：{}", entity.getName());
        return SyncTaskVO.from(entity);
    }

    /**
     * 禁用定时调度
     */
    @Transactional
    public SyncTaskVO disable(Long id) {
        SyncTask entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("同步任务不存在：id=" + id));
        entity.setEnabled(false);
        entity = repository.save(entity);
        scheduleService.unregisterSchedule(id);
        log.info("同步任务已禁用：{}", entity.getName());
        return SyncTaskVO.from(entity);
    }

    /**
     * 手动触发同步任务
     * 检查同一任务是否有运行中的执行记录
     */
    @Transactional
    public void executeManually(Long id) {
        SyncTask entity = repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("同步任务不存在：id=" + id));

        // 检查是否有运行中的执行记录
        List<TaskExecution> running = taskExecutionRepository.findBySyncTaskIdAndStatus(
            id, TaskExecution.ExecutionStatus.RUNNING);
        if (!running.isEmpty()) {
            throw new IllegalStateException("任务正在执行中，请稍后再试");
        }

        log.info("手动触发同步任务：{}", entity.getName());
        // SyncService.executeSyncTask 会创建 TaskExecution
    }

    /**
     * 获取实体
     */
    public SyncTask getEntity(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("同步任务不存在：id=" + id));
    }

    /**
     * 查询同步任务的执行历史
     *
     * @param syncTaskId 同步任务 ID
     * @return 执行历史列表（按开始时间倒序）
     */
    public List<TaskExecutionVO> getExecutions(Long syncTaskId) {
        return taskExecutionRepository.findBySyncTaskIdOrderByStartTimeDesc(syncTaskId)
            .stream()
            .map(TaskExecutionVO::from)
            .toList();
    }
}
