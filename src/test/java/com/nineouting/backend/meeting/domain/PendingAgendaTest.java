package com.nineouting.backend.meeting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 남은 안건 상태 (REQ-051~052, 094): 대기 → 진행 중 → 처리됨 (DB 없이) */
class PendingAgendaTest {

    private static final LocalDateTime CARRIED = LocalDateTime.of(2026, 10, 20, 15, 0);

    @Test
    void 넘기면_대기_상태로_만들어진다() {
        PendingAgenda pending = pending();

        assertThat(pending.isWaiting()).isTrue();
        assertThat(pending.getFromMeetingId()).isEqualTo(31L);
        assertThat(pending.getCarriedAt()).isEqualTo(CARRIED);
        assertThat(pending.getTakenMeetingId()).isNull();
    }

    @Test
    void 다음_회의에_불러오면_진행_중이고_그_회의만_가진다() {
        PendingAgenda pending = pending();

        pending.takeBy(40L);

        assertThat(pending.getStatus()).isEqualTo(PendingAgendaStatus.IN_PROGRESS);
        assertThat(pending.isWaiting()).isFalse();
        assertThat(pending.isTakenBy(40L)).isTrue();
        assertThat(pending.isTakenBy(41L)).isFalse();
    }

    @Test
    void 불러온_회의가_취소되면_다시_대기로_돌아간다() {
        PendingAgenda pending = pending();
        pending.takeBy(40L);

        pending.release();

        assertThat(pending.isWaiting()).isTrue();
        assertThat(pending.getTakenMeetingId()).isNull();
        assertThat(pending.isTakenBy(40L)).isFalse();
    }

    @Test
    void 불러온_회의에서_다시_넘기면_같은_안건이_새_회의_기준으로_대기가_된다() {
        PendingAgenda pending = pending();
        pending.takeBy(40L);

        pending.carryAgain(40L, CARRIED.plusDays(7));

        assertThat(pending.isWaiting()).isTrue();
        assertThat(pending.getFromMeetingId()).isEqualTo(40L);
        assertThat(pending.getCarriedAt()).isEqualTo(CARRIED.plusDays(7));
        assertThat(pending.getTitle()).isEqualTo("배포 일정");
    }

    @Test
    void 처리되면_더_이상_대기도_진행_중도_아니다() {
        PendingAgenda pending = pending();
        pending.takeBy(40L);

        pending.done();

        assertThat(pending.getStatus()).isEqualTo(PendingAgendaStatus.DONE);
        assertThat(pending.isWaiting()).isFalse();
        assertThat(pending.isTakenBy(40L)).isFalse();
    }

    private static PendingAgenda pending() {
        return new PendingAgenda(3L, 4L, "배포 일정", "배포 날짜 확정", 31L, CARRIED);
    }
}
