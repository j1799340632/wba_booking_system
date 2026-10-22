package edu.unsw.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.unsw.domain.dto.RegistrarBookingQueryDto;
import edu.unsw.domain.vo.RegistrarBookingVo;

/**
 * @author Yichen Guo
 * @date 17/7/2026
 * @description Registrar booking related services
 */
public interface RegistrarBookingService {
    /**
     * Get all bookings by page for registrar.
     *
     * @param registrarBookingQueryDto query and pagination parameters
     * @return paginated booking records
     */
    Page<RegistrarBookingVo> getAllBookings(RegistrarBookingQueryDto registrarBookingQueryDto);
}
