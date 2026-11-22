BEGIN;

-- 默认拒绝未配置范围的知识库；部署后由管理端明确授权，历史 AgentVersion 同样读取实时授权。
ALTER TABLE lingxi_core.ai_knowledge_base
    ADD COLUMN access_mode VARCHAR(32) NOT NULL DEFAULT 'RESTRICTED';
CREATE TABLE lingxi_core.ai_knowledge_base_access (
    knowledge_base_id BIGINT NOT NULL,
    subject_type VARCHAR(16) NOT NULL,
    subject_id BIGINT NOT NULL,
    PRIMARY KEY (knowledge_base_id, subject_type, subject_id)
);
CREATE INDEX idx_knowledge_access_subject
    ON lingxi_core.ai_knowledge_base_access (subject_type, subject_id, knowledge_base_id);
COMMENT ON COLUMN lingxi_core.ai_knowledge_base.access_mode IS 'RAG可读范围：AUTHENTICATED或RESTRICTED；实时生效，不进入发布快照';
COMMENT ON TABLE lingxi_core.ai_knowledge_base_access IS '知识库RAG读取授权，USER/ROLE/DEPARTMENT按并集授权，部门只包含直属成员；逻辑关联不建物理外键';

COMMIT;
