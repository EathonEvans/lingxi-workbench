package com.lingxi.workbench.admin.model.repo.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.model.common.enums.ModelType;
import com.lingxi.workbench.admin.model.model.entity.ModelInstanceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 模型实例 MyBatis 数据访问层。 */
@Mapper
public interface ModelInstanceMapper extends BaseMapper<ModelInstanceEntity> {

    /** 清除指定模型类型下所有实例的默认标记，确保同一类型只有一个默认实例。 */
    @Update("UPDATE lingxi_core.ai_model_instance SET is_default = FALSE, updated_at = CURRENT_TIMESTAMP WHERE model_type = #{type} AND is_default = TRUE")
    int clearDefault(@Param("type") ModelType type);
}
