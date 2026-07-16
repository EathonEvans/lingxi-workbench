package com.lingxi.workbench.admin.model.repo.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lingxi.workbench.admin.model.model.entity.ModelInstanceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
@Mapper
public interface ModelInstanceMapper extends BaseMapper<ModelInstanceEntity> {
    @Update("UPDATE lingxi_core.ai_model_instance SET is_default = FALSE, updated_at = CURRENT_TIMESTAMP WHERE model_type = #{type} AND is_default = TRUE")
    int clearDefault(@Param("type") String type);
}
