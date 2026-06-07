package com.mangatrack.manga;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class MangaUpdateSchedulerTest {

    @Mock MangaCheckOrchestrator orchestrator;

    @InjectMocks MangaUpdateScheduler scheduler;

    @Test
    void runDailyCheck_delegatesToOrchestrator() {
        scheduler.runDailyCheck();

        verify(orchestrator).runScheduledDailyCheck();
        verifyNoMoreInteractions(orchestrator);
    }

    // --- cron expression pinning ---
    // Pulls the cron literal from the @Scheduled annotation via reflection so a
    // typo in the production annotation surfaces as a test failure here, not
    // silently as a never-firing or wrong-time job in prod.

    @Test
    void runDailyCheck_isAnnotatedWithScheduledAt09_00Daily() throws Exception {
        Scheduled scheduled = scheduledAnnotation();
        assertThat(scheduled).as("@Scheduled must be present on runDailyCheck").isNotNull();
        assertThat(scheduled.cron()).isEqualTo("0 0 9 * * *");
    }

    @Test
    void cronExpression_parsesWithoutError() throws Exception {
        String cron = scheduledAnnotation().cron();
        assertThatCode(() -> CronExpression.parse(cron)).doesNotThrowAnyException();
    }

    @Test
    void cronExpression_beforeNineAm_firesAtSameDayNineAm() throws Exception {
        CronExpression cron = CronExpression.parse(scheduledAnnotation().cron());

        LocalDateTime next = cron.next(LocalDateTime.of(2026, 1, 1, 8, 30));

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 1, 1, 9, 0));
    }

    @Test
    void cronExpression_afterNineAm_firesAtNextDayNineAm() throws Exception {
        CronExpression cron = CronExpression.parse(scheduledAnnotation().cron());

        LocalDateTime next = cron.next(LocalDateTime.of(2026, 1, 1, 9, 30));

        assertThat(next).isEqualTo(LocalDateTime.of(2026, 1, 2, 9, 0));
    }

    // --- disabled-by-default gating ---
    // The daily check is gated off until the MangaDex update API is fixed: with
    // manga.check.scheduled.enabled unset/false the bean is never registered and
    // the job never fires. Pinned here so a change to the flag name or its default
    // surfaces as a test failure rather than silent, unexpected SMS in prod.

    @Test
    void scheduler_isGatedByDisabledByDefaultProperty() {
        ConditionalOnProperty gate = MangaUpdateScheduler.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(gate).as("@ConditionalOnProperty must gate the scheduler bean").isNotNull();
        assertThat(gate.name()).containsExactly("manga.check.scheduled.enabled");
        assertThat(gate.havingValue()).isEqualTo("true");
        assertThat(gate.matchIfMissing()).as("must be off unless explicitly enabled").isFalse();
    }

    private static Scheduled scheduledAnnotation() throws NoSuchMethodException {
        Method method = MangaUpdateScheduler.class.getDeclaredMethod("runDailyCheck");
        return method.getAnnotation(Scheduled.class);
    }
}
