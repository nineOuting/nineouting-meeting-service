package com.nineouting.backend.meeting.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.Tracer;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.ttddyy.observation.tracing.ConnectionContext;
import net.ttddyy.observation.tracing.QueryContext;
import net.ttddyy.observation.tracing.QueryObservationConvention;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class JdbcTimingConfiguration {
    @Bean
    ConnectionTimings connectionTimings(MeterRegistry registry, ObjectProvider<Tracer> tracers) {
        return new ConnectionTimings(registry, tracers::getIfAvailable, System::nanoTime);
    }

    @Bean
    QueryObservationConvention queryObservationConvention() {
        return new QueryObservationConvention() {
            @Override
            public String getContextualName(QueryContext context) {
                return queryName(context);
            }

            @Override
            public KeyValues getLowCardinalityKeyValues(QueryContext context) {
                return QueryObservationConvention.super.getLowCardinalityKeyValues(context)
                        .and("jdbc.operation", queryName(context));
            }

            @Override
            public KeyValues getHighCardinalityKeyValues(QueryContext context) {
                // SQL 문자·바인딩 값 대신 고정된 분류로 쿼리를 구분한다.
                return KeyValues.empty();
            }
        };
    }

    static String queryName(QueryContext context) {
        String query = String.join(" ", context.getQueries()).toLowerCase(Locale.ROOT);
        if (query.contains("meeting_attendees")) {
            return "meeting.attendees";
        }
        if (query.contains("meetings")) {
            return query.matches("(?s).*count\\s*\\(.*") ? "meeting.count" : "meeting.list";
        }
        return "other";
    }

    static final class ConnectionTimings
            implements ObservationHandler<ConnectionContext>, ObservationFilter {
        private static final Logger log = LoggerFactory.getLogger(ConnectionTimings.class);
        private static final String TIMING = ConnectionTimings.class.getName() + ".timing";
        private static final String RESULT = ConnectionTimings.class.getName() + ".result";
        private static final long SLOW_NANOS = TimeUnit.MILLISECONDS.toNanos(100);
        private final MeterRegistry registry;
        private final Supplier<Tracer> tracers;
        private final LongSupplier clock;

        ConnectionTimings(MeterRegistry registry, Supplier<Tracer> tracers, LongSupplier clock) {
            this.registry = registry;
            this.tracers = tracers;
            this.clock = clock;
        }

        @Override
        public boolean supportsContext(Observation.Context context) {
            return context instanceof ConnectionContext;
        }

        @Override
        public void onStart(ConnectionContext context) {
            Tracer tracer = tracers.get();
            String traceId = tracer == null || tracer.currentSpan() == null
                    ? "" : tracer.currentSpan().context().traceId();
            context.put(TIMING, new Timing(clock.getAsLong(), null, traceId));
        }

        @Override
        public void onEvent(Observation.Event event, ConnectionContext context) {
            if ("acquired".equals(event.getName())) {
                Timing timing = context.getRequired(TIMING);
                context.put(TIMING, new Timing(timing.started(), clock.getAsLong(), timing.traceId()));
            }
        }

        @Override
        public Observation.Context map(Observation.Context context) {
            if (context instanceof ConnectionContext connection) {
                Result result = finish(connection);
                connection.addHighCardinalityKeyValue(KeyValue.of("jdbc.connection.acquire_ms", ms(result.acquire())));
                connection.addHighCardinalityKeyValue(KeyValue.of("jdbc.connection.hold_ms", ms(result.hold())));
                connection.addHighCardinalityKeyValue(KeyValue.of("jdbc.connection.acquired",
                        Boolean.toString(result.acquired())));
            }
            return context;
        }

        @Override
        public void onStop(ConnectionContext context) {
            Result result = finish(context);
            String outcome = context.getError() == null ? "success" : "error";
            registry.timer("meeting.jdbc.connection.acquire", "outcome", outcome)
                    .record(result.acquire(), TimeUnit.NANOSECONDS);
            if (result.acquired()) {
                registry.timer("meeting.jdbc.connection.hold", "outcome", outcome)
                        .record(result.hold(), TimeUnit.NANOSECONDS);
            }
            Timing timing = context.getRequired(TIMING);
            if (!timing.traceId().isEmpty()
                    && (result.acquire() >= SLOW_NANOS || result.hold() >= SLOW_NANOS)) {
                log.info("event=jdbc_connection_timing traceId={} acquireMs={} holdMs={} acquired={} outcome={}",
                        timing.traceId(), ms(result.acquire()), ms(result.hold()), result.acquired(), outcome);
            }
        }

        private Result finish(ConnectionContext context) {
            Result result = context.get(RESULT);
            if (result == null) {
                Timing timing = context.getRequired(TIMING);
                long end = clock.getAsLong();
                boolean acquired = timing.acquired() != null;
                result = new Result(acquired ? timing.acquired() - timing.started() : end - timing.started(),
                        acquired ? end - timing.acquired() : 0, acquired);
                context.put(RESULT, result);
            }
            return result;
        }

        private static String ms(long nanos) {
            return Double.toString(nanos / 1_000_000.0);
        }

        private record Timing(long started, Long acquired, String traceId) {}
        private record Result(long acquire, long hold, boolean acquired) {}
    }
}
