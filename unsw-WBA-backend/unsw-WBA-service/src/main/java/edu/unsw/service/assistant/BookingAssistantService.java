package edu.unsw.service.assistant;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.utils.mdc.MdcUtils;
import edu.unsw.dao.mapper.UserMapper;
import edu.unsw.domain.dto.AssessmentSlotQueryDto;
import edu.unsw.domain.dto.BookingAssistantRequest;
import edu.unsw.domain.dto.AssessmentBookingSaveDto;
import edu.unsw.domain.dto.BookingReplacementQueryDto;
import edu.unsw.domain.entity.User;
import edu.unsw.domain.vo.AssessmentSlotVo;
import edu.unsw.domain.vo.BookingAssistantResult;
import edu.unsw.domain.vo.BookingAssistantResult.Candidate;
import edu.unsw.domain.vo.BookingAssistantResult.Status;
import edu.unsw.service.DictionaryService;
import edu.unsw.service.StudentSlotSearchService;
import edu.unsw.service.BookingManagementService;
import edu.unsw.service.BookingSubmissionService;
import edu.unsw.service.AssessmentPlanningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class BookingAssistantService {
    private static final Logger LOG = LoggerFactory.getLogger(BookingAssistantService.class);
    private final BookingIntentParser parser;
    private final DictionaryService dictionaries;
    private final UserMapper users;
    private final StudentSlotSearchService search;
    private final Clock clock;
    private final BookingManagementService management;
    private final BookingSubmissionService submissions;
    private final AssessmentPlanningService planning;

    public BookingAssistantService(BookingIntentParser parser, DictionaryService dictionaries, UserMapper users,
                                   StudentSlotSearchService search, @Qualifier("bookingClock") Clock clock,
                                   BookingManagementService management, BookingSubmissionService submissions, AssessmentPlanningService planning) {
        this.parser = parser;
        this.dictionaries = dictionaries;
        this.users = users;
        this.search = search;
        this.clock = clock;
        this.management=management; this.submissions=submissions;
        this.planning=planning;
    }

    public BookingAssistantResult search(BookingAssistantRequest request) {
        long started = System.nanoTime();
        BookingAssistantResult result = new BookingAssistantResult();
        result.setTraceId(StringUtils.hasText(MdcUtils.getTraceId()) ? MdcUtils.getTraceId() : UUID.randomUUID().toString());
        result.setTimeZone(clock.getZone().getId());
        if(request.getRescheduleBookingId()!=null) management.ownBooking(request.getRescheduleBookingId(),true);
        try {
            if (!parser.available()) {
                return state(result, Status.UNAVAILABLE, "AI search is currently unavailable. Please use the filters below.");
            }
            LocalDateTime now = LocalDateTime.now(clock);
            var hospitals = dictionaries.getEnabledHospitals().stream().map(item -> item.getName()).toList();
            var departments = dictionaries.getEnabledDepartments().stream().map(item -> item.getName()).toList();
            var types = dictionaries.getEnabledWbaTypes().stream().map(item -> item.getName()).toList();
            BookingIntent intent;
            try {
                intent = parser.parse(new BookingIntentParser.Context(request.getMessage(),
                        request.getHistory() == null ? List.of() : List.copyOf(request.getHistory()), now,
                        clock.getZone().getId(), hospitals, departments, types));
            } catch (RuntimeException error) {
                return state(result, Status.UNAVAILABLE, "AI search could not process that request. Please try again or use the filters below.");
            }
            if (intent.action() == BookingIntent.Action.UNSUPPORTED) {
                return state(result, Status.UNSUPPORTED, "I can find sessions and help view, cancel or reschedule your own bookings, one at a time. Approval requires a supervisor.");
            }
            if (intent.action() == BookingIntent.Action.CLARIFY) {
                return state(result, Status.CLARIFY, intent.question() == null ? "What date and assessment session would you like to find?" : intent.question());
            }
            if(intent.action()==BookingIntent.Action.PLAN_ASSESSMENTS) {
                if(request.getRescheduleBookingId()!=null) return state(result,Status.CLARIFY,"Finish selecting a replacement session, or use Back to bookings before opening your assessment plan.");
                var plan=planning.summary(); result.setAssessmentPlan(plan);
                return state(result,Status.PLAN,plan.isConfigured()?"Your saved targets have "+plan.getRemaining()+" assessments still to complete and "+plan.getToRequest()+" additional sessions to request. Pending bookings are not completed assessments."
                        :"Set your assessment targets and dates first. I will calculate the gaps and weekly pacing from your own records.");
            }
            if (intent.action()!=BookingIntent.Action.SEARCH) {
                if(request.getRescheduleBookingId()!=null) return state(result,Status.CLARIFY,"Please describe the replacement date, assessment and supervisor. Use Back to bookings to select another original booking.");
                var query=conditions(intent,hospitals,departments,types,false);
                var matches=management.list(query,intent.bookingStatus());
                result.setConditions(query); result.setBookingAction(intent.action().name());
                result.setHasMoreBookings(matches.size()>20); result.setBookings(matches.stream().limit(20).toList());
                if(intent.replacement()!=null) result.setReplacementConditions(conditions(intent.replacement(),hospitals,departments,types,true));
                return state(result,Status.BOOKINGS,matches.isEmpty()?"No bookings match these conditions. Try another date or check My Bookings."
                        :"Select your booking below. Changes require review and confirmation."+(matches.size()>20?" Showing the first 20 matches; add a date or supervisor to narrow the list.":""));
            }
            AssessmentSlotQueryDto query = new AssessmentSlotQueryDto();
            query.setHospital(resolveName(intent.hospital(), hospitals, "hospital"));
            query.setDepartment(resolveName(intent.department(), departments, "department"));
            query.setType(resolveName(intent.type(), types, "assessment type"));
            query.setStartTime(intent.startTime() == null ? now : intent.startTime());
            query.setEndTime(intent.endTime() == null ? query.getStartTime().plusDays(30) : intent.endTime());
            query.setEarliestTime(intent.earliestTime());
            query.setLatestTime(intent.latestTime());
            query.setWeekdays(intent.weekdays());
            query.setIsOrderByAvailableTime(true);
            query.setPageQuantity(-1); // Rank the full filtered search, including preferences beyond the first page.
            User supervisor = resolveSupervisor(intent.supervisorName());
            if (supervisor != null && intent.supervisorMode() == BookingIntent.SupervisorMode.REQUIRED) {
                query.setSupervisorId(supervisor.getId());
                query.setSupervisorName(supervisor.getName());
            }
            Long preferredId = supervisor != null && intent.supervisorMode() == BookingIntent.SupervisorMode.PREFERRED
                    ? supervisor.getId() : null;
            if (preferredId != null) result.setPreferredSupervisor(supervisor.getName());
            result.setConditions(query);
            try {
                result.setCandidates(findCandidates(query,preferredId,request.getRescheduleBookingId(),3));
            } catch (BusinessException error) {
                throw new Clarification(error.getErrorMsg());
            }
            return finishSearch(result,query,preferredId,request.getRescheduleBookingId(),
                    "These sessions match your search. No booking has been created.");
        } catch (Clarification question) {
            return state(result, Status.CLARIFY, question.getMessage());
        } catch (RuntimeException error) {
            return state(result, Status.UNAVAILABLE, "Search is temporarily unavailable. Please try the standard filters below.");
        } finally {
            result.setElapsedMs((System.nanoTime() - started) / 1_000_000);
            LOG.info("booking_assistant trace={} status={} elapsedMs={} candidates={}",
                    result.getTraceId(), result.getStatus(), result.getElapsedMs(), result.getCandidates().size());
        }
    }

    public BookingAssistantResult replacementOptions(BookingReplacementQueryDto request) {
        var original=management.ownBooking(request.getBookingId(),true);
        var result=new BookingAssistantResult(); result.setTimeZone(clock.getZone().getId());
        result.setTraceId(UUID.randomUUID().toString());
        var query=request.getConditions(); query.setPageQuantity(-1); query.setIsOrderByAvailableTime(true);
        query.setPageNum(1);
        if(query.getStartTime()==null) query.setStartTime(LocalDateTime.now(clock));
        if(query.getEndTime()==null) query.setEndTime(query.getStartTime().plusDays(30));
        if(query.getType()==null) query.setType(original.getWbaType());
        result.setPreferredSupervisor(query.getPreferredSupervisorName());
        result.setConditions(query);
        result.setCandidates(findCandidates(query,query.getPreferredSupervisorId(),request.getBookingId(),3));
        return finishSearch(result,query,query.getPreferredSupervisorId(),request.getBookingId(),
                "Choose a replacement. Your original booking stays active until you confirm a successful reschedule.");
    }

    private BookingAssistantResult finishSearch(BookingAssistantResult result,AssessmentSlotQueryDto query,
            Long preferredId,Long originalId,String success) {
        if(!result.getCandidates().isEmpty()) return state(result,Status.READY,success);
        try {
            var alternatives=new ArrayList<BookingAssistantResult.Alternative>();
            var seen=new HashSet<String>();
            for(var option:BookingSearchAlternatives.options(query)) {
                if(!option.conditions().getEndTime().isAfter(LocalDateTime.now(clock))) continue;
                var candidates=findCandidates(option.conditions(),preferredId,originalId,2,seen);
                if(candidates.isEmpty()) continue;
                var alternative=new BookingAssistantResult.Alternative();
                alternative.setKind(option.kind()); alternative.setTitle(option.title()); alternative.setExplanation(option.explanation());
                alternative.setConditions(option.conditions()); alternative.setCandidates(candidates); alternatives.add(alternative);
                if(alternatives.size()==3) break;
            }
            result.setAlternatives(alternatives);
        } catch(RuntimeException unavailable) {
            result.setAlternatives(List.of());
            return state(result,Status.NO_RESULTS,"No bookable sessions match your original conditions. Alternative suggestions could not be loaded; try again or change the filters.");
        }
        return state(result,Status.NO_RESULTS,result.getAlternatives().isEmpty()
                ? "No bookable sessions match your conditions or the single-condition alternatives checked. Try another date or discuss availability with your supervisor."
                : "No bookable sessions match all your original conditions. These alternatives require changing one condition; review that change before requesting a session.");
    }

    private List<Candidate> findCandidates(AssessmentSlotQueryDto query,Long preferredId,Long originalId,int limit) {
        return findCandidates(query,preferredId,originalId,limit,new HashSet<>());
    }

    private List<Candidate> findCandidates(AssessmentSlotQueryDto query,Long preferredId,Long originalId,int limit,Set<String> seen) {
        var options=new ArrayList<Candidate>();
        for(var slot:search.search(query).getRecords()) {
            if(slot.getId()==null || slot.getVersion()==null || slot.getAvailableTimeList()==null) continue;
            for(var time:slot.getAvailableTimeList()) {
                if(time.size()==2 && time.getFirst()!=null && time.getLast()!=null) options.add(candidate(slot,time,preferredId));
            }
        }
        options.sort(Comparator.comparing((Candidate item)->!item.isPreferredSupervisor()).thenComparing(Candidate::getStartTime).thenComparing(Candidate::getSlotId));
        var result=new ArrayList<Candidate>();
        for(var item:options) {
            String key=item.getSlotId()+"/"+item.getStartTime()+"/"+item.getEndTime();
            if(seen.contains(key)) continue;
            if(originalId!=null) {
                if(!validReplacement(item,originalId)) continue;
            } else {
                var selection=new AssessmentBookingSaveDto(); selection.setAssessmentSlotId(Long.valueOf(item.getSlotId())); selection.setSlotVersion(item.getSlotVersion());
                selection.setStartTime(item.getStartTime()); selection.setEndTime(item.getEndTime());
                try { submissions.review(selection); } catch(BusinessException unavailable) { continue; }
            }
            seen.add(key); result.add(item);
            if(result.size()==limit) break;
        }
        return result;
    }

    private boolean validReplacement(Candidate item,Long originalId) {
        var request=new AssessmentBookingSaveDto(); request.setAssessmentSlotId(Long.valueOf(item.getSlotId())); request.setSlotVersion(item.getSlotVersion());
        request.setStartTime(item.getStartTime()); request.setEndTime(item.getEndTime());
        var original=management.ownBooking(originalId,true);
        if(Objects.equals(original.getSlotId(),request.getAssessmentSlotId()) && Objects.equals(original.getStartTime(),request.getStartTime())) return false;
        try { submissions.reviewReplacement(request,originalId); return true; }
        catch(BusinessException unavailable) { return false; }
    }

    private AssessmentSlotQueryDto conditions(BookingIntent intent,List<String> hospitals,List<String> departments,List<String> types,boolean defaults) {
        var query=new AssessmentSlotQueryDto();
        query.setHospital(resolveName(intent.hospital(),hospitals,"hospital")); query.setDepartment(resolveName(intent.department(),departments,"department"));
        query.setType(resolveName(intent.type(),types,"assessment type"));
        query.setStartTime(intent.startTime()==null && defaults?LocalDateTime.now(clock):intent.startTime());
        query.setEndTime(intent.endTime()==null && defaults?query.getStartTime().plusDays(30):intent.endTime());
        if(query.getStartTime()!=null && query.getEndTime()!=null && !query.getStartTime().isBefore(query.getEndTime())) throw new Clarification("Please give an end date after the start date.");
        query.setEarliestTime(intent.earliestTime()); query.setLatestTime(intent.latestTime()); query.setWeekdays(intent.weekdays());
        var mentor=resolveSupervisor(intent.supervisorName());
        if(mentor!=null) {
            if(defaults && intent.supervisorMode()==BookingIntent.SupervisorMode.PREFERRED) { query.setPreferredSupervisorId(mentor.getId()); query.setPreferredSupervisorName(mentor.getName()); }
            else { query.setSupervisorId(mentor.getId()); query.setSupervisorName(mentor.getName()); }
        }
        return query;
    }

    private String resolveName(String value, List<String> dictionary, String label) {
        if (value == null) return null;
        var exact = dictionary.stream().filter(name -> name.equalsIgnoreCase(value)).toList();
        if (exact.size() == 1) return exact.getFirst();
        var matches = dictionary.stream().filter(name -> name.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT))).toList();
        if (matches.size() == 1) return matches.getFirst();
        throw new Clarification("Please give the full " + label + " name from the filters below; '" + value + "' could not be matched uniquely.");
    }

    private User resolveSupervisor(String name) {
        if (name == null) return null;
        if (name.matches(".*[%_\\\\].*")) throw new Clarification("Please enter the supervisor's full name.");
        // Role 3 is the supervisor role also used by the existing frontend route map.
        List<User> matches = users.selectList(Wrappers.<User>lambdaQuery()
                .eq(User::getRoleId, 3L).eq(User::getIsEnable, true).eq(User::getIsCanceled, false)
                .like(User::getName, name).select(User::getId, User::getName));
        var exact = matches.stream().filter(user -> name.equalsIgnoreCase(user.getName())).toList();
        var resolved = exact.isEmpty() ? matches : exact;
        if (resolved.size() != 1) {
            throw new Clarification(resolved.isEmpty() ? "No active supervisor matches '" + name + "'. Please check their full name."
                    : "Several supervisors match '" + name + "'. Please give a more specific name or compare them using the standard filters.");
        }
        return resolved.getFirst();
    }

    private Candidate candidate(AssessmentSlotVo slot, List<LocalDateTime> time, Long preferredId) {
        Candidate item = new Candidate();
        item.setSlotVersion(slot.getVersion());
        item.setSlotId(slot.getId().toString());
        item.setSupervisorId(slot.getCreator() == null ? null : slot.getCreator().toString());
        item.setSupervisorName(slot.getCreatorName());
        item.setHospital(slot.getHospital());
        item.setDepartment(slot.getDepartment());
        item.setType(slot.getType());
        item.setLocation(slot.getLocation());
        item.setStartTime(time.getFirst());
        item.setEndTime(time.getLast());
        item.setPreferredSupervisor(preferredId != null && preferredId.equals(slot.getCreator()));
        return item;
    }

    private BookingAssistantResult state(BookingAssistantResult result, Status status, String message) {
        result.setStatus(status);
        result.setMessage(message);
        return result;
    }

    private static class Clarification extends RuntimeException {
        Clarification(String message) { super(message); }
    }
}
