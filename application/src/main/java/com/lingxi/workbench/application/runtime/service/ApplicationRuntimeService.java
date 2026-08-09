package com.lingxi.workbench.application.runtime.service;

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
}
