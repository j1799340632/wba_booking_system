package edu.unsw.api.controller.registrar;

import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.domain.pojo.ResultEntity;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.domain.vo.AssistantInsightsVo;
import edu.unsw.service.AssistantInsightsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Set;

@RestController
@RequestMapping("/registrar/assistantInsights")
public class AssistantInsightsController {
    private final AssistantInsightsService insights;
    public AssistantInsightsController(AssistantInsightsService insights) {this.insights=insights;}
    @GetMapping
    public ResultEntity<AssistantInsightsVo> get(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate endDate) {
        var actor=CurrentUserUtils.getUser();
        if(actor==null || actor.getRoleKey()==null || !Set.of("Admin","SuAdmin").contains(actor.getRoleKey()))throw new BusinessException(ResponseEnum.NO_PERMISSION);
        return ResultEntity.success("Assistant observations loaded",insights.get(startDate,endDate));
    }
}
