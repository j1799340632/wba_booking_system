package edu.unsw.service;

import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.dao.mapper.*;
import edu.unsw.domain.vo.AssistantInsightsVo;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AssistantInsightsService {
    private final AssistantEventMapper events;
    private final UserMapper users;
    private final AssistantTelemetryService telemetry;
    private final Clock clock;
    public AssistantInsightsService(AssistantEventMapper events,UserMapper users,AssistantTelemetryService telemetry,@Qualifier("bookingClock") Clock clock) {
        this.events=events;this.users=users;this.telemetry=telemetry;this.clock=clock;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public AssistantInsightsVo get(LocalDate startDate,LocalDate endDate) {
        var actor=CurrentUserUtils.getUser();
        if(actor==null || actor.getId()==null || actor.getRoleKey()==null || !Set.of("Admin","SuAdmin").contains(actor.getRoleKey()))denied();
        var user=users.selectById(actor.getId());
        if(user==null || !Boolean.TRUE.equals(user.getIsEnable()) || Boolean.TRUE.equals(user.getIsCanceled())
                || !("Admin".equals(actor.getRoleKey()) && Objects.equals(user.getRoleId(),2L) || "SuAdmin".equals(actor.getRoleKey()) && Objects.equals(user.getRoleId(),1L)))denied();
        var now=LocalDateTime.now(clock);var today=now.toLocalDate();
        if(endDate==null)endDate=today;if(startDate==null)startDate=endDate.minusDays(6);
        if(endDate.isAfter(today) || startDate.isAfter(endDate) || ChronoUnit.DAYS.between(startDate,endDate)>30)
            throw new BusinessException(ResponseEnum.ILLEGAL_PARAM.getCode(),"Choose 1–31 calendar days ending no later than today in Australia/Sydney.");
        var start=startDate.atStartOfDay();var end=endDate.equals(today)?now.truncatedTo(ChronoUnit.MICROS).plusNanos(1000):endDate.plusDays(1).atStartOfDay();
        var result=new AssistantInsightsVo();result.setStartDate(startDate);result.setEndDate(endDate);result.setGeneratedAt(now);result.setTimeZone(clock.getZone().getId());
        var counts=events.counts(start,end);result.setStages(counts);result.setFirstRecordedAt(events.firstRecordedAt());
        for(var row:counts) {
            result.setObservedOperations(result.getObservedOperations()+row.getAttempts());
            result.setModelCalls(result.getModelCalls()+row.getModelCalls());result.setCallsWithKnownUsage(result.getCallsWithKnownUsage()+row.getKnownUsage());
            result.setPromptTokens(result.getPromptTokens()+row.getPromptTokens());result.setCompletionTokens(result.getCompletionTokens()+row.getCompletionTokens());result.setTotalTokens(result.getTotalTokens()+row.getTotalTokens());
            if("SEARCH".equals(row.getStage())) {result.setSearchRequests(result.getSearchRequests()+row.getAttempts());result.setSearchWithAlternatives(result.getSearchWithAlternatives()+row.getWithAlternatives());}
            if("CONFIRM_BOOKING".equals(row.getStage())) {
                result.setBookingConfirmAttempts(result.getBookingConfirmAttempts()+row.getAttempts());
                if("SAVED".equals(row.getOutcome()))result.setNewBookingRequests(result.getNewBookingRequests()+row.getAttempts());
                else if("REPLAYED".equals(row.getOutcome()))result.setBookingConfirmReplays(result.getBookingConfirmReplays()+row.getAttempts());
                else result.setBookingConfirmFailures(result.getBookingConfirmFailures()+row.getAttempts());
            }
        }
        if(result.getSearchRequests()>0) {
            long n=result.getSearchRequests();
            result.setSearchP50Ms(events.searchLatency(start,end,Math.max(0,(n+1)/2-1)));
            result.setSearchP95Ms(events.searchLatency(start,end,Math.max(0,n-n/20-1)));
        }
        var daily=events.days(start,end).stream().collect(Collectors.toMap(AssistantInsightsVo.Daily::getDate,d->d));
        var days=new ArrayList<AssistantInsightsVo.Daily>();
        for(var date=startDate;!date.isAfter(endDate);date=date.plusDays(1)) {
            var row=daily.get(date);if(row==null){row=new AssistantInsightsVo.Daily();row.setDate(date);}days.add(row);
        }
        result.setDays(days);result.setModels(events.models(start,end));result.setDroppedEventsSinceRestart(telemetry.droppedSinceRestart());return result;
    }
    private void denied() {throw new BusinessException(ResponseEnum.NO_PERMISSION);}
}
