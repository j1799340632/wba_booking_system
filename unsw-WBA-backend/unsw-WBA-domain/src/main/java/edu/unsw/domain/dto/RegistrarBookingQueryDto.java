package edu.unsw.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author Yichen Guo
 * @date 17/7/2026
 * @description Registrar booking query field
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema(title = "Registrar booking query field")
public class RegistrarBookingQueryDto extends BaseQueryDto {
    @Schema(description = "Search keyword for student name, student zId, or supervisor name")
    private String keyword;

    @Schema(description = "Booking status, reference edu.unsw.service.enums.AssessmentBookingStatusEnum")
    private Integer status;

    @Schema(description = "Hospital")
    private String hospital;

    @Schema(description = "Department")
    private String department;
}