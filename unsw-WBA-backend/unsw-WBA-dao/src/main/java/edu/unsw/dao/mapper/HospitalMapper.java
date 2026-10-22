package edu.unsw.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import edu.unsw.domain.entity.Hospital;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 24/7/2026 21:35
 * @description Hospital-related database access
 */
@Mapper
public interface HospitalMapper extends BaseMapper<Hospital> {
}
