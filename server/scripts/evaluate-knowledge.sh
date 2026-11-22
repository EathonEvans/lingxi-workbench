#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# 使用已有构建产物和应用的数据源/模型密钥环境变量，不启动业务服务。
java -Dloader.main=com.lingxi.workbench.knowledge.evaluation.KnowledgeEvaluationCli \
  -cp application/target/application-0.1.0-SNAPSHOT.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher "$@"
