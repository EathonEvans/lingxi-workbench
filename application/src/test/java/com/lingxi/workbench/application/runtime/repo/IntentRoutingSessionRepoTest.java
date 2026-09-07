package com.lingxi.workbench.application.runtime.repo;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lingxi.workbench.application.runtime.common.enums.IntentClarificationType;
import com.lingxi.workbench.application.runtime.common.enums.IntentRoutingSessionStatus;
import com.lingxi.workbench.application.runtime.model.entity.IntentRoutingSessionEntity;
import com.lingxi.workbench.application.runtime.repo.mapper.IntentRoutingSessionMapper;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 提交条件同时锁定归属与原始澄清进度，不能只凭主键更新会话。 */
class IntentRoutingSessionRepoTest {
    private final IntentRoutingSessionMapper mapper = mock(IntentRoutingSessionMapper.class);
    private final IntentRoutingSessionRepo repo = new IntentRoutingSessionRepo(mapper);

    @BeforeEach
    void initializeMapping() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), IntentRoutingSessionEntity.class);
    }

    @Test
    void shouldMatchOwnerVersionsAndOriginalProgressWhileWritingFinalState() {
        IntentRoutingSessionEntity session = session();
        when(mapper.update(eq(session), any())).thenAnswer(call -> {
            LambdaQueryWrapper<IntentRoutingSessionEntity> where = call.getArgument(1);
            assertThat(where.getSqlSegment()).contains("id =", "conversation_id =", "user_id =", "application_version_id =",
                    "intent_tree_version_id =", "status =", "clarification_type =", "round_no =");
            assertThat(where.getParamNameValuePairs().values()).containsExactlyInAnyOrder(99L, 10L, 7L, 30L, 20L,
                    IntentRoutingSessionStatus.PENDING, IntentClarificationType.INTENT_DISAMBIGUATION, 2);
            assertThat(session.getClarificationType()).isEqualTo(IntentClarificationType.MISSING_SLOT);
            assertThat(session.getRoundNo()).isEqualTo(1);
            return 1;
        });
        repo.updatePending(session, IntentClarificationType.INTENT_DISAMBIGUATION, 2);
        verify(mapper).update(eq(session), any());
    }

    @Test
    void shouldRejectStaleReplyWhenConditionalUpdateMatchesNoRows() {
        IntentRoutingSessionEntity session = session();
        assertThatThrownBy(() -> repo.updatePending(session, IntentClarificationType.INTENT_DISAMBIGUATION, 2))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已经推进");
    }

    private static IntentRoutingSessionEntity session() {
        IntentRoutingSessionEntity session = new IntentRoutingSessionEntity();
        session.setId(99L);
        session.setConversationId(10L);
        session.setUserId(7L);
        session.setApplicationVersionId(30L);
        session.setIntentTreeVersionId(20L);
        session.setStatus(IntentRoutingSessionStatus.PENDING);
        session.setClarificationType(IntentClarificationType.MISSING_SLOT);
        session.setRoundNo(1);
        return session;
    }
}
