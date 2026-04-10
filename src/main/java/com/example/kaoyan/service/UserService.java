package com.example.kaoyan.service;

import com.example.kaoyan.dto.UserInfoDTO;
import com.example.kaoyan.dto.UserProfileDTO;
import com.example.kaoyan.entity.CheckIn;
import com.example.kaoyan.entity.User;
import com.example.kaoyan.entity.UserProfile;
import com.example.kaoyan.repository.CheckInRepository;
import com.example.kaoyan.repository.UserProfileRepository;
import com.example.kaoyan.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 用户服务
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final CheckInRepository checkInRepository;

    /**
     * 获取用户完整信息
     */
    public UserInfoDTO getUserInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElse(new UserProfile());

        UserInfoDTO dto = new UserInfoDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setAvatar(user.getAvatar());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setTargetSchool(profile.getTargetSchool());
        dto.setTargetMajor(profile.getTargetMajor());
        dto.setExamDate(profile.getExamDate());
        dto.setStudyStartDate(profile.getStudyStartDate());
        dto.setCheckInDays(checkInRepository.countByUserId(userId));
        dto.setTotalStudyMinutes(checkInRepository.sumStudyMinutesByUserId(userId));

        return dto;
    }

    /**
     * 更新用户档案
     */
    @Transactional
    public void updateProfile(Long userId, UserProfileDTO dto) {
        UserProfile profile = userProfileRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserProfile p = new UserProfile();
                    p.setUserId(userId);
                    return p;
                });

        profile.setTargetSchool(dto.getTargetSchool());
        profile.setTargetMajor(dto.getTargetMajor());
        profile.setExamDate(dto.getExamDate());
        profile.setStudyStartDate(dto.getStudyStartDate());
        userProfileRepository.save(profile);
    }

    /**
     * 每日打卡
     */
    @Transactional
    public CheckIn checkIn(Long userId, Integer studyMinutes) {
        LocalDate today = LocalDate.now();
        CheckIn checkIn = checkInRepository.findByUserIdAndCheckDate(userId, today)
                .orElseGet(() -> {
                    CheckIn c = new CheckIn();
                    c.setUserId(userId);
                    c.setCheckDate(today);
                    return c;
                });

        checkIn.setStudyMinutes(studyMinutes != null ? studyMinutes : 0);
        return checkInRepository.save(checkIn);
    }

    /**
     * 获取打卡记录
     */
    public List<CheckIn> getCheckInHistory(Long userId, LocalDate start, LocalDate end) {
        if (start != null && end != null) {
            return checkInRepository.findByUserIdAndCheckDateBetween(userId, start, end);
        }
        return checkInRepository.findByUserIdOrderByCheckDateDesc(userId);
    }
}
