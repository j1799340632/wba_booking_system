package edu.unsw.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.constant.RedisKeyConstant;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.core.utils.RedissonUtils;
import edu.unsw.dao.mapper.*;
import edu.unsw.domain.dto.*;
import edu.unsw.domain.entity.*;
import edu.unsw.domain.vo.*;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.*;

/** Owner-scoped, durable cancellation and atomic cancel-and-rebook. */
@Service
public class BookingManagementService {
    private final AssessmentBookingMapper bookings;
    private final AssessmentSlotMapper slots;
    private final UserMapper users;
    private final MessageMapper messages;
    private final BookingSubmissionService submissions;
    private final RedissonUtils redis;
    private final Clock clock;
    private static final String PREFIX = "booking:management:";

    public BookingManagementService(AssessmentBookingMapper bookings, AssessmentSlotMapper slots, UserMapper users,
            MessageMapper messages, BookingSubmissionService submissions, RedissonUtils redis,
            @Qualifier("bookingClock") Clock clock) {
        this.bookings=bookings; this.slots=slots; this.users=users; this.messages=messages;
        this.submissions=submissions; this.redis=redis; this.clock=clock;
    }

    @Getter @Setter
    public static class Draft {
        private BookingManagementRequest request;
        private String originalFingerprint;
        private String replacementFingerprint;
        private LocalDateTime expiresAt;
    }

    public String restriction(AssessmentBooking booking) {
        if (!Set.of(0,1).contains(booking.getStatus())) return "Only pending or confirmed bookings can be changed.";
        if (booking.getStartTime() == null || !booking.getStartTime().isAfter(LocalDateTime.now(clock))) return "This session has already started.";
        if (Objects.equals(booking.getStatus(),1) && Duration.between(clock.instant(), booking.getStartTime().atZone(clock.getZone()).toInstant()).compareTo(Duration.ofHours(48)) < 0)
            return "Confirmed bookings cannot be cancelled or rescheduled within 48 hours of the session.";
        return null;
    }

    public BookingVo ownBooking(Long id, boolean mutable) {
        var booking = owned(id);
        if (mutable) changeable(booking);
        return view(booking);
    }

    @Transactional(readOnly=true, isolation=Isolation.READ_COMMITTED)
    public List<BookingVo> list(AssessmentSlotQueryDto query, Integer status) {
        Long owner = BookingSubmissionService.studentId();
        var filter=Wrappers.<AssessmentBooking>lambdaQuery().eq(AssessmentBooking::getCreator,owner)
                .eq(status != null, AssessmentBooking::getStatus,status)
                .ge(query.getStartTime()!=null,AssessmentBooking::getStartTime,query.getStartTime())
                .le(query.getEndTime()!=null,AssessmentBooking::getEndTime,query.getEndTime())
                .orderByAsc(AssessmentBooking::getStartTime).orderByAsc(AssessmentBooking::getId);
        var result=new ArrayList<BookingVo>();
        for (var booking: bookings.selectList(filter)) {
            var slot=slots.selectIncludingDeleted(booking.getAssessmentSlotId());
            if (slot == null || !matches(query, booking, slot)) continue;
            result.add(view(booking,slot));
            if (result.size()==21) break;
        }
        return result;
    }

    @Transactional(readOnly=true, isolation=Isolation.READ_COMMITTED)
    public BookingManagementVo prepare(BookingManagementRequest request) {
        BookingSubmissionService.studentId();
        if (request == null || request.getBookingId()==null || request.getAction()==null || request.getBookingVersion()==null) fail("Select a booking and review it again.");
        var booking=owned(request.getBookingId()); changeable(booking);
        if (!Objects.equals(request.getBookingVersion(),booking.getVersion())) fail("Booking details changed. Refresh and review again.");
        if ((request.getAction()==BookingManagementRequest.Action.RESCHEDULE) != (request.getReplacement()!=null)) fail("Select a replacement session before reviewing a reschedule.");
        var originalSlot=slots.selectIncludingDeleted(booking.getAssessmentSlotId());
        if (originalSlot==null) fail("Session details are unavailable. Contact your supervisor.");
        var draft=new Draft();
        // Independent copy; never accept business details on the confirmation endpoint.
        var copy=new BookingManagementRequest(); copy.setBookingId(booking.getId()); copy.setBookingVersion(booking.getVersion()); copy.setAction(request.getAction());
        var result=new BookingManagementVo();
        if (request.getReplacement()!=null) {
            var source=request.getReplacement(); var target=new AssessmentBookingSaveDto();
            target.setAssessmentSlotId(source.getAssessmentSlotId()); target.setSlotVersion(source.getSlotVersion());
            target.setStartTime(source.getStartTime()); target.setEndTime(source.getEndTime());
            if (Objects.equals(booking.getAssessmentSlotId(),target.getAssessmentSlotId()) && Objects.equals(booking.getStartTime(),target.getStartTime())) fail("Choose a different session for rescheduling.");
            var review=submissions.reviewReplacement(target,booking.getId());
            draft.setReplacementFingerprint(review.fingerprint()); result.setReplacement(review.candidate()); copy.setReplacement(target);
        }
        draft.setRequest(copy); draft.setOriginalFingerprint(fingerprint(booking,originalSlot));
        draft.setExpiresAt(LocalDateTime.now(clock).plusMinutes(10));
        String id=UUID.randomUUID().toString();
        redis.setObject(PREFIX+booking.getCreator()+":"+id,draft,Duration.ofMinutes(10));
        result.setDraftId(id); result.setAction(copy.getAction().name()); result.setOriginal(view(booking,originalSlot));
        result.setExpiresAt(draft.getExpiresAt()); result.setTimeZone(clock.getZone().getId()); return result;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED, rollbackFor=Exception.class)
    public BookingManagementVo confirm(BookingConfirmationRequest request) {
        Long owner=BookingSubmissionService.studentId();
        if (request==null || !Boolean.TRUE.equals(request.getConfirmed()) || request.getDraftId()==null || !request.getDraftId().matches("[a-f0-9-]{36}")) fail("Explicit confirmation of a valid review is required.");
        lockStudent(owner);
        String key="manage_"+request.getDraftId();
        var existing=bookings.findManagement(owner,key);
        if (existing!=null) return receipt(existing,true);
        Draft draft=redis.getObject(PREFIX+owner+":"+request.getDraftId());
        if (draft==null || draft.getExpiresAt()==null || !draft.getExpiresAt().isAfter(LocalDateTime.now(clock)))
            throw new BusinessException(ResponseEnum.VERIFICATION_EXPIRED.getCode(),"This review expired. Select and review the booking again.");
        return execute(draft.getRequest(),key,draft.getOriginalFingerprint(),draft.getReplacementFingerprint());
    }

    @Transactional(isolation=Isolation.READ_COMMITTED, rollbackFor=Exception.class)
    public boolean cancelDirect(Long id) {
        Long owner=BookingSubmissionService.studentId(); lockStudent(owner);
        String key="direct_cancel_"+id;
        if (bookings.findManagement(owner,key)!=null) return true;
        var booking=owned(id);
        var request=new BookingManagementRequest(); request.setBookingId(id); request.setBookingVersion(booking.getVersion()); request.setAction(BookingManagementRequest.Action.CANCEL);
        execute(request,key,null,null); return true;
    }

    private BookingManagementVo execute(BookingManagementRequest request, String key, String originalFingerprint, String replacementFingerprint) {
        var initial=owned(request.getBookingId());
        var oldSlot=slots.selectIncludingDeleted(initial.getAssessmentSlotId());
        if (oldSlot==null) fail("The original session details are unavailable.");
        var target=request.getReplacement()==null ? null : slots.selectById(request.getReplacement().getAssessmentSlotId());
        if (request.getReplacement()!=null && target==null) fail("The replacement session was removed. Your original booking is unchanged.");
        // All management operations take participant and slot locks in the same order.
        var mentors=new TreeSet<Long>(); mentors.add(oldSlot.getCreator()); if(target!=null) mentors.add(target.getCreator());
        for (Long mentor:mentors) users.lockBookingParticipant(mentor);
        var slotIds=new TreeSet<Long>(); slotIds.add(oldSlot.getId()); if(target!=null) slotIds.add(target.getId());
        for (Long slotId:slotIds) slots.lockForBooking(slotId);
        oldSlot=slots.selectIncludingDeleted(oldSlot.getId());
        var booking=bookings.lockForManagement(initial.getId());
        if (booking==null || !Objects.equals(booking.getCreator(),BookingSubmissionService.studentId())) fail("Booking details changed. Refresh and review again.");
        changeable(booking);
        if (!Objects.equals(request.getBookingVersion(),booking.getVersion()) || (originalFingerprint!=null && !originalFingerprint.equals(fingerprint(booking,oldSlot)))) fail("Booking details changed after review. Refresh and review again.");
        // Conditional update protects against supervisor status changes as well as other student requests.
        int changed=bookings.update(Wrappers.<AssessmentBooking>lambdaUpdate().eq(AssessmentBooking::getId,booking.getId())
                .eq(AssessmentBooking::getVersion,booking.getVersion()).eq(AssessmentBooking::getStatus,booking.getStatus())
                .set(AssessmentBooking::getStatus,4).set(AssessmentBooking::getVersion,booking.getVersion()+1)
                .set(AssessmentBooking::getEditTime,LocalDateTime.now(clock)).set(AssessmentBooking::getManagementKey,key));
        if(changed!=1) fail("Booking details changed. Review again.");
        // The old cancellation is uncommitted: target validation/insert failure rolls the whole transaction back.
        BookingVo replacement=null;
        if(request.getReplacement()!=null) {
            request.getReplacement().setRequestKey(key);
            replacement=submissions.submit(request.getReplacement(),replacementFingerprint);
            if(replacement.isReplayed()) fail("This replacement request was already used. Check My Bookings.");
            bookings.update(Wrappers.<AssessmentBooking>lambdaUpdate().eq(AssessmentBooking::getId,booking.getId()).set(AssessmentBooking::getReplacementBookingId,replacement.getBookingId()));
        }
        // A recurring template stays available; occupancy is calculated per actual occurrence.
        if (bookings.selectCount(Wrappers.<AssessmentBooking>lambdaQuery().eq(AssessmentBooking::getAssessmentSlotId,oldSlot.getId()).in(AssessmentBooking::getStatus,List.of(0,1)))==0 || !Objects.equals(oldSlot.getRepeatType(),0))
            slots.update(Wrappers.<AssessmentSlot>lambdaUpdate().eq(AssessmentSlot::getId,oldSlot.getId()).set(AssessmentSlot::getStatus,0));
        var notices=persistCancellation(booking,oldSlot,replacement);
        var result=receipt(bookings.selectById(booking.getId()),false);
        result.setNewBooking(replacement);
        var replacementReceipt=replacement;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
            @Override public void afterCommit(){
                try { for(var notice:notices) redis.publish(RedisKeyConstant.SSE_NOTIFY_CHANNEL,notice); }
                catch(RuntimeException error) { result.setNotificationStatus("LIVE_DELAYED"); LoggerFactory.getLogger(BookingManagementService.class).warn("Management saved; live notification unavailable for booking {}",booking.getId()); }
                if(replacementReceipt!=null && "LIVE_DELAYED".equals(replacementReceipt.getNotificationStatus())) result.setNotificationStatus("LIVE_DELAYED");
            }
        });
        return result;
    }

    private BookingManagementVo receipt(AssessmentBooking original,boolean replayed) {
        var result=new BookingManagementVo(); result.setOriginal(view(original)); result.setTimeZone(clock.getZone().getId());
        result.setAction(original.getReplacementBookingId()==null?"CANCEL":"RESCHEDULE");
        if(original.getReplacementBookingId()!=null) {
            var replacement=bookings.selectById(original.getReplacementBookingId());
            if(replacement!=null) result.setNewBooking(view(replacement));
        }
        result.setReplayed(replayed); result.setNotificationStatus("SAVED"); return result;
    }
    private List<NotificationDto> persistCancellation(AssessmentBooking original,AssessmentSlot slot,BookingVo replacement) {
        var result=new ArrayList<NotificationDto>();
        String text="Booking cancelled: "+slot.getType()+" on "+original.getStartTime()+" ("+clock.getZone()+"). Reference: "+original.getId()+"."
                +(replacement==null?"":" Replacement request: "+replacement.getBookingId()+"; awaiting supervisor approval.");
        for(Long receiver:new LinkedHashSet<>(List.of(original.getCreator(),slot.getCreator()))) {
            var message=new Message(); message.setCreator(original.getCreator()); message.setReceiver(receiver); message.setContent(text);
            message.setCreateTime(LocalDateTime.now(clock)); message.setIsDelete(false); message.setIsRead(false); message.setIsDelivery(false);
            if(messages.insert(message)!=1) fail("Could not save notifications. No changes were committed. Retry this confirmation.");
            var notice=new NotificationDto(); notice.setMessageId(message.getId()); notice.setSenderId(original.getCreator()); notice.setReceiverId(receiver);
            notice.setEventName("Notification"); notice.setEventContent(text); notice.setIsSendEmail(false); result.add(notice);
        }
        return result;
    }
    private boolean matches(AssessmentSlotQueryDto q,AssessmentBooking b,AssessmentSlot s) {
        return (q.getHospital()==null || q.getHospital().equalsIgnoreCase(s.getHospital()))
                && (q.getDepartment()==null || q.getDepartment().equalsIgnoreCase(s.getDepartment()))
                && (q.getType()==null || q.getType().equalsIgnoreCase(s.getType()))
                && (q.getSupervisorId()==null || q.getSupervisorId().equals(s.getCreator()))
                && (q.getWeekdays()==null || q.getWeekdays().isEmpty() || q.getWeekdays().contains(b.getStartTime().getDayOfWeek().getValue()))
                && (q.getEarliestTime()==null || !b.getStartTime().toLocalTime().isBefore(q.getEarliestTime()))
                && (q.getLatestTime()==null || !b.getEndTime().toLocalTime().isAfter(q.getLatestTime()));
    }
    private AssessmentBooking owned(Long id) {
        Long owner=BookingSubmissionService.studentId(); var booking=id==null?null:bookings.selectById(id);
        if(booking==null || !owner.equals(booking.getCreator())) throw new BusinessException(ResponseEnum.NO_PERMISSION.getCode(),"This booking is unavailable for your account.");
        return booking;
    }
    private BookingVo view(AssessmentBooking booking) {
        var slot=slots.selectIncludingDeleted(booking.getAssessmentSlotId());
        if(slot==null) { var result=new BookingVo(); result.setBookingId(booking.getId()); result.setStatus(booking.getStatus()); result.setManagementRestriction("Session details unavailable."); return result; }
        return view(booking,slot);
    }
    private BookingVo view(AssessmentBooking booking,AssessmentSlot slot) {
        var result=submissions.view(booking,slot,users.selectById(slot.getCreator()));
        result.setManagementRestriction(restriction(booking)); result.setCanManage(result.getManagementRestriction()==null); return result;
    }
    private void changeable(AssessmentBooking booking) { String reason=restriction(booking); if(reason!=null) fail(reason); }
    private void lockStudent(Long id) {
        var user=users.lockBookingParticipant(id);
        if(user==null || !Objects.equals(user.getRoleId(),4L) || !Boolean.TRUE.equals(user.getIsEnable()) || Boolean.TRUE.equals(user.getIsCanceled())) throw new BusinessException(ResponseEnum.NO_PERMISSION);
    }
    private String fingerprint(AssessmentBooking booking,AssessmentSlot slot) {
        var mentor=users.selectById(slot.getCreator());
        return BookingSubmissionService.hash(booking.getId(),booking.getCreator(),booking.getVersion(),booking.getStatus(),booking.getStartTime(),booking.getEndTime(),slot.getId(),slot.getCreator(),
                slot.getType(),slot.getHospital(),slot.getDepartment(),slot.getLocation(),mentor==null?null:mentor.getName());
    }
    private static void fail(String text) { throw new BusinessException(ResponseEnum.SLOT_CONFLICT.getCode(),text); }
}
