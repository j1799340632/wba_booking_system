package edu.unsw.api.aop;

import edu.unsw.core.annotation.AssistantObserved;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.domain.pojo.ResultEntity;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.domain.entity.AssistantEvent;
import edu.unsw.domain.vo.*;
import edu.unsw.service.AssistantTelemetryService;
import edu.unsw.service.assistant.BookingModelObservation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.Set;

@Aspect @Component @Order(100)
public class AssistantObservationAspect {
    private static final Logger log=LoggerFactory.getLogger(AssistantObservationAspect.class);
    private final AssistantTelemetryService telemetry;
    private final Clock clock;
    public AssistantObservationAspect(AssistantTelemetryService telemetry,@Qualifier("bookingClock") Clock clock) {this.telemetry=telemetry;this.clock=clock;}

    @Around("@annotation(observed)")
    public Object observe(ProceedingJoinPoint invocation,AssistantObserved observed) throws Throwable {
        var actor=CurrentUserUtils.getUser();
        if(actor==null || actor.getId()==null || !"Stu".equals(actor.getRoleKey()))return invocation.proceed();
        long started=System.nanoTime();
        try(var model=BookingModelObservation.begin()) {
            Object response;
            try {response=invocation.proceed();}
            catch(Throwable failure) {save(actor.getId(),observed.value(),null,failure,started,model);throw failure;}
            save(actor.getId(),observed.value(),response,null,started,model);return response;
        }
    }

    private void save(Long actor,String stage,Object response,Throwable failure,long started,BookingModelObservation model) {
        try {
            var event=new AssistantEvent();event.setActorId(actor);event.setCreatedAt(LocalDateTime.now(clock));
            event.setStage(stage);event.setElapsedMs(Math.max(0,(System.nanoTime()-started)/1_000_000));event.setAlternativeGroups(0);
            event.setModelCalls(model.calls());event.setModelName(model.model());event.setUsageKnown(model.usageKnown());
            if(model.usageKnown()){event.setPromptTokens(model.promptTokens());event.setCompletionTokens(model.completionTokens());event.setTotalTokens(model.totalTokens());}
            if(failure!=null)event.setOutcome(failure instanceof BusinessException?"REJECTED":"ERROR");
            else {
                Object data=response instanceof ResultEntity<?> result?result.getData():null;
                if(data instanceof BookingAssistantResult result) {
                    event.setOutcome(result.getStatus()==null?"ERROR":result.getStatus().name());
                    event.setAlternativeGroups(result.getAlternatives()==null?0:result.getAlternatives().size());
                    event.setAction(safeAction(result.getBookingAction()));
                } else if(data instanceof BookingVo result)event.setOutcome(result.isReplayed()?"REPLAYED":"SAVED");
                else if(data instanceof BookingManagementVo result) {
                    event.setAction(safeAction(result.getAction()));
                    event.setOutcome(stage.startsWith("PREPARE")?"REVIEW_READY":result.isReplayed()?"REPLAYED":"SAVED");
                } else if(data instanceof BookingDraftVo)event.setOutcome("REVIEW_READY");
                else event.setOutcome("ERROR");
            }
            telemetry.record(event);
        } catch(RuntimeException unavailable) {
            telemetry.dropped();log.warn("Assistant observation was not saved: {}",unavailable.getClass().getSimpleName());
        }
    }
    private String safeAction(String action) {
        return action!=null && Set.of("SEARCH","LIST_BOOKINGS","CANCEL_BOOKING","RESCHEDULE_BOOKING","PLAN_ASSESSMENTS","CANCEL","RESCHEDULE").contains(action)?action:null;
    }
}
