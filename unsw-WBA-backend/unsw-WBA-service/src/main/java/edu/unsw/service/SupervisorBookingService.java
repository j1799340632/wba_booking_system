package edu.unsw.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.constant.RedisKeyConstant;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.core.utils.CurrentUserUtils;
import edu.unsw.core.utils.RedissonUtils;
import edu.unsw.dao.mapper.*;
import edu.unsw.domain.dto.*;
import edu.unsw.domain.entity.*;
import edu.unsw.domain.vo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Explicit supervisor decisions. Student -> supervisor -> slot -> booking matches the student write lock order. */
@Service
public class SupervisorBookingService {
    private static final Logger log=LoggerFactory.getLogger(SupervisorBookingService.class);
    public enum Action { APPROVE, DECLINE, CANCEL }
    private final AssessmentBookingMapper bookings;
    private final AssessmentSlotMapper slots;
    private final UserMapper users;
    private final MessageMapper messages;
    private final RedissonUtils redis;
    private final Clock clock;

    public SupervisorBookingService(AssessmentBookingMapper bookings,AssessmentSlotMapper slots,UserMapper users,
            MessageMapper messages,RedissonUtils redis,@Qualifier("bookingClock") Clock clock) {
        this.bookings=bookings;this.slots=slots;this.users=users;this.messages=messages;this.redis=redis;this.clock=clock;
    }

    @Transactional(readOnly=true,isolation=Isolation.READ_COMMITTED)
    public Page<UpcomingSessionVo> sessions(AssessmentSlotQueryDto query,int status) {
        Long owner=supervisorId(); activeSupervisor(users.selectById(owner));
        if(status!=0 && status!=1) fail("Choose pending or confirmed sessions.");
        var references=slots.selectByCreatorIdsIncludingDeleted(List.of(owner));
        var result=new Page<UpcomingSessionVo>(query.getPageNum(),query.getPageQuantity());
        if(references.isEmpty()) return result;
        var page=bookings.selectPage(new Page<AssessmentBooking>(query.getPageNum(),query.getPageQuantity()),
                Wrappers.<AssessmentBooking>lambdaQuery().in(AssessmentBooking::getAssessmentSlotId,references.stream().map(AssessmentSlot::getId).toList())
                        .eq(AssessmentBooking::getStatus,status).orderByAsc(AssessmentBooking::getStartTime).orderByAsc(AssessmentBooking::getId));
        result.setTotal(page.getTotal());
        if(page.getRecords().isEmpty()) return result;
        var slotMap=slots.selectByIdsIncludingDeleted(page.getRecords().stream().map(AssessmentBooking::getAssessmentSlotId).distinct().toList()).stream().collect(Collectors.toMap(AssessmentSlot::getId,s->s));
        var studentIds=page.getRecords().stream().map(AssessmentBooking::getCreator).filter(Objects::nonNull).distinct().toList();
        var students=studentIds.isEmpty()?Map.<Long,User>of():users.selectByIds(studentIds).stream().collect(Collectors.toMap(User::getId,u->u));
        var now=LocalDateTime.now(clock);var records=new ArrayList<UpcomingSessionVo>();
        for(var booking:page.getRecords()) {
            var slot=slotMap.get(booking.getAssessmentSlotId());
            if(slot==null || !owner.equals(slot.getCreator())) continue;
            var view=new UpcomingSessionVo();BeanUtils.copyProperties(slot,view);
            view.setBookingId(booking.getId());view.setStudentId(booking.getCreator());view.setBookingVersion(booking.getVersion());
            view.setBookingStatus(status);view.setStatus(status);view.setStartTime(booking.getStartTime());view.setEndTime(booking.getEndTime());
            view.setTimeZone(clock.getZone().getId());
            var student=students.get(booking.getCreator());view.setStudentName(student==null?"Student unavailable":student.getName());
            view.setCreatorName(view.getStudentName());view.setCreatorAvatar(student==null?null:student.getAvatar());
            boolean valid=validDates(booking),future=valid && booking.getStartTime().isAfter(now);
            view.setDuration(valid?Duration.between(booking.getStartTime(),booking.getEndTime()).toMinutes()+"min":"Unknown");
            view.setCanApprove(status==0 && future && available(slot) && activeStudent(student));view.setCanDecline(status==0);
            view.setCanCancel(status==1 && future);view.setCanComplete(status==1 && valid && !booking.getEndTime().isAfter(now));
            view.setActionGuidance(!valid?"Session dates need checking before approval or completion.":status==0
                    ?(view.isCanApprove()?"Review the actual session details before approving.":"This request cannot be approved. Its start has passed, the publication is unavailable, or the student is inactive; check or decline it.")
                    :view.isCanComplete()?"The scheduled end has passed. Record completion only if the assessment actually took place.":future?"Completion becomes available after the scheduled end.":"This session is in progress. Completion becomes available after its scheduled end.");
            records.add(view);
        }
        result.setRecords(records);return result;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED,rollbackFor=Exception.class)
    public SupervisorBookingResult decide(Action action,Long slotId,Long bookingId,Integer expectedVersion,Boolean reopen) {
        if(action==null || slotId==null || ((action==Action.DECLINE || action==Action.CANCEL) && reopen==null)) fail("Check the decision details and refresh the session.");
        var locked=lock(bookingId,slotId);var booking=locked.booking();var slot=locked.slot();
        int target=action==Action.APPROVE?1:action==Action.DECLINE?3:4;
        int previous=action==Action.CANCEL?1:0;
        version(expectedVersion);
        if(Objects.equals(booking.getStatus(),target) && Objects.equals(booking.getVersion(),expectedVersion+1)) {
            if(action!=Action.APPROVE && Objects.equals(slot.getRepeatType(),0) && !Boolean.TRUE.equals(slot.getIsDelete()) && !Objects.equals(slot.getIsEnable(),reopen))
                fail("The decision was saved with a different availability setting, or the publication changed. Refresh to check its current state.");
            return receipt(booking,true);
        }
        unchanged(booking,expectedVersion,previous);
        if(action==Action.APPROVE) {
            if(!validDates(booking) || !booking.getStartTime().isAfter(LocalDateTime.now(clock))) fail("Only future requests can be approved. Refresh and check this request.");
            if(!available(slot) || !activeStudent(locked.student())) fail("The session publication or student account is unavailable. Check this request before approval.");
            if(bookings.countReplacementConflicts(booking.getCreator(),slot.getCreator(),slot.getId(),booking.getStartTime(),booking.getEndTime(),booking.getId())>0)
                fail("This request overlaps another booking. Resolve the conflict before approval.");
        } else if(action==Action.CANCEL && (!validDates(booking) || !booking.getStartTime().isAfter(LocalDateTime.now(clock))))
            fail("Only future confirmed sessions can be cancelled. Check the assessment status instead.");
        update(booking,expectedVersion,target,null);
        if(action!=Action.APPROVE) release(slot,reopen,false);
        return notifyAfterCommit(booking,slot,action==Action.APPROVE?"Booking approved":action==Action.DECLINE?"Booking declined":"Booking cancelled");
    }

    @Transactional(isolation=Isolation.READ_COMMITTED,rollbackFor=Exception.class)
    public SupervisorBookingResult complete(AssessmentCompletionDto request) { return result(request,false); }

    @Transactional(isolation=Isolation.READ_COMMITTED,rollbackFor=Exception.class)
    public SupervisorBookingResult correct(AssessmentCompletionDto request) { return result(request,true); }

    private SupervisorBookingResult result(AssessmentCompletionDto request,boolean correction) {
        if(request==null) fail("Check the assessment result.");
        version(request.getVersion());
        if(request.getScore()==null || !Double.isFinite(request.getScore()) || request.getScore()<0 || request.getScore()>100
                || request.getFeedback()==null || request.getFeedback().isBlank() || request.getFeedback().length()>255) fail("Enter a score from 0 to 100 and feedback of 1–255 characters.");
        var locked=lock(request.getBookingId(),null);var booking=locked.booking();var slot=locked.slot();
        var feedback=request.getFeedback().trim();
        boolean same=Objects.equals(booking.getScore(),request.getScore()) && feedback.equals(booking.getFeedback());
        if(Objects.equals(booking.getStatus(),2) && Objects.equals(booking.getVersion(),request.getVersion()+1) && same) return receipt(booking,true);
        unchanged(booking,request.getVersion(),correction?2:1);
        if(correction) {
            if(booking.getCompletedTime()==null) fail("This historical result has no completion date. Contact support before correcting it.");
            if(same) return receipt(booking,true);
        } else {
            if(!validDates(booking) || booking.getEndTime().isAfter(LocalDateTime.now(clock))) fail("Complete an assessment only after its scheduled end and after it took place.");
            booking.setCompletedTime(LocalDateTime.now(clock));
        }
        booking.setScore(request.getScore());booking.setFeedback(feedback);
        update(booking,request.getVersion(),2,booking.getCompletedTime());
        if(!correction) release(slot,true,true);
        return notifyAfterCommit(booking,slot,correction?"Assessment result corrected":"Assessment result available");
    }

    private record Locked(AssessmentBooking booking,AssessmentSlot slot,User student) { }
    private Locked lock(Long bookingId,Long requestedSlot) {
        Long owner=supervisorId();activeSupervisor(users.selectById(owner));
        var initial=bookingId==null?null:bookings.selectById(bookingId);
        var initialSlot=initial==null?null:slots.selectIncludingDeleted(initial.getAssessmentSlotId());
        if(initialSlot==null || !owner.equals(initialSlot.getCreator()) || (requestedSlot!=null && !requestedSlot.equals(initialSlot.getId()))) denied();
        if(initial.getCreator()==null || owner.equals(initial.getCreator())) fail("This booking's participants need checking.");
        var student=users.lockBookingParticipant(initial.getCreator());
        if(student!=null && !Objects.equals(student.getRoleId(),4L)) fail("This booking's student role needs checking.");
        activeSupervisor(users.lockBookingParticipant(owner));
        var slot=slots.lockForBooking(initialSlot.getId());var booking=bookings.lockForManagement(bookingId);
        if(slot==null || booking==null || !owner.equals(slot.getCreator()) || !slot.getId().equals(booking.getAssessmentSlotId()) || !initial.getCreator().equals(booking.getCreator())) denied();
        return new Locked(booking,slot,student);
    }

    private void update(AssessmentBooking booking,int expected,int status,LocalDateTime completed) {
        var change=Wrappers.<AssessmentBooking>lambdaUpdate().eq(AssessmentBooking::getId,booking.getId()).eq(AssessmentBooking::getVersion,expected)
                .eq(AssessmentBooking::getStatus,booking.getStatus()).set(AssessmentBooking::getStatus,status)
                .set(AssessmentBooking::getVersion,expected+1).set(AssessmentBooking::getEditTime,LocalDateTime.now(clock));
        if(completed!=null) change.set(AssessmentBooking::getCompletedTime,completed).set(AssessmentBooking::getScore,booking.getScore()).set(AssessmentBooking::getFeedback,booking.getFeedback());
        if(bookings.update(change)!=1) fail("The booking changed. Refresh before making another decision.");
        booking.setStatus(status);booking.setVersion(expected+1);
    }

    private void release(AssessmentSlot slot,Boolean reopen,boolean completed) {
        if(Boolean.TRUE.equals(slot.getIsDelete())) return; // The owned booking remains resolvable after its template was archived.
        boolean recurring=!Objects.equals(slot.getRepeatType(),0);
        var change=Wrappers.<AssessmentSlot>lambdaUpdate().eq(AssessmentSlot::getId,slot.getId());
        if(recurring) {
            if(Objects.equals(slot.getStatus(),0)) return;
            change.set(AssessmentSlot::getStatus,0); // Release this occurrence without closing/enabling the entire series.
        } else {
            change.set(AssessmentSlot::getStatus,completed?2:0);
            // next_available_time is NOT NULL in the original schema; completed status prevents further booking.
            if(!completed) change.set(AssessmentSlot::getIsEnable,reopen).set(AssessmentSlot::getNextAvailableTime,slot.getStartTime());
        }
        change.set(AssessmentSlot::getVersion,slot.getVersion()+1);
        if(slots.update(change)!=1) fail("The session changed. No decision was saved; refresh and retry.");
    }

    private SupervisorBookingResult notifyAfterCommit(AssessmentBooking booking,AssessmentSlot slot,String event) {
        var notices=new ArrayList<NotificationDto>();
        String text=event+": "+slot.getType()+" on "+booking.getStartTime()+" ("+clock.getZone()+"). Reference: "+booking.getId()+"."
                +(booking.getStatus()==2?" View Assessment History for the result.":"");
        for(Long receiver:List.of(booking.getCreator(),slot.getCreator())) {
            var message=new Message();message.setCreator(slot.getCreator());message.setReceiver(receiver);message.setContent(text);
            message.setCreateTime(LocalDateTime.now(clock));message.setIsDelete(false);message.setIsRead(false);message.setIsDelivery(false);
            if(messages.insert(message)!=1) fail("The decision and its notifications could not be saved. Retry the same decision.");
            var notice=new NotificationDto();notice.setMessageId(message.getId());notice.setSenderId(slot.getCreator());notice.setReceiverId(receiver);
            notice.setEventName("Notification");notice.setEventContent(text);notice.setIsSendEmail(false);notices.add(notice);
        }
        var result=receipt(booking,false);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try { for(var notice:notices) redis.publish(RedisKeyConstant.SSE_NOTIFY_CHANNEL,notice); }
                catch(RuntimeException failure) { result.setNotificationStatus("LIVE_DELAYED");log.warn("Supervisor decision for booking {} saved; live alerts delayed",booking.getId()); }
            }
        });return result;
    }

    private SupervisorBookingResult receipt(AssessmentBooking booking,boolean replayed) {
        var result=new SupervisorBookingResult();result.setBookingId(booking.getId().toString());result.setStatus(booking.getStatus());
        result.setVersion(booking.getVersion());result.setCompletedTime(booking.getCompletedTime());result.setReplayed(replayed);return result;
    }
    private boolean validDates(AssessmentBooking b) { return b.getStartTime()!=null && b.getEndTime()!=null && b.getEndTime().isAfter(b.getStartTime()); }
    private boolean available(AssessmentSlot slot) { return !Boolean.TRUE.equals(slot.getIsDelete()) && Boolean.TRUE.equals(slot.getIsEnable()) && List.of(0,1).contains(slot.getStatus()); }
    private boolean activeStudent(User student) { return student!=null && Objects.equals(student.getRoleId(),4L) && Boolean.TRUE.equals(student.getIsEnable()) && !Boolean.TRUE.equals(student.getIsCanceled()); }
    private Long supervisorId() { var actor=CurrentUserUtils.getUser();if(actor==null || actor.getId()==null || !"PS".equals(actor.getRoleKey())) denied();return actor.getId(); }
    private void activeSupervisor(User user) { if(user==null || !Objects.equals(user.getRoleId(),3L) || !Boolean.TRUE.equals(user.getIsEnable()) || Boolean.TRUE.equals(user.getIsCanceled())) denied(); }
    private void version(Integer version) { if(version==null || version<0 || version==Integer.MAX_VALUE) fail("Reload the booking to obtain its current version."); }
    private void unchanged(AssessmentBooking booking,int version,int status) { if(!Objects.equals(booking.getVersion(),version) || !Objects.equals(booking.getStatus(),status)) fail("The booking status or result changed. Refresh before making another decision."); }
    private void denied() { throw new BusinessException(ResponseEnum.NO_PERMISSION.getCode(),"This booking is unavailable for your account."); }
    private void fail(String message) { throw new BusinessException(ResponseEnum.ILLEGAL_PARAM.getCode(),message); }
}
