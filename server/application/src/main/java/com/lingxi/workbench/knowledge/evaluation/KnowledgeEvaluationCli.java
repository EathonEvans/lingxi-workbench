package com.lingxi.workbench.knowledge.evaluation;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/** 知识检索离线评测入口，不启动 Web 端口、任务调度或数据写入服务。 */
public final class KnowledgeEvaluationCli {
    private KnowledgeEvaluationCli() { }

    /**
     * 执行评测后关闭资源；有失败断言返回非零退出码，便于回归脚本阻止发布。
     * @param arguments --dataset=输入文件 --output=报告文件
     */
    public static void main(String[] arguments) {
        boolean passed;
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(KnowledgeEvaluationConfiguration.class)
                .web(WebApplicationType.NONE).properties("lingxi.knowledge-evaluation.enabled=true").run()) {
            passed = context.getBean(KnowledgeEvaluationCommand.class).execute(arguments).isPassed();
        }
        if (!passed) System.exit(1);
    }
}
