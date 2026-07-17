package com.lingxi.workbench.admin.auth.model.request;

import com.lingxi.workbench.admin.common.enums.EnableStatus;
import com.lingxi.workbench.framework.convention.PageQuery;

/** 系统用户分页查询条件。 */
public class SysUserPageQuery extends PageQuery {

    /** 登录账号，支持模糊匹配。 */
    private String username;
    /** 显示名称，支持模糊匹配。 */
    private String displayName;
    /** 账号状态筛选。 */
    private EnableStatus status;

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public EnableStatus getStatus() {
        return status;
    }

    public void setStatus(EnableStatus status) {
        this.status = status;
    }
}
