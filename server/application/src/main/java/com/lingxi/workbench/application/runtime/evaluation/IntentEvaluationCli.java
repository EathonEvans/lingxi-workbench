package com.lingxi.workbench.application.runtime.evaluation;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * 意图路由只读离线评测入口。V1 只评候选召回，V2 通过内部接口调用正在运行的完整决策链。
 *
 * <p>示例：{@code export --application-id=1 --application-version-id=2
 * --dataset-version=v1-baseline-20260824 --output=/tmp/intent-dataset.json}。</p>
 */
public final class IntentEvaluationCli {

    private IntentEvaluationCli() {
    }

    /**
     * 启动非 Web Spring 上下文，执行完成后立即关闭，不启动 Chat 服务端口。
     *
     * @param arguments export/evaluate 命令及其参数
     */
    public static void main(String[] arguments) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(IntentEvaluationConfiguration.class)
                .web(WebApplicationType.NONE)
                .properties("lingxi.intent-evaluation.enabled=true")
                .run(arguments)) {
            context.getBean(IntentEvaluationCommand.class).execute(arguments);
        }
    }
}
