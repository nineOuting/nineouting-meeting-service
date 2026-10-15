package com.nineouting.backend.meeting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/** 회의 상태 전이와 회의록 상태 표시 (DB 없이) */
class MeetingTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 14, 0);
    private static final long OWNER = 1L;
    private static final long WRITER = 2L;

    @Test
    void 만들면_예정이고_시작하면_진행_중_마무리하면_끝남이다() {
        Meeting meeting = meeting();
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.SCHEDULED);

        meeting.start(NOW);
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.IN_PROGRESS);
        assertThat(meeting.getStartedAt()).isEqualTo(NOW);

        meeting.finish(NOW.plusHours(1));
        assertThat(meeting.getStatus()).isEqualTo(MeetingStatus.FINISHED);
        assertThat(meeting.getFinishedAt()).isEqualTo(NOW.plusHours(1));
    }

    @Test
    void 취소하면_취소_상태와_시각을_남긴다() {
        Meeting meeting = meeting();

        meeting.cancel(NOW);

        assertThat(meeting.is(MeetingStatus.CANCELLED)).isTrue();
        assertThat(meeting.getCancelledAt()).isEqualTo(NOW);
    }

    @Test
    void 만든_사람과_회의록_작성자를_구분한다() {
        Meeting meeting = meeting();

        assertThat(meeting.isOwnedBy(OWNER)).isTrue();
        assertThat(meeting.isOwnedBy(WRITER)).isFalse();
        assertThat(meeting.isWriter(WRITER)).isTrue();
        assertThat(meeting.isWriter(OWNER)).isFalse();
    }

    @Test
    void 회의록_상태는_검토_중에서_확정으로_바뀐다() {
        Meeting meeting = meeting();
        assertThat(meeting.getMinutesStatus()).isNull();

        meeting.changeMinutesStatus(MinutesProgress.REVIEWING);
        assertThat(meeting.getMinutesStatus()).isEqualTo(MinutesProgress.REVIEWING);

        meeting.changeMinutesStatus(MinutesProgress.CONFIRMED);
        assertThat(meeting.getMinutesStatus()).isEqualTo(MinutesProgress.CONFIRMED);
    }

    @Test
    void 이벤트_순서가_바뀌어_와도_확정을_검토_중으로_되돌리지_않는다() {
        Meeting meeting = meeting();
        meeting.changeMinutesStatus(MinutesProgress.CONFIRMED);

        meeting.changeMinutesStatus(MinutesProgress.REVIEWING);

        assertThat(meeting.getMinutesStatus()).isEqualTo(MinutesProgress.CONFIRMED);
    }

    @Test
    void 폴더를_옮기면_폴더만_바뀐다() {
        Meeting meeting = meeting();

        meeting.moveTo(99L);

        assertThat(meeting.getFolderId()).isEqualTo(99L);
        assertThat(meeting.getWorkspaceId()).isEqualTo(3L);
    }

    private static Meeting meeting() {
        Meeting meeting = new Meeting(3L, 4L, OWNER, null);
        meeting.changeInfo("주간 회의", NOW, NOW.plusHours(1), "3층", WRITER);
        return meeting;
    }
}
