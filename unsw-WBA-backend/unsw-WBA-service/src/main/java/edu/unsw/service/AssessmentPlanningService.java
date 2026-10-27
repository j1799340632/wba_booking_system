package edu.unsw.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.dao.mapper.*;
import edu.unsw.domain.dto.*;
import edu.unsw.domain.entity.*;
import edu.unsw.domain.vo.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import java.time.*;
import java.time.temporal.*;
import java.util.*;
import java.util.stream.Collectors;

/** Personal goals and transparent pacing. The model never decides required course counts. */
@Service
public class AssessmentPlanningService {
    private final AssessmentPlanMapper plans;
    private final WbaTypeMapper types;
    private final AssessmentBookingMapper bookings;
    private final AssessmentSlotMapper slots;
    private final UserMapper users;
    private final StudentSlotSearchService search;
    private final BookingSubmissionService submissions;
    private final Clock clock;
    private final JsonMapper json=new JsonMapper();

    public record StoredGoal(Long typeId,String typeName,int target) { }

    public AssessmentPlanningService(AssessmentPlanMapper plans,WbaTypeMapper types,AssessmentBookingMapper bookings,
            AssessmentSlotMapper slots,UserMapper users,StudentSlotSearchService search,BookingSubmissionService submissions,
            @Qualifier("bookingClock") Clock clock) {
        this.plans=plans; this.types=types; this.bookings=bookings; this.slots=slots; this.users=users;
        this.search=search; this.submissions=submissions; this.clock=clock;
    }

    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
    public AssessmentPlanVo get() { return build(true); }

    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
    public AssessmentPlanVo summary() { return build(false); }

    @Transactional(isolation=Isolation.READ_COMMITTED,rollbackFor=Exception.class)
    public Integer save(AssessmentPlanSaveDto request) {
        Long owner=BookingSubmissionService.studentId(); active(users.lockBookingParticipant(owner));
        if(request==null || request.getStartDate()==null || request.getDeadline()==null || request.getExpectedVersion()==null
                || request.getExpectedVersion()<0 || request.getGoals()==null || request.getGoals().isEmpty() || request.getGoals().size()>20)
            fail("Set a period and at least one assessment target.");
        long days=ChronoUnit.DAYS.between(request.getStartDate(),request.getDeadline());
        if(days<0 || days>365) fail("The planning period must be between 1 and 366 days, including the deadline.");
        var enabled=enabledTypes().stream().collect(Collectors.toMap(WbaType::getId,t->t));
        var goals=new ArrayList<StoredGoal>(); var usedIds=new HashSet<Long>(); var usedNames=new HashSet<String>();
        int total=0;
        for(var goal:request.getGoals()) {
            if(goal==null || goal.getTypeId()==null || goal.getTarget()==null || goal.getTarget()<1 || goal.getTarget()>100) fail("Each target must be between 1 and 100.");
            var type=enabled.get(goal.getTypeId());
            if(type==null) fail("An assessment type is no longer available. Refresh the available types.");
            if(!usedIds.add(type.getId()) || !usedNames.add(type.getName().trim().toLowerCase(Locale.ROOT))) fail("Use each assessment type only once.");
            total+=goal.getTarget(); goals.add(new StoredGoal(type.getId(),type.getName(),goal.getTarget()));
        }
        if(total>1000) fail("The plan cannot contain more than 1000 assessments.");
        goals.sort(Comparator.comparing(StoredGoal::typeId));
        String encoded=json.writeValueAsString(goals);
        var existing=plans.selectById(owner);
        if(existing!=null && !Objects.equals(existing.getVersion(),request.getExpectedVersion())) {
            // A lost save response can be retried without overwriting a different edit from another tab.
            if(encoded.equals(existing.getGoalsJson()) && request.getStartDate().equals(existing.getStartDate()) && request.getDeadline().equals(existing.getDeadline())) return existing.getVersion();
            fail("Your plan changed in another session. Reload before saving your edits.");
        }
        if(existing==null && request.getExpectedVersion()!=0) fail("Your plan changed. Reload before saving.");
        var plan=existing==null?new AssessmentPlan():existing;
        plan.setStudentId(owner); plan.setStartDate(request.getStartDate()); plan.setDeadline(request.getDeadline()); plan.setGoalsJson(encoded);
        plan.setVersion(existing==null?1:existing.getVersion()+1); plan.setEditTime(LocalDateTime.now(clock));
        if(existing==null) {
            plan.setCreateTime(plan.getEditTime());
            if(plans.insert(plan)!=1) fail("The plan could not be saved. Retry or refresh.");
        } else if(plans.updateById(plan)!=1) fail("The plan could not be saved. Retry or refresh.");
        return plan.getVersion();
    }

    private AssessmentPlanVo build(boolean includeRecommendations) {
        Long owner=BookingSubmissionService.studentId(); active(users.selectById(owner));
        var now=LocalDateTime.now(clock);
        var result=new AssessmentPlanVo(); result.setToday(now.toLocalDate()); result.setGeneratedAt(now); result.setTimeZone(clock.getZone().getId());
        var enabled=enabledTypes();
        result.setAvailableTypes(enabled.stream().map(t->new AssessmentPlanVo.TypeOption(t.getId().toString(),t.getName())).toList());
        var plan=plans.selectById(owner);
        if(plan==null) {
            result.setGuidance("Set your own assessment targets and placement dates. Course requirements are not preconfigured.");
            return result;
        }
        result.setConfigured(true); result.setVersion(plan.getVersion()); result.setStartDate(plan.getStartDate()); result.setDeadline(plan.getDeadline());
        result.setDaysToDeadline(ChronoUnit.DAYS.between(result.getToday(),plan.getDeadline()));
        StoredGoal[] stored;
        try { stored=json.readValue(plan.getGoalsJson(),StoredGoal[].class); }
        catch(RuntimeException invalid) { fail("Saved plan data could not be read. Please contact support."); return result; }
        var end=plan.getDeadline().plusDays(1).atStartOfDay();
        var start=plan.getStartDate().atStartOfDay(); var futureStart=now.isAfter(start)?now:start;
        var lookahead=futureStart.plusDays(30); if(lookahead.isAfter(end)) lookahead=end;
        result.setRecommendationEnd(lookahead);
        var own=bookings.selectList(Wrappers.<AssessmentBooking>lambdaQuery().eq(AssessmentBooking::getCreator,owner).in(AssessmentBooking::getStatus,List.of(0,1,2)));
        var slotIds=own.stream().map(AssessmentBooking::getAssessmentSlotId).filter(Objects::nonNull).distinct().toList();
        var slotMap=slotIds.isEmpty()?Map.<Long,AssessmentSlot>of():slots.selectByIdsIncludingDeleted(slotIds).stream().collect(Collectors.toMap(AssessmentSlot::getId,s->s));
        var weeks=weeks(futureStart.toLocalDate(),plan.getDeadline()); result.setWeeksRemaining(weeks.size());
        var enabledMap=enabled.stream().collect(Collectors.toMap(WbaType::getId,t->t));
        var progress=new ArrayList<AssessmentPlanVo.GoalProgress>();
        for(var goal:stored) {
            var row=new AssessmentPlanVo.GoalProgress(); row.setTypeId(goal.typeId().toString()); row.setType(goal.typeName()); row.setTarget(goal.target());
            var current=enabledMap.get(goal.typeId()); row.setTypeAvailable(current!=null && current.getName().equals(goal.typeName()));
            for(var booking:own) {
                var slot=slotMap.get(booking.getAssessmentSlotId());
                if(slot==null || slot.getType()==null || !goal.typeName().equalsIgnoreCase(slot.getType().trim())) continue;
                if(Objects.equals(booking.getStatus(),2)) {
                    var completed=booking.getCompletedTime();
                    if(completed!=null && !completed.isBefore(start) && completed.isBefore(end) && !completed.isAfter(now)) row.setCompleted(row.getCompleted()+1);
                } else if(validPeriod(booking,start,end)) {
                    if(!booking.getEndTime().isAfter(now)) row.setMissed(row.getMissed()+1);
                    else if(!booking.getStartTime().isAfter(now)) {
                        if(Objects.equals(booking.getStatus(),1)) row.setInProgress(row.getInProgress()+1);
                    }
                    else if(Objects.equals(booking.getStatus(),1)) row.setConfirmed(row.getConfirmed()+1);
                    else row.setPending(row.getPending()+1);
                }
            }
            row.setRemaining(Math.max(0,row.getTarget()-row.getCompleted()));
            row.setToRequest(Math.max(0,row.getRemaining()-row.getConfirmed()-row.getPending()-row.getInProgress()));
            row.setSuggestedPerWeek(weeks.isEmpty()?0:(row.getRemaining()+weeks.size()-1)/weeks.size());
            if(includeRecommendations && row.getToRequest()>0 && futureStart.isBefore(lookahead) && row.isTypeAvailable()) {
                row.setCandidates(recommend(row.getType(),futureStart,lookahead));
                row.setRecommendationMessage(row.getCandidates().isEmpty()?"No bookable sessions were found in this search window. Check Book Slots or ask your supervisor about additional sessions.":"Options checked against current availability and your bookings. Review before requesting.");
            } else if(!row.isTypeAvailable()) row.setRecommendationMessage("This saved assessment type was renamed or disabled. Review your targets before requesting new sessions.");
            else if(row.getRemaining()==0) row.setRecommendationMessage("Target completed for this period.");
            else if(row.getToRequest()==0) row.setRecommendationMessage("Existing requests cover the current gap. Pending requests still need approval, and booked sessions still need completion.");
            else if(!futureStart.isBefore(lookahead)) row.setRecommendationMessage("The deadline has passed. Review your dates and discuss the remaining assessments with your supervisor.");
            else row.setRecommendationMessage("Open your assessment plan to find sessions for this gap.");
            progress.add(row);
        }
        result.setGoals(progress);
        result.setTotalTarget(progress.stream().mapToInt(AssessmentPlanVo.GoalProgress::getTarget).sum());
        result.setCompleted(progress.stream().mapToInt(g->Math.min(g.getTarget(),g.getCompleted())).sum());
        result.setConfirmed(progress.stream().mapToInt(AssessmentPlanVo.GoalProgress::getConfirmed).sum());
        result.setPending(progress.stream().mapToInt(AssessmentPlanVo.GoalProgress::getPending).sum());
        result.setInProgress(progress.stream().mapToInt(AssessmentPlanVo.GoalProgress::getInProgress).sum());
        result.setRemaining(progress.stream().mapToInt(AssessmentPlanVo.GoalProgress::getRemaining).sum());
        result.setToRequest(progress.stream().mapToInt(AssessmentPlanVo.GoalProgress::getToRequest).sum());
        result.setUncountedRecords((int)own.stream().filter(b->!slotMap.containsKey(b.getAssessmentSlotId()) || (Objects.equals(b.getStatus(),2) ? b.getCompletedTime()==null : !validDates(b))).count());
        populateFollowUps(result,own,slotMap,progress,start,end,now);
        populateWeeks(weeks,progress,own,slotMap); result.setWeeks(weeks);
        result.setStatus(result.getRemaining()==0?"COMPLETE":plan.getDeadline().isBefore(result.getToday())?"OVERDUE":result.getToRequest()>0?"NEEDS_BOOKING":result.getPending()>0?"AWAITING_APPROVAL":"SCHEDULED");
        result.setGuidance("Only completed assessments count toward targets. Confirmed and pending sessions are provisional coverage. Weekly request counts are pacing suggestions, not reservations.");
        return result;
    }

    private boolean validDates(AssessmentBooking booking) {
        return booking.getStartTime()!=null && booking.getEndTime()!=null && booking.getEndTime().isAfter(booking.getStartTime());
    }

    private boolean validPeriod(AssessmentBooking booking,LocalDateTime start,LocalDateTime end) {
        return validDates(booking) && !booking.getStartTime().isBefore(start) && !booking.getEndTime().isAfter(end);
    }

    private void populateFollowUps(AssessmentPlanVo result,List<AssessmentBooking> own,Map<Long,AssessmentSlot> slotMap,
            List<AssessmentPlanVo.GoalProgress> goals,LocalDateTime start,LocalDateTime end,LocalDateTime now) {
        var plannedTypes=goals.stream().map(g->g.getType().trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        var mentorIds=slotMap.values().stream().map(AssessmentSlot::getCreator).filter(Objects::nonNull).distinct().toList();
        var mentors=mentorIds.isEmpty()?Map.<Long,User>of():users.selectByIds(mentorIds).stream().collect(Collectors.toMap(User::getId,u->u));
        var upcomingEnd=clock.instant().plus(Duration.ofHours(48));
        var items=new ArrayList<AssessmentPlanVo.FollowUp>();
        for(var booking:own) {
            if(!Objects.equals(booking.getStatus(),0) && !Objects.equals(booking.getStatus(),1)) continue;
            var slot=slotMap.get(booking.getAssessmentSlotId());
            if(slot==null || slot.getType()==null || !plannedTypes.contains(slot.getType().trim().toLowerCase(Locale.ROOT)) || !validPeriod(booking,start,end)) continue;
            String kind;
            if(!booking.getEndTime().isAfter(now)) kind=Objects.equals(booking.getStatus(),1)?"AWAITING_COMPLETION":"UNAPPROVED_SESSION";
            else if(!booking.getStartTime().isAfter(now)) kind=Objects.equals(booking.getStatus(),1)?"IN_PROGRESS":"UNAPPROVED_SESSION";
            else if(Objects.equals(booking.getStatus(),0)) kind="PENDING_APPROVAL";
            else kind=!booking.getStartTime().atZone(clock.getZone()).toInstant().isAfter(upcomingEnd)?"UPCOMING":"SCHEDULED";
            var mentor=mentors.get(slot.getCreator());
            items.add(new AssessmentPlanVo.FollowUp(booking.getId().toString(),kind,booking.getStatus(),slot.getType(),
                    mentor==null?"Supervisor unavailable":mentor.getName(),booking.getStartTime(),booking.getEndTime(),slot.getHospital(),slot.getDepartment(),slot.getLocation()));
        }
        var priority=List.of("UNAPPROVED_SESSION","AWAITING_COMPLETION","IN_PROGRESS","UPCOMING","PENDING_APPROVAL","SCHEDULED");
        items.sort(Comparator.comparingInt((AssessmentPlanVo.FollowUp item)->priority.indexOf(item.kind()))
                .thenComparing(AssessmentPlanVo.FollowUp::startTime).thenComparing(AssessmentPlanVo.FollowUp::bookingId));
        result.setFollowUpTotal(items.size()); result.setHasMoreFollowUps(items.size()>20);
        result.setFollowUps(items.stream().limit(20).toList());
        result.setFollowUpCounts(new AssessmentPlanVo.FollowUpCounts(
                (int)items.stream().filter(i->Set.of("UNAPPROVED_SESSION","AWAITING_COMPLETION").contains(i.kind())).count(),
                countKind(items,"PENDING_APPROVAL"),countKind(items,"IN_PROGRESS"),countKind(items,"UPCOMING"),countKind(items,"SCHEDULED")));
    }

    private int countKind(List<AssessmentPlanVo.FollowUp> items,String kind) {
        return (int)items.stream().filter(i->kind.equals(i.kind())).count();
    }

    private List<BookingAssistantResult.Candidate> recommend(String type,LocalDateTime start,LocalDateTime end) {
        var query=new AssessmentSlotQueryDto(); query.setType(type); query.setStartTime(start); query.setEndTime(end); query.setPageQuantity(-1); query.setIsOrderByAvailableTime(true);
        record Option(AssessmentSlotVo slot,LocalDateTime start,LocalDateTime end) { }
        var options=new ArrayList<Option>();
        for(var slot:search.search(query).getRecords()) {
            if(slot.getId()==null || slot.getAvailableTimeList()==null) continue;
            for(var time:slot.getAvailableTimeList()) if(time.size()==2 && time.getFirst()!=null && time.getLast()!=null) options.add(new Option(slot,time.getFirst(),time.getLast()));
        }
        options.sort(Comparator.comparing(Option::start).thenComparing(o->o.slot().getId()));
        var result=new ArrayList<BookingAssistantResult.Candidate>(); var seen=new HashSet<String>();
        for(var option:options) {
            if(!seen.add(option.slot().getId()+"/"+option.start())) continue;
            var request=new AssessmentBookingSaveDto(); request.setAssessmentSlotId(option.slot().getId()); request.setSlotVersion(option.slot().getVersion()); request.setStartTime(option.start()); request.setEndTime(option.end());
            try { result.add(submissions.review(request).candidate()); }
            catch(BusinessException unavailable) { continue; }
            if(result.size()==3) break;
        }
        return result;
    }

    private List<AssessmentPlanVo.Week> weeks(LocalDate start,LocalDate deadline) {
        var result=new ArrayList<AssessmentPlanVo.Week>();
        while(!start.isAfter(deadline)) {
            var end=start.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)); if(end.isAfter(deadline)) end=deadline;
            var week=new AssessmentPlanVo.Week(); week.setStartDate(start); week.setEndDate(end); result.add(week); start=end.plusDays(1);
        }
        return result;
    }

    private void populateWeeks(List<AssessmentPlanVo.Week> weeks,List<AssessmentPlanVo.GoalProgress> goals,List<AssessmentBooking> own,Map<Long,AssessmentSlot> slotMap) {
        for(int index=0;index<weeks.size();index++) {
            var week=weeks.get(index); var rows=new ArrayList<AssessmentPlanVo.WeekGoal>();
            for(var goal:goals) {
                int pending=0,confirmed=0;
                for(var booking:own) {
                    var slot=slotMap.get(booking.getAssessmentSlotId());
                    if(slot==null || slot.getType()==null || !goal.getType().equalsIgnoreCase(slot.getType().trim()) || !validDates(booking) || !booking.getStartTime().isAfter(LocalDateTime.now(clock))) continue;
                    var day=booking.getStartTime().toLocalDate();
                    if(day.isBefore(week.getStartDate()) || day.isAfter(week.getEndDate()) || booking.getEndTime()==null || booking.getEndTime().isAfter(weeks.getLast().getEndDate().plusDays(1).atStartOfDay())) continue;
                    if(Objects.equals(booking.getStatus(),0)) pending++; else if(Objects.equals(booking.getStatus(),1)) confirmed++;
                }
                int requests=goal.getToRequest()/weeks.size()+(index<goal.getToRequest()%weeks.size()?1:0);
                rows.add(new AssessmentPlanVo.WeekGoal(goal.getType(),requests,confirmed,pending));
            }
            week.setGoals(rows);
        }
    }
    private List<WbaType> enabledTypes() {
        return types.selectList(Wrappers.<WbaType>lambdaQuery().eq(WbaType::getIsEnable,true).orderByAsc(WbaType::getName));
    }
    private void active(User user) {
        if(user==null || !Objects.equals(user.getRoleId(),4L) || !Boolean.TRUE.equals(user.getIsEnable()) || Boolean.TRUE.equals(user.getIsCanceled())) throw new BusinessException(ResponseEnum.NO_PERMISSION);
    }
    private static void fail(String text) { throw new BusinessException(ResponseEnum.ILLEGAL_PARAM.getCode(),text); }
}
