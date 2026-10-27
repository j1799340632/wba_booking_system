package edu.unsw.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.*;

@Getter @Setter
@TableName("student_assessment_plan")
public class AssessmentPlan {
    @TableId(value="student_id",type=IdType.INPUT)
    private Long studentId;
    private LocalDate startDate;
    private LocalDate deadline;
    private String goalsJson;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime editTime;
}
