package com.lingxi.workbench.application.runtime.service.app;

import com.lingxi.workbench.application.runtime.model.AppRuntimeSnapshot;
import com.lingxi.workbench.application.runtime.model.vo.AvailableApplicationVO;

import java.util.List;

public interface ApplicationRuntimeService {

    /**
     * 查询应用端允许选择的已启用、已发布 Application。
     */
    List<AvailableApplicationVO> listAvailableApplications();

    /**
     * 按稳定编码解析当前已启用且已发布的 ApplicationVersion。
     */
    AppRuntimeSnapshot loadRuntimeAppConfig(String applicationCode);

    /** 按稳定主键加载历史冻结版本，供异步消费者避免跟随当前发布版本漂移。 */
    AppRuntimeSnapshot loadRuntimeAppVersion(Long applicationId, Long applicationVersionId);
}
