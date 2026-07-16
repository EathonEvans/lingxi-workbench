package com.lingxi.workbench.framework.audit;

import com.lingxi.workbench.framework.audit.mapper.AuditLogMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuditServiceTest {

    @Test
    void shouldPassCompleteBusinessSemanticsToMapper() {
        CapturingAuditLogMapper mapper = new CapturingAuditLogMapper();
        AuditService service = new AuditService(mapper);

        service.record("MODEL", "PROVIDER", 11L, 1L, "PROVIDER_UPDATE", "供应商=deepseek");

        assertEquals("MODEL", mapper.businessType);
        assertEquals("PROVIDER", mapper.targetType);
        assertEquals(11L, mapper.targetId);
        assertEquals(1L, mapper.operatorId);
        assertEquals("PROVIDER_UPDATE", mapper.action);
        assertEquals("供应商=deepseek", mapper.summary);
    }

    /** 测试专用 Mapper，用于确认统一审计服务不会丢失业务语义。 */
    private static final class CapturingAuditLogMapper implements AuditLogMapper {
        private String businessType;
        private String targetType;
        private Long targetId;
        private Long operatorId;
        private String action;
        private String summary;

        @Override
        public int insert(
                String businessType,
                String targetType,
                Long targetId,
                Long operatorId,
                String action,
                String summary) {
            this.businessType = businessType;
            this.targetType = targetType;
            this.targetId = targetId;
            this.operatorId = operatorId;
            this.action = action;
            this.summary = summary;
            return 1;
        }
    }
}
