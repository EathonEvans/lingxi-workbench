package com.lingxi.workbench.admin.appmonitor.repo.mapper;

import com.lingxi.workbench.admin.appmonitor.model.bo.AppIssueBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppHealthBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppMonitorSummaryBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppStageMetricBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppTraceBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppTraceEventBO;
import com.lingxi.workbench.admin.appmonitor.model.bo.AppTrendPointBO;
import com.lingxi.workbench.admin.appmonitor.model.request.AppMonitorQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Application 观测事件聚合查询。 */
@Mapper
public interface AppMonitorMapper {

    @Select("""
            WITH terminal AS (
                SELECT DISTINCT ON (trace_id)
                       trace_id, application_id, status, latency_ms, created_at
                FROM lingxi_core.ai_observability_event
                WHERE event_type IN ('RUN_COMPLETED', 'RUN_FAILED')
                  AND created_at >= #{from}
                ORDER BY trace_id, created_at DESC
            )
            SELECT application.id AS application_id,
                   application.name AS application_name,
                   COUNT(terminal.trace_id) AS total_runs,
                   COUNT(terminal.trace_id) FILTER (WHERE terminal.status = 'SUCCESS') AS success_runs,
                   COUNT(terminal.trace_id) FILTER (WHERE terminal.status <> 'SUCCESS') AS failure_runs,
                   COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY terminal.latency_ms)
                            FILTER (WHERE terminal.latency_ms IS NOT NULL), 0)::BIGINT AS p95_ms,
                   MAX(terminal.created_at) AS last_run_at
            FROM lingxi_core.ai_application application
            LEFT JOIN terminal ON terminal.application_id = application.id
            WHERE application.status = 'ACTIVE'
            GROUP BY application.id, application.name
            ORDER BY CASE WHEN COUNT(terminal.trace_id) = 0 THEN 1 ELSE 0 END,
                     failure_runs DESC,
                     COUNT(terminal.trace_id) FILTER (WHERE terminal.status = 'SUCCESS')::NUMERIC
                         / NULLIF(COUNT(terminal.trace_id), 0),
                     application.name
            LIMIT 100
            """)
    List<AppHealthBO> applicationHealth(@Param("from") OffsetDateTime from);

    @Select("""
            SELECT COUNT(*) AS total_runs,
                   COUNT(*) FILTER (WHERE status = 'SUCCESS') AS success_runs,
                   COALESCE(AVG(latency_ms), 0)::BIGINT AS avg_latency_ms,
                   COALESCE(SUM(COALESCE(prompt_tokens, 0) + COALESCE(completion_tokens, 0)), 0)::BIGINT AS total_tokens
            FROM lingxi_core.ai_observability_event
            WHERE event_type IN ('RUN_COMPLETED', 'RUN_FAILED')
              AND created_at >= #{from}
              AND (#{applicationId}::BIGINT IS NULL OR application_id = #{applicationId})
            """)
    AppMonitorSummaryBO summary(@Param("applicationId") Long applicationId, @Param("from") OffsetDateTime from);

    @Select("""
            SELECT stage,
                   COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE status = 'ERROR') AS error_count,
                   COALESCE(PERCENTILE_CONT(0.95) WITHIN GROUP (ORDER BY latency_ms), 0)::BIGINT AS p95_ms
            FROM lingxi_core.ai_observability_event
            WHERE event_type = 'STAGE_COMPLETED'
              AND created_at >= #{from}
              AND (#{applicationId}::BIGINT IS NULL OR application_id = #{applicationId})
            GROUP BY stage
            ORDER BY MIN(created_at)
            """)
    List<AppStageMetricBO> stages(@Param("applicationId") Long applicationId, @Param("from") OffsetDateTime from);

    @Select("""
            SELECT date_trunc('hour', created_at) AS bucket,
                   COUNT(*) AS total,
                   COUNT(*) FILTER (WHERE status = 'SUCCESS') AS success_runs
            FROM lingxi_core.ai_observability_event
            WHERE event_type IN ('RUN_COMPLETED', 'RUN_FAILED')
              AND created_at >= #{from}
              AND (#{applicationId}::BIGINT IS NULL OR application_id = #{applicationId})
            GROUP BY bucket
            ORDER BY bucket
            """)
    List<AppTrendPointBO> trend(@Param("applicationId") Long applicationId, @Param("from") OffsetDateTime from);

    @Select("""
            SELECT COALESCE(metadata ->> 'runtimeErrorCode', metadata ->> 'errorType', 'UnknownError') AS error_type,
                   COALESCE(stage, 'EXECUTION') AS stage,
                   COUNT(*) AS count,
                   MAX(created_at) AS latest_at
            FROM lingxi_core.ai_observability_event
            WHERE status = 'ERROR'
              AND created_at >= #{from}
              AND (#{applicationId}::BIGINT IS NULL OR application_id = #{applicationId})
            GROUP BY error_type, stage
            ORDER BY count DESC, MAX(created_at) DESC
            LIMIT 5
            """)
    List<AppIssueBO> issues(@Param("applicationId") Long applicationId, @Param("from") OffsetDateTime from);

    @Select("""
            <script>
            WITH terminal AS (
                SELECT DISTINCT ON (trace_id)
                       trace_id, application_id, status, latency_ms,
                       COALESCE(prompt_tokens, 0) + COALESCE(completion_tokens, 0) AS total_tokens,
                       created_at
                FROM lingxi_core.ai_observability_event
                WHERE event_type IN ('RUN_COMPLETED', 'RUN_FAILED')
                  AND created_at &gt;= #{from}
                  AND (#{query.applicationId}::BIGINT IS NULL OR application_id = #{query.applicationId})
                ORDER BY trace_id, created_at DESC
            ), counts AS (
                SELECT trace_id,
                       COUNT(*) FILTER (WHERE event_type = 'MODEL_CALL'
                           OR (event_type = 'RUNTIME_PHASE' AND metadata ->> 'runtimePhase' = 'MODEL')) AS model_calls,
                       COUNT(*) FILTER (WHERE event_type = 'TOOL_CALL'
                           OR (event_type = 'RUNTIME_PHASE' AND metadata ->> 'runtimePhase' = 'TOOL')) AS tool_calls,
                       MAX(metadata -&gt;&gt; 'executionMode') FILTER (WHERE event_type = 'EXECUTION_COMPLETED') AS execution_mode,
                       MIN(created_at) AS started_at
                FROM lingxi_core.ai_observability_event
                WHERE created_at &gt;= #{from}
                GROUP BY trace_id
            )
            SELECT terminal.trace_id, terminal.application_id, application.name AS application_name,
                   COALESCE(counts.execution_mode, 'REACT') AS execution_mode,
                   CASE terminal.status WHEN 'SUCCESS' THEN 'SUCCESS' ELSE 'FAILED' END AS status,
                   terminal.latency_ms, COALESCE(counts.model_calls, 0) AS model_calls,
                   COALESCE(counts.tool_calls, 0) AS tool_calls, terminal.total_tokens,
                   counts.started_at
            FROM terminal
            JOIN lingxi_core.ai_application application ON application.id = terminal.application_id
            LEFT JOIN counts ON counts.trace_id = terminal.trace_id
            <where>
              <if test="query.mode != null">COALESCE(counts.execution_mode, 'REACT') = #{query.mode.code}</if>
              <if test="query.status != null">AND (CASE terminal.status WHEN 'SUCCESS' THEN 'SUCCESS' ELSE 'FAILED' END) = #{query.status.code}</if>
              <if test="query.keyword != null and query.keyword != ''">
                AND (terminal.trace_id ILIKE CONCAT('%', #{query.keyword}, '%') OR application.name ILIKE CONCAT('%', #{query.keyword}, '%'))
              </if>
              <if test="query.stage != null and query.stage != ''">
                AND EXISTS (SELECT 1 FROM lingxi_core.ai_observability_event filter_event
                            WHERE filter_event.trace_id = terminal.trace_id AND filter_event.stage = #{query.stage})
              </if>
              <if test="query.errorType != null and query.errorType != ''">
                AND EXISTS (SELECT 1 FROM lingxi_core.ai_observability_event filter_event
                            WHERE filter_event.trace_id = terminal.trace_id
                              AND COALESCE(filter_event.metadata -&gt;&gt; 'runtimeErrorCode',
                                           filter_event.metadata -&gt;&gt; 'errorType') = #{query.errorType})
              </if>
            </where>
            ORDER BY terminal.created_at DESC
            LIMIT #{query.size} OFFSET #{offset}
            </script>
            """)
    List<AppTraceBO> traces(@Param("query") AppMonitorQuery query, @Param("from") OffsetDateTime from,
                            @Param("offset") long offset);

    @Select("""
            <script>
            WITH terminal AS (
                SELECT DISTINCT ON (trace_id) trace_id, application_id, status, created_at
                FROM lingxi_core.ai_observability_event
                WHERE event_type IN ('RUN_COMPLETED', 'RUN_FAILED')
                  AND created_at &gt;= #{from}
                  AND (#{query.applicationId}::BIGINT IS NULL OR application_id = #{query.applicationId})
                ORDER BY trace_id, created_at DESC
            ), modes AS (
                SELECT trace_id, MAX(metadata -&gt;&gt; 'executionMode') AS execution_mode
                FROM lingxi_core.ai_observability_event
                WHERE event_type = 'EXECUTION_COMPLETED' AND created_at &gt;= #{from}
                GROUP BY trace_id
            )
            SELECT COUNT(*)
            FROM terminal
            JOIN lingxi_core.ai_application application ON application.id = terminal.application_id
            LEFT JOIN modes ON modes.trace_id = terminal.trace_id
            <where>
              <if test="query.mode != null">COALESCE(modes.execution_mode, 'REACT') = #{query.mode.code}</if>
              <if test="query.status != null">AND (CASE terminal.status WHEN 'SUCCESS' THEN 'SUCCESS' ELSE 'FAILED' END) = #{query.status.code}</if>
              <if test="query.keyword != null and query.keyword != ''">
                AND (terminal.trace_id ILIKE CONCAT('%', #{query.keyword}, '%') OR application.name ILIKE CONCAT('%', #{query.keyword}, '%'))
              </if>
              <if test="query.stage != null and query.stage != ''">
                AND EXISTS (SELECT 1 FROM lingxi_core.ai_observability_event filter_event
                            WHERE filter_event.trace_id = terminal.trace_id AND filter_event.stage = #{query.stage})
              </if>
              <if test="query.errorType != null and query.errorType != ''">
                AND EXISTS (SELECT 1 FROM lingxi_core.ai_observability_event filter_event
                            WHERE filter_event.trace_id = terminal.trace_id
                              AND COALESCE(filter_event.metadata -&gt;&gt; 'runtimeErrorCode',
                                           filter_event.metadata -&gt;&gt; 'errorType') = #{query.errorType})
              </if>
            </where>
            </script>
            """)
    long traceCount(@Param("query") AppMonitorQuery query, @Param("from") OffsetDateTime from);

    @Select("""
            WITH terminal AS (
                SELECT DISTINCT ON (event.trace_id)
                       event.trace_id, event.application_id, event.status, event.latency_ms,
                       COALESCE(event.prompt_tokens, 0) + COALESCE(event.completion_tokens, 0) AS total_tokens,
                       event.created_at
                FROM lingxi_core.ai_observability_event event
                WHERE event.trace_id = #{traceId}
                  AND event.event_type IN ('RUN_COMPLETED', 'RUN_FAILED')
                ORDER BY event.trace_id, event.created_at DESC
            ), counts AS (
                SELECT trace_id,
                       COUNT(*) FILTER (WHERE event_type = 'MODEL_CALL'
                           OR (event_type = 'RUNTIME_PHASE' AND metadata ->> 'runtimePhase' = 'MODEL')) AS model_calls,
                       COUNT(*) FILTER (WHERE event_type = 'TOOL_CALL'
                           OR (event_type = 'RUNTIME_PHASE' AND metadata ->> 'runtimePhase' = 'TOOL')) AS tool_calls,
                       MAX(metadata ->> 'executionMode') FILTER (WHERE event_type = 'EXECUTION_COMPLETED') AS execution_mode,
                       MIN(created_at) AS started_at
                FROM lingxi_core.ai_observability_event
                WHERE trace_id = #{traceId}
                GROUP BY trace_id
            )
            SELECT terminal.trace_id, terminal.application_id, application.name AS application_name,
                   COALESCE(counts.execution_mode, 'REACT') AS execution_mode,
                   CASE terminal.status WHEN 'SUCCESS' THEN 'SUCCESS' ELSE 'FAILED' END AS status,
                   terminal.latency_ms, COALESCE(counts.model_calls, 0) AS model_calls,
                   COALESCE(counts.tool_calls, 0) AS tool_calls, terminal.total_tokens,
                   counts.started_at
            FROM terminal
            JOIN lingxi_core.ai_application application ON application.id = terminal.application_id
            LEFT JOIN counts ON counts.trace_id = terminal.trace_id
            """)
    Optional<AppTraceBO> trace(@Param("traceId") String traceId);

    @Select("""
            SELECT event.span_id, event.parent_span_id, event.event_type, event.stage, event.status,
                   event.latency_ms, (event.metadata ->> 'modelInstanceId')::BIGINT AS model_instance_id,
                   model_instance.code AS model_instance_code, event.model_code,
                   model_provider.name AS model_provider_name,
                   (event.metadata ->> 'modelCallNo')::INTEGER AS model_call_no,
                   event.prompt_tokens, event.completion_tokens,
                   COALESCE(event.metadata ->> 'runtimeOperationName', event.metadata ->> 'toolName') AS tool_name,
                   (event.metadata ->> 'modelFirstResponseMs')::BIGINT AS first_response_ms,
                   COALESCE(event.metadata ->> 'runtimeErrorCode', event.metadata ->> 'errorType') AS error_code,
                   event.created_at
            FROM lingxi_core.ai_observability_event event
            LEFT JOIN lingxi_core.ai_model_instance model_instance
              ON model_instance.id = (event.metadata ->> 'modelInstanceId')::BIGINT
            LEFT JOIN lingxi_core.ai_model_provider model_provider ON model_provider.id = model_instance.provider_id
            WHERE event.trace_id = #{traceId}
            ORDER BY event.created_at, event.id
            """)
    List<AppTraceEventBO> traceEvents(@Param("traceId") String traceId);
}
