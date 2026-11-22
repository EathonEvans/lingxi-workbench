package com.lingxi.workbench.knowledge.evaluation;

import com.lingxi.workbench.framework.common.enums.HttpStatusEnum;
import com.lingxi.workbench.framework.exceptions.BusinessException;
import com.lingxi.workbench.framework.json.JSON;
import com.lingxi.workbench.framework.json.JSONException;
import com.lingxi.workbench.framework.json.JSONWriter;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** 只读数据集与新建评测报告的文件边界，不覆盖输入文件或既有报告。 */
@RequiredArgsConstructor
public class KnowledgeEvaluationCommand {
    private static final int MAX_DATASET_BYTES = 1048576;
    private final KnowledgeEvaluationService service;

    /** 执行有大小限制的本地 JSON 数据集，不输出输入内容或供应商异常。 */
    public KnowledgeEvaluationReport execute(String[] arguments) {
        if (arguments.length != 2 || !arguments[0].startsWith("--dataset=") || !arguments[1].startsWith("--output=")) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "用法：--dataset=数据集.json --output=新报告.json");
        }
        Path datasetPath = Path.of(arguments[0].substring("--dataset=".length()));
        Path reportPath = Path.of(arguments[1].substring("--output=".length()));
        if (Files.exists(reportPath)) {
            throw new BusinessException(HttpStatusEnum.CONFLICT, "报告文件已存在，请指定新路径");
        }
        KnowledgeEvaluationDataset dataset;
        try (InputStream stream = Files.newInputStream(datasetPath)) {
            byte[] bytes = stream.readNBytes(MAX_DATASET_BYTES + 1);
            if (bytes.length > MAX_DATASET_BYTES) throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "评测数据集超过 1 MiB");
            dataset = JSON.parseObject(new String(bytes, StandardCharsets.UTF_8), KnowledgeEvaluationDataset.class);
        } catch (IOException | JSONException exception) {
            throw new BusinessException(HttpStatusEnum.BAD_REQUEST, "无法读取有效的知识评测数据集");
        }
        KnowledgeEvaluationReport report = service.evaluate(dataset);
        try {
            Path parent = reportPath.toAbsolutePath().getParent();
            Files.createDirectories(parent);
            Files.writeString(reportPath, JSON.toJSONString(report, JSONWriter.Feature.PrettyFormat, JSONWriter.Feature.WriteLongAsString),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException exception) {
            throw new BusinessException(HttpStatusEnum.INTERNAL_SERVER_ERROR, "知识评测报告写入失败");
        }
        return report;
    }
}
