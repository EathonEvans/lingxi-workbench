package com.lingxi.workbench.admin.knowledge.service.impl;

import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentSourceType;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentStatus;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeJobStatus;
import com.lingxi.workbench.admin.knowledge.config.KnowledgeProperties;
import com.lingxi.workbench.admin.knowledge.model.converter.KnowledgeConverter;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeDocumentEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeIngestionJobEntity;
import com.lingxi.workbench.admin.knowledge.model.request.CreateTextDocumentRequest;
import com.lingxi.workbench.admin.knowledge.model.request.KnowledgeDocumentPageQuery;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeDocumentContentVO;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeDocumentVO;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeBaseRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeDocumentRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeIngestionJobRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeObjectStorage;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeDocumentService;
import com.lingxi.workbench.admin.knowledge.service.event.KnowledgeObjectDeleteRequestedEvent;
import com.lingxi.workbench.admin.knowledge.service.event.KnowledgeObjectStoredEvent;
import com.lingxi.workbench.admin.knowledge.service.event.KnowledgeParseRequestedEvent;
import com.lingxi.workbench.framework.audit.AuditService;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.convention.PageResult;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** 知识库文档上传、文本录入和解析任务管理。 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "md", "markdown", "pdf", "docx");

    private final KnowledgeBaseRepo baseRepo;
    private final KnowledgeDocumentRepo documentRepo;
    private final KnowledgeIngestionJobRepo jobRepo;
    private final KnowledgeObjectStorage objectStorage;
    private final KnowledgeProperties properties;
    private final KnowledgeConverter converter;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public PageResult<KnowledgeDocumentVO> page(KnowledgeDocumentPageQuery query) {
        requireBase(query.getKnowledgeBaseId());
        return documentRepo.findPage(query).map(converter::toDocumentVO);
    }

    @Override
    @Transactional
    public KnowledgeDocumentVO upload(Long knowledgeBaseId, MultipartFile file, Long operatorId) {
        requireBase(knowledgeBaseId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "请选择需要上传的文档");
        }
        if (file.getSize() > properties.parsing().maxFileSize()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "文档大小超过限制");
        }
        String fileName = normalizeFileName(file.getOriginalFilename());
        validateFileName(fileName);
        String extension = extension(fileName);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "仅支持TXT、Markdown、PDF和DOCX文档");
        }
        try {
            byte[] content = file.getBytes();
            String contentHash = sha256(content);
            rejectDuplicate(knowledgeBaseId, contentHash);
            String objectKey = buildObjectKey(knowledgeBaseId, extension);
            String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
            objectStorage.put(objectKey, content, contentType);
            eventPublisher.publishEvent(new KnowledgeObjectStoredEvent(objectKey));
            KnowledgeDocumentEntity entity = newDocument(knowledgeBaseId, operatorId);
            entity.setName(fileName);
            entity.setSourceType(KnowledgeDocumentSourceType.FILE);
            entity.setFileName(fileName);
            entity.setFileType(extension.toUpperCase(Locale.ROOT));
            entity.setFileSize(file.getSize());
            entity.setObjectKey(objectKey);
            entity.setContentHash(contentHash);
            documentRepo.save(entity);
            createJobAndPublish(entity, operatorId, 0);
            auditService.record("KNOWLEDGE", "DOCUMENT", entity.getId(), operatorId,
                    "DOCUMENT_UPLOAD", "文档=" + fileName);
            return converter.toDocumentVO(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "该知识库中已存在内容相同的文档");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("知识库文档上传失败，knowledgeBaseId={}，fileName={}", knowledgeBaseId, fileName, exception);
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "文档上传失败");
        }
    }

    @Override
    @Transactional
    public KnowledgeDocumentVO createText(CreateTextDocumentRequest request, Long operatorId) {
        requireBase(request.knowledgeBaseId());
        byte[] content = request.content().getBytes(StandardCharsets.UTF_8);
        String contentHash = sha256(content);
        rejectDuplicate(request.knowledgeBaseId(), contentHash);
        String objectKey = buildObjectKey(request.knowledgeBaseId(), "txt");
        try {
            objectStorage.put(objectKey, content, "text/plain; charset=UTF-8");
            eventPublisher.publishEvent(new KnowledgeObjectStoredEvent(objectKey));
            KnowledgeDocumentEntity entity = newDocument(request.knowledgeBaseId(), operatorId);
            entity.setName(request.name().trim());
            entity.setSourceType(KnowledgeDocumentSourceType.TEXT);
            entity.setFileName(request.name().trim() + ".txt");
            entity.setFileType("TXT");
            entity.setFileSize((long) content.length);
            entity.setObjectKey(objectKey);
            entity.setContentHash(contentHash);
            documentRepo.save(entity);
            createJobAndPublish(entity, operatorId, 0);
            auditService.record("KNOWLEDGE", "DOCUMENT", entity.getId(), operatorId,
                    "DOCUMENT_TEXT_CREATE", "文档=" + entity.getName());
            return converter.toDocumentVO(entity);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "该知识库中已存在内容相同的文档");
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("文本知识录入失败，knowledgeBaseId={}，name={}", request.knowledgeBaseId(), request.name(), exception);
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "文本知识录入失败");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public KnowledgeDocumentContentVO content(Long id) {
        KnowledgeDocumentEntity entity = requireDocument(id);
        if (entity.getStatus() != KnowledgeDocumentStatus.PARSED) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "文档尚未解析完成");
        }
        return new KnowledgeDocumentContentVO(String.valueOf(entity.getId()), entity.getName(), entity.getParsedContent());
    }

    @Override
    @Transactional
    public void retry(Long id, Long operatorId) {
        KnowledgeDocumentEntity entity = requireDocument(id);
        if (entity.getStatus() != KnowledgeDocumentStatus.FAILED) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "只有解析失败的文档可以重试");
        }
        if (jobRepo.existsRunningByDocumentId(id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "文档已有解析任务正在执行");
        }
        entity.setStatus(KnowledgeDocumentStatus.UPLOADED);
        entity.setErrorCode(null);
        entity.setErrorMessage(null);
        entity.setUpdatedBy(operatorId);
        documentRepo.save(entity);
        try {
            createJobAndPublish(entity, operatorId, 1);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "文档已有解析任务正在执行");
        }
        auditService.record("KNOWLEDGE", "DOCUMENT", id, operatorId, "DOCUMENT_PARSE_RETRY", "重新解析文档");
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        KnowledgeDocumentEntity entity = requireDocument(id);
        if (entity.getStatus() == KnowledgeDocumentStatus.PARSING || jobRepo.existsRunningByDocumentId(id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "文档正在解析，不能删除");
        }
        documentRepo.delete(id);
        auditService.record("KNOWLEDGE", "DOCUMENT", id, operatorId, "DOCUMENT_DELETE", "文档=" + entity.getName());
        eventPublisher.publishEvent(new KnowledgeObjectDeleteRequestedEvent(entity.getObjectKey()));
    }

    private KnowledgeDocumentEntity newDocument(Long knowledgeBaseId, Long operatorId) {
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setKnowledgeBaseId(knowledgeBaseId);
        entity.setStatus(KnowledgeDocumentStatus.UPLOADED);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        return entity;
    }

    private void createJobAndPublish(KnowledgeDocumentEntity document, Long operatorId, int retryCount) {
        KnowledgeIngestionJobEntity job = new KnowledgeIngestionJobEntity();
        job.setKnowledgeBaseId(document.getKnowledgeBaseId());
        job.setDocumentId(document.getId());
        job.setStatus(KnowledgeJobStatus.PENDING);
        job.setProgress(0);
        job.setRetryCount(retryCount);
        job.setCreatedBy(operatorId);
        job.setUpdatedBy(operatorId);
        jobRepo.save(job);
        eventPublisher.publishEvent(new KnowledgeParseRequestedEvent(job.getId()));
    }

    private void rejectDuplicate(Long knowledgeBaseId, String hash) {
        if (documentRepo.existsByHash(knowledgeBaseId, hash)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "该知识库中已存在内容相同的文档");
        }
    }

    private KnowledgeBaseEntity requireBase(Long id) {
        return baseRepo.findById(id).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "知识库不存在"));
    }

    private KnowledgeDocumentEntity requireDocument(Long id) {
        return documentRepo.findById(id).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "知识库文档不存在"));
    }

    private static String normalizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) return "未命名文档";
        String normalized = originalFilename.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1).trim();
    }

    private static void validateFileName(String fileName) {
        if (fileName.isBlank()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "文档文件名不能为空");
        }
        if (fileName.length() > 128) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "文档文件名不能超过128个字符");
        }
    }

    private static String extension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex < 0 ? "" : fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private static String buildObjectKey(Long knowledgeBaseId, String extension) {
        return "knowledge/" + knowledgeBaseId + "/" + UUID.randomUUID() + "." + extension;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前JDK不支持SHA-256", exception);
        }
    }
}
