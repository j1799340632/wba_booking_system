package edu.unsw.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 28/7/2026 21:40
 * @description DictionaryVo (hospital, department...)
 */
@Data
@Schema(title = "DictionaryVo (hospital, department...)")
public class DictionaryVo {
    @Schema(description = "Snowflake primary key")
    private Long id;

    @Schema(description = "Dictionary name")
    private String name;

    @Schema(description = "Creator id")
    private Long creator;

    @Schema(description = "Creator name")
    private String creatorName;

    @Schema(description = "Create time")
    private LocalDateTime createTime;

    @Schema(description = "Edit time")
    private LocalDateTime editTime;

    @Schema(description = "1/true is enabled")
    private Boolean isEnable;
}
