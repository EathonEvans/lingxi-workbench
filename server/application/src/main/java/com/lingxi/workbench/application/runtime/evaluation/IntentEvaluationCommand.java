package com.lingxi.workbench.application.runtime.evaluation;

import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.framework.json.JSONException;
import com.lingxi.workbench.framework.json.JSONWriter;
import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 意图评测离线命令的参数解析和文件输入输出边界。 */
@RequiredArgsConstructor
public class IntentEvaluationCommand {

    private final IntentEvaluationService intentEvaluationService;
    private final IntentRoutingEvaluationService intentRoutingEvaluationService;

    /**
     * 执行 export 或 evaluate 命令。
     *
     * @param arguments 命令行参数
     */
    public void execute(String[] arguments) {
        IntentEvaluationCommandArguments commandArguments = IntentEvaluationCommandArguments.parse(arguments);
        if (commandArguments.operation() == IntentEvaluationCommandOperation.EXPORT) {
            IntentEvaluationDataset dataset = intentEvaluationService.exportDatasetTemplate(
                    commandArguments.requireLong("application-id"),
                    commandArguments.requireLong("application-version-id"),
                    commandArguments.require("dataset-version"));
            write(commandArguments.requirePath("output"), dataset);
            return;
        }
        if (commandArguments.operation() == IntentEvaluationCommandOperation.EXPORT_V2) {
            IntentRoutingEvaluationDataset dataset = intentRoutingEvaluationService.exportDatasetTemplate(
                    commandArguments.requireLong("application-id"),
                    commandArguments.requireLong("application-version-id"),
                    commandArguments.require("dataset-version"));
            write(commandArguments.requirePath("output"), dataset);
            return;
        }
        if (commandArguments.operation() == IntentEvaluationCommandOperation.EVALUATE_V2) {
            IntentRoutingEvaluationDataset dataset = read(
                    commandArguments.requirePath("dataset"), IntentRoutingEvaluationDataset.class);
            IntentRoutingEvaluationReport report = intentRoutingEvaluationService.evaluate(dataset);
            write(commandArguments.requirePath("output"), report);
            return;
        }
        IntentEvaluationDataset dataset = read(
                commandArguments.requirePath("dataset"), IntentEvaluationDataset.class);
        write(commandArguments.requirePath("output"), intentEvaluationService.evaluate(dataset));
    }

    private <T> T read(Path datasetPath, Class<T> type) {
        if (!Files.isRegularFile(datasetPath)) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "评测数据集文件不存在: " + datasetPath);
        }
        try {
            return JSON.parseObject(Files.readString(datasetPath), type);
        } catch (IOException | JSONException exception) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST,
                    "评测数据集文件格式非法: " + exception.getMessage());
        }
    }

    private void write(Path outputPath, Object value) {
        try {
            Path parent = outputPath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(outputPath, JSON.toJSONString(value,
                    JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteLongAsString));
        } catch (IOException exception) {
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR,
                    "意图评测报告写入失败: " + exception.getMessage());
        }
    }
}
