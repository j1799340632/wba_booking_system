package edu.unsw.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.unsw.domain.entity.Department;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 24/7/2026 21:35
 * @description Department-related database access
 */
@Mapper
public interface DepartmentMapper extends BaseMapper<Department> {
}
