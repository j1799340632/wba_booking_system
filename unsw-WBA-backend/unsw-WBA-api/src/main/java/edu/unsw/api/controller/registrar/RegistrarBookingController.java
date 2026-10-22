package edu.unsw.api.controller.registrar;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.unsw.core.constant.RequestPrefixConstant;
import edu.unsw.core.domain.pojo.ResultEntity;
import edu.unsw.domain.dto.RegistrarBookingQueryDto;
import edu.unsw.domain.vo.RegistrarBookingVo;
import edu.unsw.service.RegistrarBookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author Yichen Guo
 * @date 17/7/2026
 * @description Registrar booking related interfaces
 */
@RestController
@RequestMapping(RequestPrefixConstant.REGISTRAR_PREFIX + "/assessmentBooking")
@Validated
@Tag(name = "Registrar booking related interfaces")
public class RegistrarBookingController {
    private final RegistrarBookingService registrarBookingService;

    public RegistrarBookingController(RegistrarBookingService registrarBookingService) {
        this.registrarBookingService = registrarBookingService;
    }

    @GetMapping("/getAllBookings")
    @Operation(summary = "Get all bookings by page")
    public ResultEntity<Page<RegistrarBookingVo>> getAllBookings(
            RegistrarBookingQueryDto registrarBookingQueryDto) {
        return ResultEntity.success(
                "Get all bookings successfully",
                registrarBookingService.getAllBookings(registrarBookingQueryDto)
        );
    }
}
