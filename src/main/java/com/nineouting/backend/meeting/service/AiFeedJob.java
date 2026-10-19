package com.nineouting.backend.meeting.service;

import com.nineouting.backend.meeting.client.AiServiceClient;
import com.nineouting.backend.meeting.client.AiServiceException;
import com.nineouting.backend.meeting.config.AiFeedProperties;
import com.nineouting.backend.meeting.config.AppConfig;
import com.nineouting.backend.meeting.domain.AiBatch;
import com.nineouting.backend.meeting.domain.AiFeed;
import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.MeetingStatus;
import com.nineouting.backend.meeting.domain.ProgressEvent;
import com.nineouting.backend.meeting.domain.ProgressEventType;
import com.nineouting.backend.meeting.domain.TranscriptLine;
import com.nineouting.backend.meeting.dto.AiEventMessage;
import com.nineouting.backend.meeting.dto.AiLinesMessage;
import com.nineouting.backend.meeting.repository.AiBatchRepository;
import com.nineouting.backend.meeting.repository.AiFeedRepository;
import com.nineouting.backend.meeting.repository.MeetingRepository;
import com.nineouting.backend.meeting.repository.ProgressEventRepository;
import com.nineouting.backend.meeting.repository.TranscriptLineRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 회의 중 ai-service로 회의 알림은 바로, 줄 묶음은 batchInterval마다 밀어 준다 (docs/services/ai-integration.md 2.4).
 * <ul>
 *   <li>회의마다 잠그고 처리하므로 서비스를 여러 개 띄워도 같은 회의를 동시에 보내지 않는다</li>
 *   <li>ai-service가 실패하면 보낼 것을 그대로 두고 다음 주기에 다시 보낸다. 그동안의 변경은 한 묶음에 모인다 (REQ-111)</li>
 *   <li>ai-service가 빠진 묶음 번호를 알려 주면(409) 저장해 둔 묶음을 그 번호부터 다시 보낸다</li>
 *   <li>종료(end) 알림은 남은 줄을 다 보낸 뒤에 보낸다. 먼저 가면 ai-service가 마지막 구간의 줄을 빠뜨린다</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiFeedJob {

    /** 한 번 돌 때 처리하는 회의 수 상한 (다른 회의가 오래 밀리지 않게) */
    private static final int MAX_FEEDS_PER_RUN = 50;

    private final AiFeedRepository feedRepository;
    private final ProgressEventRepository eventRepository;
    private final TranscriptLineRepository lineRepository;
    private final AiBatchRepository batchRepository;
    private final MeetingRepository meetingRepository;
    private final AiServiceClient aiServiceClient;
    private final AiFeedProperties properties;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${nineouting.meeting.ai.poll-interval:1s}")
    public void run() {
        if (!properties.enabled()) {
            return;
        }
        for (int i = 0; i < MAX_FEEDS_PER_RUN; i++) {
            if (!Boolean.TRUE.equals(transactionTemplate.execute(status -> feedNext()))) {
                return;
            }
        }
    }

    /** 차례인 회의 하나를 보낸다. 차례인 회의가 없으면 false */
    private boolean feedNext() {
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<AiFeed> due = feedRepository.lockNextDue(now);
        if (due.isEmpty()) {
            return false;
        }
        AiFeed feed = due.get();
        try {
            boolean endWaiting = sendEvents(feed);
            if (feed.isBatchDue(now) || endWaiting) {
                boolean sendAgainNow = resendStoredBatches(feed) || sendNewBatch(feed, now);
                feed.succeeded(sendAgainNow ? now : now.plus(properties.batchInterval()));
                if (endWaiting && !sendAgainNow) {
                    sendEvents(feed);
                }
                deleteIfDone(feed, now);
            }
        } catch (AiServiceException e) {
            feed.failed(now.plus(properties.batchInterval()));
            log.warn("ai-service로 보내지 못해 {}초 뒤에 다시 보냅니다 (meetingId={}, 연속 실패 {}회): {}",
                    properties.batchInterval().toSeconds(), feed.getMeetingId(), feed.getFailures(), e.getMessage());
        }
        return true;
    }

    /** 쌓인 회의 알림을 순서대로 보낸다. 종료 알림 앞에서 아직 못 보낸 줄이 있으면 멈추고 true */
    private boolean sendEvents(AiFeed feed) {
        Long meetingId = feed.getMeetingId();
        for (ProgressEvent event : eventRepository.findByMeetingIdAndSentFalseOrderById(meetingId)) {
            if (event.getEvent() == ProgressEventType.END && !lineRepository.findPendingForAi(meetingId).isEmpty()) {
                return true;
            }
            aiServiceClient.sendEvent(meetingId, new AiEventMessage(meetingId.toString(), event.getEvent().wireName(),
                    event.getAtMs(), event.getReceivedAt().atZone(AppConfig.ZONE).toOffsetDateTime()));
            event.markSent();
        }
        return false;
    }

    /** @return ai-service가 더 앞 번호를 다시 달라고 해서 곧바로 다시 보내야 하면 true */
    private boolean resendStoredBatches(AiFeed feed) {
        if (feed.getResendFrom() == null) {
            return false;
        }
        List<AiBatch> stored = batchRepository.findByMeetingIdAndBatchGreaterThanEqualOrderByBatch(
                feed.getMeetingId(), feed.getResendFrom());
        for (AiBatch batch : stored) {
            // 보내다 실패하면(예외) resendFrom이 남아 다음 주기에 처음부터 다시 보낸다
            Integer expected = aiServiceClient.sendLines(feed.getMeetingId(), batch.getPayload());
            if (expected != null) {
                feed.resendDone();
                feed.requestResend(expected);
                return true;
            }
        }
        feed.resendDone();
        return false;
    }

    /** @return ai-service가 빠진 번호를 알려 와서 곧바로 다시 보내야 하면 true */
    private boolean sendNewBatch(AiFeed feed, LocalDateTime now) {
        List<TranscriptLine> pending = lineRepository.findPendingForAi(feed.getMeetingId());
        if (pending.isEmpty()) {
            return false;
        }
        int batch = feed.getNextBatch();
        String payload = jsonMapper.writeValueAsString(new AiLinesMessage(feed.getMeetingId().toString(), batch,
                pending.stream().filter(TranscriptLine::isNewForAi).map(AiFeedJob::toLine).toList(),
                pending.stream().filter(TranscriptLine::isChangedForAi).map(AiFeedJob::toEdit).toList()));
        Integer expected = aiServiceClient.sendLines(feed.getMeetingId(), payload);
        if (expected != null) {
            log.info("ai-service가 줄 묶음 {}번을 기다려 다시 보냅니다 (meetingId={}, 보낸 번호 {})",
                    expected, feed.getMeetingId(), batch);
            feed.requestResend(expected);
            return true;
        }
        batchRepository.save(new AiBatch(feed.getMeetingId(), batch, payload, now));
        pending.forEach(line -> lineRepository.markSentToAi(line.getId(), line.getRev()));
        feed.batchSent();
        return false;
    }

    /** 회의가 끝나고 늦게 오는 줄까지 기다린 뒤 보낼 것이 없으면 정리한다. 회의가 지워졌으면 바로 정리한다 */
    private void deleteIfDone(AiFeed feed, LocalDateTime now) {
        Long meetingId = feed.getMeetingId();
        Optional<Meeting> meeting = meetingRepository.findById(meetingId);
        boolean done = meeting.isEmpty()
                || (meeting.get().is(MeetingStatus.FINISHED)
                    && meeting.get().getFinishedAt().plus(properties.finishedGrace()).isBefore(now)
                    && !eventRepository.existsByMeetingIdAndSentFalse(meetingId)
                    && lineRepository.findPendingForAi(meetingId).isEmpty());
        if (done) {
            batchRepository.deleteByMeetingIdIn(List.of(meetingId));
            feedRepository.delete(feed);
        }
    }

    private static AiLinesMessage.Line toLine(TranscriptLine line) {
        return new AiLinesMessage.Line(line.getSeq(), line.getStartMs(), line.getEndMs(), line.getText(), line.getKind(),
                line.isLowConfidence(), line.isRecovered(), false);
    }

    private static AiLinesMessage.Edit toEdit(TranscriptLine line) {
        return line.isDeleted()
                ? new AiLinesMessage.Edit(line.getSeq(), line.getRev(), null, true)
                : new AiLinesMessage.Edit(line.getSeq(), line.getRev(), line.getText(), null);
    }
}
