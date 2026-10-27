package edu.unsw.api.controller.student;

import edu.unsw.core.annotation.RepeatRequestLimit;
import edu.unsw.core.domain.pojo.ResultEntity;
import edu.unsw.domain.dto.AssessmentPlanSaveDto;
import edu.unsw.domain.vo.AssessmentPlanVo;
import edu.unsw.service.AssessmentPlanningService;
import edu.unsw.service.BookingSubmissionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/student/assessmentPlan")
public class AssessmentPlanController {
    private final AssessmentPlanningService planning;
    public AssessmentPlanController(AssessmentPlanningService planning) { this.planning=planning; }
    @GetMapping
    public ResultEntity<AssessmentPlanVo> get(@RequestParam(defaultValue="false") boolean summary) {
        BookingSubmissionService.studentId();
        return ResultEntity.success("Assessment plan loaded",summary?planning.summary():planning.get());
    }
    @PostMapping
    @RepeatRequestLimit(time=60,count=20)
    public ResultEntity<Integer> save(@Valid @RequestBody AssessmentPlanSaveDto request) {
        BookingSubmissionService.studentId();
        return ResultEntity.success("Assessment targets saved",planning.save(request));
    }
}
