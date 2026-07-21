package com.lingxi.workbench.admin.knowledge.service.impl;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentStatus;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeJobStatus;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeDocumentEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeIngestionJobEntity;
import com.lingxi.workbench.admin.knowledge.model.bo.KnowledgeChunkBO;
import com.lingxi.workbench.admin.knowledge.config.KnowledgeProperties;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeDocumentRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeIngestionJobRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeObjectStorage;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeDocumentParser;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeDocumentChunker;
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

/** 文档解析任务执行器，长耗时解析不占用数据库事务。 */
@Service
@RequiredArgsConstructor
public class KnowledgeParseService {
    private final KnowledgeIngestionJobRepo jobRepo;
    private final KnowledgeDocumentRepo documentRepo;
    private final KnowledgeObjectStorage objectStorage;
    private final KnowledgeDocumentParser parser;
    private final KnowledgeDocumentChunker chunker;
    private final KnowledgeProperties properties;

    @Transactional
    public ParseInput start(Long jobId) {
        OffsetDateTime startedAt = OffsetDateTime.now();
        if (!jobRepo.claim(jobId, startedAt)) return null;
        KnowledgeIngestionJobEntity job = requireJob(jobId);
        KnowledgeDocumentEntity document = requireDocument(job.getDocumentId());
        document.setStatus(KnowledgeDocumentStatus.PARSING);
        document.setErrorCode(null);
        document.setErrorMessage(null);
        documentRepo.save(document);
        return new ParseInput(job.getId(), document.getId(), document.getObjectKey(), document.getFileName());
    }

    /** 查询等待任务，并恢复超过30分钟仍未结束的遗留任务。 */
    @Transactional
    public List<Long> recoverableJobIds() {
        jobRepo.resetStale(OffsetDateTime.now().minusMinutes(30));
        return jobRepo.findPendingIds();
    }

    public ProcessingResult parse(ParseInput input) throws Exception {
        try (InputStream stream = objectStorage.get(input.objectKey())) {
            KnowledgeDocumentParser.ParsedDocument parsedDocument = parser.parse(stream, input.fileName());
            KnowledgeProperties.Chunking policy = properties.chunking();
            List<KnowledgeChunkBO> chunks = chunker.chunk(
                    parsedDocument.content(), policy.strategy(), policy.chunkSize(), policy.chunkOverlap());
            return new ProcessingResult(parsedDocument, chunks);
        }
    }

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
        // TODO Chunk落库：持久化前先删除该文档旧Chunk，再批量写入result.chunks()，保证重试幂等。
        job.setStatus(KnowledgeJobStatus.SUCCEEDED);
        job.setProgress(100);
        job.setFinishedAt(OffsetDateTime.now());
        jobRepo.save(job);
    }

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

    private KnowledgeIngestionJobEntity requireJob(Long id) {
        return jobRepo.findById(id).orElseThrow(() -> new IllegalStateException("文档解析任务不存在：" + id));
    }

    private KnowledgeDocumentEntity requireDocument(Long id) {
        return documentRepo.findById(id).orElseThrow(() -> new IllegalStateException("知识库文档不存在：" + id));
    }

    private static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "文档解析失败";
        }
        String sanitized = message.replaceAll("[\\r\\n\\t]+", " ").trim();
        return sanitized.length() > 500 ? sanitized.substring(0, 500) : sanitized;
    }

    /** 解析阶段所需的不可变输入。 */
    public record ParseInput(Long jobId, Long documentId, String objectKey, String fileName) {}

    /** 当前处理结果同时包含标准化正文和内存Chunk，Chunk持久化将在后续阶段接入。 */
    public record ProcessingResult(
            KnowledgeDocumentParser.ParsedDocument parsedDocument,
            List<KnowledgeChunkBO> chunks) {
    }
}
