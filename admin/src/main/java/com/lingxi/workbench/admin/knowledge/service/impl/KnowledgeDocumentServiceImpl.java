package com.lingxi.workbench.admin.knowledge.service.impl;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentSourceType;
import com.lingxi.workbench.admin.knowledge.common.enums.KnowledgeDocumentStatus;
import com.lingxi.workbench.admin.knowledge.config.KnowledgeProperties;
import com.lingxi.workbench.admin.knowledge.model.converter.KnowledgeConverter;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeBaseEntity;
import com.lingxi.workbench.admin.knowledge.model.entity.KnowledgeDocumentEntity;
import com.lingxi.workbench.admin.knowledge.model.request.CreateTextDocumentRequest;
import com.lingxi.workbench.admin.knowledge.model.request.KnowledgeDocumentPageQuery;
import com.lingxi.workbench.admin.knowledge.model.vo.KnowledgeDocumentVO;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeBaseRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeChunkRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeChunkTaskRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeChunkVectorRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeDocumentRepo;
import com.lingxi.workbench.admin.knowledge.repo.KnowledgeObjectStorage;
import com.lingxi.workbench.admin.knowledge.service.KnowledgeDocumentService;
import com.lingxi.workbench.admin.knowledge.event.event.KnowledgeObjectDeleteRequestedEvent;
import com.lingxi.workbench.admin.knowledge.event.event.KnowledgeObjectStoredEvent;
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
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 知识库文档上传与元数据管理。
 *
 * <p>上传阶段只持久化原始文件和文档记录，不执行解析、分块或向量化。
 * 用户确认文档信息后，由分块接口统一触发完整异步处理链。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class KnowledgeDocumentServiceImpl implements KnowledgeDocumentService {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("txt", "md", "markdown", "pdf", "docx");
    private static final Duration PREVIEW_URL_TTL = Duration.ofMinutes(15);

    private final KnowledgeBaseRepo baseRepo;
    private final KnowledgeDocumentRepo documentRepo;
    private final KnowledgeChunkTaskRepo taskRepo;
    private final KnowledgeChunkRepo chunkRepo;
    private final KnowledgeChunkVectorRepo vectorRepo;
    private final KnowledgeObjectStorage objectStorage;
    private final KnowledgeProperties properties;
    private final KnowledgeConverter converter;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public PageResult<KnowledgeDocumentVO> page(KnowledgeDocumentPageQuery query) {
        // 先校验知识库存在
        requireBase(query.getKnowledgeBaseId());
        return documentRepo.findPage(query).map(converter::toDocumentVO);
    }

    @Override
    @Transactional
    public KnowledgeDocumentVO upload(Long knowledgeBaseId, MultipartFile file, Long operatorId) {
        requireWritableBase(knowledgeBaseId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "请选择需要上传的文档");
        }
        if (file.getSize() > properties.parsing().maxFileSize()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "文档大小超过限制");
        }
        // 文件名安全处理和格式校验
        String fileName = normalizeFileName(file.getOriginalFilename());
        validateFileName(fileName);
        String extension = extension(fileName);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "仅支持TXT、Markdown、PDF和DOCX文档");
        }
        try {
            // 计算文件SHA-256哈希用于去重
            byte[] content = file.getBytes();
            String contentHash = sha256(content);
            rejectDuplicate(knowledgeBaseId, contentHash);
            // 上传原始文件到对象存储，发布存储成功事件用于事务补偿
            String objectKey = buildObjectKey(knowledgeBaseId, extension);
            String contentType = resolveContentType(extension);
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
            documentRepo.upsert(entity);
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
        requireWritableBase(request.knowledgeBaseId());
        // 文本录入以UTF-8格式编码，统一按txt扩展名存储到对象存储
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
            documentRepo.upsert(entity);
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
    public String previewUrl(Long id) {
        KnowledgeDocumentEntity document = requireDocument(id);
        return objectStorage.presignedGetUrl(
                document.getObjectKey(),
                PREVIEW_URL_TTL,
                resolveContentType(document.getFileType()));
    }

    @Override
    @Transactional
    public void delete(Long id, Long operatorId) {
        KnowledgeDocumentEntity entity = requireDocument(id);
        if (entity.getStatus() == KnowledgeDocumentStatus.RUNNING || taskRepo.existsActive(id)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "文档正在处理，不能删除");
        }
        // 仅使用逻辑外键，必须按派生数据到主数据的顺序显式清理。
        vectorRepo.deleteByDocumentId(id);
        chunkRepo.deleteByDocumentId(id);
        taskRepo.deleteByDocumentId(id);
        documentRepo.delete(id);
        auditService.record("KNOWLEDGE", "DOCUMENT", id, operatorId, "DOCUMENT_DELETE", "文档=" + entity.getName());
        // 事务提交后异步清理对象存储中的原始文件
        eventPublisher.publishEvent(new KnowledgeObjectDeleteRequestedEvent(entity.getObjectKey()));
    }

    /**
     * 创建文档实体并设置初始状态为待处理。
     */
    private KnowledgeDocumentEntity newDocument(Long knowledgeBaseId, Long operatorId) {
        KnowledgeDocumentEntity entity = new KnowledgeDocumentEntity();
        entity.setKnowledgeBaseId(knowledgeBaseId);
        entity.setStatus(KnowledgeDocumentStatus.PENDING);
        entity.setChunkCount(0);
        entity.setCreatedBy(operatorId);
        entity.setUpdatedBy(operatorId);
        return entity;
    }

    /**
     * 检查内容哈希是否重复，已存在相同内容的文档则拒绝。
     */
    private void rejectDuplicate(Long knowledgeBaseId, String hash) {
        if (documentRepo.existsByHash(knowledgeBaseId, hash)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "该知识库中已存在内容相同的文档");
        }
    }

    /**
     * 按ID加载知识库，不存在时抛出业务异常。
     */
    private KnowledgeBaseEntity requireBase(Long id) {
        return baseRepo.findById(id).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "知识库不存在"));
    }

    /**
     * 校验知识库可写，停用知识库只允许查看已有数据。
     */
    private KnowledgeBaseEntity requireWritableBase(Long id) {
        KnowledgeBaseEntity knowledgeBase = requireBase(id);
        if (knowledgeBase.getStatus() != EnableStatus.ACTIVE) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "知识库已停用，不能新增文档");
        }
        return knowledgeBase;
    }

    /**
     * 按ID加载文档，不存在时抛出业务异常。
     */
    private KnowledgeDocumentEntity requireDocument(Long id) {
        return documentRepo.findById(id).orElseThrow(() -> new BusinessException(HttpStatusEnum.NOT_FOUND, "知识库文档不存在"));
    }

    /**
     * 根据文件扩展名确定稳定的响应类型。
     *
     * <p>文本类型必须显式声明UTF-8，否则对象存储直出时浏览器可能按本地编码解析中文。</p>
     */
    private static String resolveContentType(String fileType) {
        if (fileType == null) {
            return "application/octet-stream";
        }
        return switch (fileType.toLowerCase(Locale.ROOT)) {
            case "txt" -> "text/plain; charset=UTF-8";
            case "md", "markdown" -> "text/markdown; charset=UTF-8";
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> "application/octet-stream";
        };
    }

    /**
     * 从上传文件名中提取安全文件名，去除路径信息。
     */
    private static String normalizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) return "未命名文档";
        String normalized = originalFilename.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1).trim();
    }

    /**
     * 校验文件名非空且在长度限制内。
     */
    private static void validateFileName(String fileName) {
        if (fileName.isBlank()) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "文档文件名不能为空");
        }
        if (fileName.length() > 128) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "文档文件名不能超过128个字符");
        }
    }

    /**
     * 从文件名提取小写扩展名。
     */
    private static String extension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex < 0 ? "" : fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 构建对象存储路径：knowledge/{知识库ID}/{UUID}.{扩展名}。
     */
    private static String buildObjectKey(Long knowledgeBaseId, String extension) {
        return "knowledge/" + knowledgeBaseId + "/" + UUID.randomUUID() + "." + extension;
    }

    /**
     * 计算内容的SHA-256摘要，用于知识库内的文档去重。
     */
    private static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持SHA-256算法", exception);
        }
    }

}
