package com.lingxi.workbench.admin.knowledge.service.impl;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentStatus;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeJobStatus;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeDocumentEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeIngestionJobEntity;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeDocumentRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeIngestionJobRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeObjectStorage;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeDocumentParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * 文档解析任务执行器，长耗时解析不占用数据库事务。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeParseService {
    private final KnowledgeIngestionJobRepo jobRepo;
    private final KnowledgeDocumentRepo documentRepo;
    private final KnowledgeObjectStorage objectStorage;
    private final KnowledgeDocumentParser parser;

    /**
     * 原子抢占解析任务并标记文档为解析中；抢占失败返回null表示已被其他线程取走。
     */
    @Transactional
    public ParseInput start(Long jobId) {
        OffsetDateTime startedAt = OffsetDateTime.now();
        if (!jobRepo.claim(jobId, startedAt)) return null;
        KnowledgeIngestionJobEntity job = requireJob(jobId);
        KnowledgeDocumentEntity document = requireDocument(job.getDocumentId());
        // 清除上次失败的错误信息，标记为解析中
        document.setStatus(KnowledgeDocumentStatus.PARSING);
        document.setErrorCode(null);
        document.setErrorMessage(null);
        documentRepo.save(document);
        return new ParseInput(job.getId(), document.getId(), document.getObjectKey(), document.getFileName());
    }

    /**
     * 查询等待任务，并恢复超过30分钟仍未结束的遗留任务。
     */
    @Transactional
    public List<Long> recoverableJobIds() {
        jobRepo.resetStale(OffsetDateTime.now().minusMinutes(30));
        return jobRepo.findPendingIds();
    }

    /**
     * 执行实际解析（从对象存储读取并通过Tika提取正文），长耗时操作不持有事务。
     */
    public ProcessingResult parse(ParseInput input) throws Exception {
        try (InputStream stream = objectStorage.get(input.objectKey())) {
            KnowledgeDocumentParser.ParsedDocument parsedDocument = parser.parse(stream, input.fileName());
            return new ProcessingResult(parsedDocument);
        }
    }

    /**
     * 解析成功后更新文档正文和任务状态。
     */
    @Transactional
    public void complete(ParseInput input, ProcessingResult result) {
        KnowledgeIngestionJobEntity job = requireJob(input.jobId());
        KnowledgeDocumentEntity document = requireDocument(input.documentId());
        KnowledgeDocumentParser.ParsedDocument parsedDocument = result.parsedDocument();
        document.setParsedContent(parsedDocument.content());
        document.setParsedContentHash(sha256(parsedDocument.content()));
        document.setPageCount(parsedDocument.pageCount());
        document.setStatus(KnowledgeDocumentStatus.PARSED);
        document.setParsedAt(OffsetDateTime.now());
        documentRepo.save(document);
        job.setStatus(KnowledgeJobStatus.SUCCEEDED);
        job.setProgress(100);
        job.setFinishedAt(OffsetDateTime.now());
        jobRepo.save(job);
    }

    /**
     * 解析失败后记录错误信息并更新文档和任务状态为失败。
     */
    @Transactional
    public void fail(ParseInput input, Exception exception) {
        KnowledgeIngestionJobEntity job = requireJob(input.jobId());
        KnowledgeDocumentEntity document = requireDocument(input.documentId());
        String message = safeMessage(exception);
        document.setStatus(KnowledgeDocumentStatus.FAILED);
        document.setErrorCode("DOCUMENT_PARSE_FAILED");
        document.setErrorMessage(message);
        documentRepo.save(document);
        job.setStatus(KnowledgeJobStatus.FAILED);
        job.setErrorCode("DOCUMENT_PARSE_FAILED");
        job.setErrorMessage(message);
        job.setFinishedAt(OffsetDateTime.now());
        jobRepo.save(job);
    }

    /**
     * 按ID加载解析任务。
     */
    private KnowledgeIngestionJobEntity requireJob(Long id) {
        return jobRepo.findById(id).orElseThrow(() -> new IllegalStateException("文档解析任务不存在：" + id));
    }

    /**
     * 按ID加载文档。
     */
    private KnowledgeDocumentEntity requireDocument(Long id) {
        return documentRepo.findById(id).orElseThrow(() -> new IllegalStateException("知识库文档不存在：" + id));
    }

    /**
     * 提取安全的异常消息：去除换行和制表符，最多保留500字符。
     */
    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "文档解析失败";
        }
        String sanitized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    /**
     * 计算解析正文的SHA-256摘要，便于后续识别正文是否发生变化。
     */
    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持SHA-256算法", exception);
        }
    }

    /**
     * 解析阶段所需的不可变输入。
     */
    public record ParseInput(Long jobId, Long documentId, String objectKey, String fileName) {
    }

    /**
     * 文档解析结果。
     *
     * @param parsedDocument 标准化后的文档正文和页数
     */
    public record ProcessingResult(KnowledgeDocumentParser.ParsedDocument parsedDocument) {
    }
}
