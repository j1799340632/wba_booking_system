package edu.unsw.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import edu.unsw.dao.mapper.AssessmentBookingMapper;
import edu.unsw.dao.mapper.AssessmentSlotMapper;
import edu.unsw.dao.mapper.UserMapper;
import edu.unsw.domain.dto.RegistrarBookingQueryDto;
import edu.unsw.domain.entity.AssessmentBooking;
import edu.unsw.domain.entity.AssessmentSlot;
import edu.unsw.domain.entity.User;
import edu.unsw.domain.vo.RegistrarBookingVo;
import edu.unsw.service.RegistrarBookingService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Yichen Guo
 * @date 17/7/2026
 * @description Registrar booking related services implementation
 */
@Service
public class RegistrarBookingServiceImpl extends ServiceImpl<AssessmentBookingMapper, AssessmentBooking> implements RegistrarBookingService {
    private final AssessmentSlotMapper assessmentSlotMapper;
    private final UserMapper userMapper;

    public RegistrarBookingServiceImpl(AssessmentSlotMapper assessmentSlotMapper, UserMapper userMapper) {
        this.assessmentSlotMapper = assessmentSlotMapper;
        this.userMapper = userMapper;
    }

    @Override
    public Page<RegistrarBookingVo> getAllBookings(RegistrarBookingQueryDto registrarBookingQueryDto) {
        Set<Long> filteredSlotIds = queryFilteredSlotIds(registrarBookingQueryDto);
        if (filteredSlotIds.isEmpty()) {
            return emptyPage(registrarBookingQueryDto);
        }

        Set<Long> keywordUserIds = new HashSet<>();
        Set<Long> keywordSlotIds = new HashSet<>();
        String keyword = registrarBookingQueryDto.getKeyword();

        if (StringUtils.hasText(keyword)) {
            List<User> users = userMapper.selectList(
                    Wrappers.<User>lambdaQuery()
                            .like(User::getName, keyword)
                            .or()
                            .like(User::getZId, keyword)
            );

            keywordUserIds = users.stream()
                    .map(User::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            if (!keywordUserIds.isEmpty()) {
                keywordSlotIds = assessmentSlotMapper.selectList(
                                Wrappers.<AssessmentSlot>lambdaQuery()
                                        .in(AssessmentSlot::getCreator, keywordUserIds)
                        )
                        .stream()
                        .map(AssessmentSlot::getId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
            }

            if (keywordUserIds.isEmpty() && keywordSlotIds.isEmpty()) {
                return emptyPage(registrarBookingQueryDto);
            }
        }

        LambdaQueryWrapper<AssessmentBooking> bookingQueryWrapper = Wrappers.lambdaQuery();

        bookingQueryWrapper
                .eq(Objects.nonNull(registrarBookingQueryDto.getStatus()),
                        AssessmentBooking::getStatus,
                        registrarBookingQueryDto.getStatus())
                .ge(Objects.nonNull(registrarBookingQueryDto.getStartTime()),
                        AssessmentBooking::getStartTime,
                        registrarBookingQueryDto.getStartTime())
                .le(Objects.nonNull(registrarBookingQueryDto.getEndTime()),
                        AssessmentBooking::getEndTime,
                        registrarBookingQueryDto.getEndTime())
                .in(AssessmentBooking::getAssessmentSlotId, filteredSlotIds)
                .orderByDesc(AssessmentBooking::getStartTime);

        if (StringUtils.hasText(keyword)) {
            Set<Long> matchedUserIds = keywordUserIds;
            Set<Long> matchedSlotIds = keywordSlotIds;

            bookingQueryWrapper.and(wrapper -> {
                boolean hasPreviousCondition = false;

                if (!matchedUserIds.isEmpty()) {
                    wrapper.in(AssessmentBooking::getCreator, matchedUserIds);
                    hasPreviousCondition = true;
                }

                if (!matchedSlotIds.isEmpty()) {
                    if (hasPreviousCondition) {
                        wrapper.or();
                    }
                    wrapper.in(AssessmentBooking::getAssessmentSlotId, matchedSlotIds);
                }
            });
        }

        Page<AssessmentBooking> bookingPage = this.page(
                new Page<>(
                        registrarBookingQueryDto.getPageNum(),
                        registrarBookingQueryDto.getPageQuantity()
                ),
                bookingQueryWrapper
        );

        return buildRegistrarBookingVoPage(bookingPage);
    }

    private Set<Long> queryFilteredSlotIds(RegistrarBookingQueryDto registrarBookingQueryDto) {
      return assessmentSlotMapper.selectList(
                        Wrappers.<AssessmentSlot>lambdaQuery()
                                .eq(StringUtils.hasText(registrarBookingQueryDto.getHospital()),
                                        AssessmentSlot::getHospital,
                                        registrarBookingQueryDto.getHospital())
                                .eq(StringUtils.hasText(registrarBookingQueryDto.getDepartment()),
                                        AssessmentSlot::getDepartment,
                                        registrarBookingQueryDto.getDepartment())
                )
                .stream()
                .map(AssessmentSlot::getId)
                .collect(Collectors.toSet());
    }

    private Page<RegistrarBookingVo> buildRegistrarBookingVoPage(Page<AssessmentBooking> bookingPage) {
        Page<RegistrarBookingVo> registrarBookingVoPage = new Page<>();
        BeanUtils.copyProperties(bookingPage, registrarBookingVoPage);

        List<AssessmentBooking> bookings = bookingPage.getRecords();
        if (bookings.isEmpty()) {
            registrarBookingVoPage.setRecords(Collections.emptyList());
            return registrarBookingVoPage;
        }

        Set<Long> slotIds = bookings.stream()
                .map(AssessmentBooking::getAssessmentSlotId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, AssessmentSlot> slotMap = assessmentSlotMapper.selectList(
                        Wrappers.<AssessmentSlot>lambdaQuery()
                                .in(AssessmentSlot::getId, slotIds)
                )
                .stream()
                .collect(Collectors.toMap(
                        AssessmentSlot::getId,
                        slot -> slot,
                        (existing, ignored) -> existing
                ));

        Set<Long> userIds = new HashSet<>();
        bookings.stream()
                .map(AssessmentBooking::getCreator)
                .filter(Objects::nonNull)
                .forEach(userIds::add);

        slotMap.values().stream()
                .map(AssessmentSlot::getCreator)
                .filter(Objects::nonNull)
                .forEach(userIds::add);

        Map<Long, User> userMap = userMapper.selectList(
                        Wrappers.<User>lambdaQuery()
                                .in(User::getId, userIds)
                )
                .stream()
                .collect(Collectors.toMap(
                        User::getId,
                        user -> user,
                        (existing, ignored) -> existing
                ));

        List<RegistrarBookingVo> records = bookings.stream()
                .map(booking -> buildRegistrarBookingVo(booking, slotMap, userMap))
                .toList();

        registrarBookingVoPage.setRecords(records);
        return registrarBookingVoPage;
    }

    private RegistrarBookingVo buildRegistrarBookingVo(AssessmentBooking booking,
                                                       Map<Long, AssessmentSlot> slotMap,
                                                       Map<Long, User> userMap) {
        AssessmentSlot slot = slotMap.get(booking.getAssessmentSlotId());
        if (Objects.isNull(slot)) {
            slot = new AssessmentSlot();
        }

        User student = userMap.get(booking.getCreator());
        User supervisor = userMap.get(slot.getCreator());

        RegistrarBookingVo registrarBookingVo = new RegistrarBookingVo();
        registrarBookingVo.setBookingId(booking.getId());
        registrarBookingVo.setSlotId(booking.getAssessmentSlotId());

        if (Objects.nonNull(student)) {
            registrarBookingVo.setStudentId(student.getId());
            registrarBookingVo.setStudentName(student.getName());
            registrarBookingVo.setStudentZid(student.getZId());
        }

        if (Objects.nonNull(supervisor)) {
            registrarBookingVo.setSupervisorId(supervisor.getId());
            registrarBookingVo.setSupervisorName(supervisor.getName());
        }

        registrarBookingVo.setHospital(slot.getHospital());
        registrarBookingVo.setDepartment(slot.getDepartment());
        registrarBookingVo.setLocation(slot.getLocation());

        registrarBookingVo.setWbaType(slot.getType());
        registrarBookingVo.setStartTime(booking.getStartTime());
        registrarBookingVo.setEndTime(booking.getEndTime());
        registrarBookingVo.setStatus(booking.getStatus());
        registrarBookingVo.setScore(booking.getScore());
        registrarBookingVo.setCompletedTime(booking.getCompletedTime());

        return registrarBookingVo;
    }

    private Page<RegistrarBookingVo> emptyPage(RegistrarBookingQueryDto registrarBookingQueryDto) {
        Page<RegistrarBookingVo> emptyPage = new Page<>(
                registrarBookingQueryDto.getPageNum(),
                registrarBookingQueryDto.getPageQuantity()
        );
        emptyPage.setTotal(0L);
        emptyPage.setRecords(Collections.emptyList());
        return emptyPage;
    }
}
