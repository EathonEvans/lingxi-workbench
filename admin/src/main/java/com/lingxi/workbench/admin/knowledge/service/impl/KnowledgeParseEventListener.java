package com.lingxi.workbench.admin.knowledge.service.impl;

import com.lingxi.workbench.admin.knowledge.service.event.KnowledgeParseRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 在上传事务提交后异步执行文档解析。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeParseEventListener {
    private final KnowledgeParseService parseService;

    @Async("knowledgeTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(KnowledgeParseRequestedEvent event) {
        process(event.jobId());
    }

    /**
     * 定时恢复未被事件线程执行或因服务退出而遗留的解析任务。
     */
    @Scheduled(fixedDelayString = "${lingxi.knowledge.parsing.recovery-interval-millis:30000}")
    public void recover() {
        for (Long jobId : parseService.recoverableJobIds()) {
            process(jobId);
        }
    }

    /**
     * 执行单个解析任务：抢占任务 -> 解析文档 -> 标记完成或失败。
     */
    private void process(Long jobId) {
        KnowledgeParseService.ParseInput input;
        try {
            input = parseService.start(jobId);
        } catch (RuntimeException exception) {
            log.warn("知识库文档解析任务启动失败，jobId={}", jobId, exception);
            return;
        }
        // claim失败返回null，任务已被其他线程抢占
        if (input == null) return;
        try {
            KnowledgeParseService.ProcessingResult result = parseService.parse(input);
            parseService.complete(input, result);
        } catch (Exception exception) {
            log.warn("知识库文档解析失败，documentId={}", input.documentId(), exception);
            try {
                parseService.fail(input, exception);
            } catch (RuntimeException persistenceException) {
                log.error("知识库文档解析失败状态保存失败，jobId={}", input.jobId(), persistenceException);
            }
        }
    }
}
