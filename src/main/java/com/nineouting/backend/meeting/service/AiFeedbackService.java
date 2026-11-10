package com.nineouting.backend.meeting.service;

import com.nineouting.backend.meeting.domain.AiFeedback;
import com.nineouting.backend.meeting.repository.AiFeedbackRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** REQ-172 AI 결과 피드백. 회의를 볼 수 있는 사람이 남긴다. 같은 자리에 다시 누르면 바꾼다 */
@Service
@RequiredArgsConstructor
public class AiFeedbackService {

    private final AiFeedbackRepository repository;
    private final MeetingAccess access;
    private final Clock clock;

    @Transactional
    public void save(Long meetingId, Long userId, AiFeedback.Target target, AiFeedback.Rating rating) {
        access.requireViewable(meetingId, userId);
        LocalDateTime now = LocalDateTime.now(clock);
        repository.findByMeetingIdAndUserIdAndTarget(meetingId, userId, target).ifPresentOrElse(
                feedback -> feedback.change(rating, now),
                () -> repository.save(new AiFeedback(meetingId, userId, target, rating, now)));
    }
}
