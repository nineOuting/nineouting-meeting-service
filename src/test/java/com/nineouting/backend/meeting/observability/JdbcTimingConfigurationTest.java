package com.nineouting.backend.meeting.observability;

import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import net.ttddyy.observation.tracing.ConnectionContext;
import net.ttddyy.observation.tracing.DataSourceObservationListener;
import net.ttddyy.observation.tracing.QueryContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JdbcTimingConfigurationTest {
    @Test
    void separatesAcquireFromHoldAndUsesBoundedMetricTags() {
        var meters = new SimpleMeterRegistry();
        var clock = new AtomicLong(1);
        var tracer = mock(Tracer.class);
        var span = mock(Span.class);
        var trace = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(trace);
        when(trace.traceId()).thenReturn("test-trace");
        var handler = new JdbcTimingConfiguration.ConnectionTimings(meters, () -> tracer, clock::get);
        var registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(handler).observationFilter(handler);
        var context = new ConnectionContext();
        var observation = Observation.start("jdbc.connection", () -> context, registry);

        clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(150));
        observation.event(Observation.Event.of("acquired"));
        clock.addAndGet(TimeUnit.MILLISECONDS.toNanos(20));
        observation.stop();

        assertEquals(150, meters.get("meeting.jdbc.connection.acquire").timer()
                .totalTime(TimeUnit.MILLISECONDS), 0.001);
        assertEquals(20, meters.get("meeting.jdbc.connection.hold").timer()
                .totalTime(TimeUnit.MILLISECONDS), 0.001);
        assertEquals(KeyValue.of("jdbc.connection.acquire_ms", "150.0"),
                context.getHighCardinalityKeyValues().stream().filter(value -> value.getKey().equals("jdbc.connection.acquire_ms")).findFirst().orElseThrow());
        assertEquals(KeyValue.of("jdbc.connection.hold_ms", "20.0"),
                context.getHighCardinalityKeyValues().stream().filter(value -> value.getKey().equals("jdbc.connection.hold_ms")).findFirst().orElseThrow());
        assertTrue(meters.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .allMatch(tag -> tag.getKey().equals("outcome")));
    }

    @Test
    void failedAcquisitionRecordsWaitingWithoutCreatingHoldMeasurement() {
        var meters = new SimpleMeterRegistry();
        var clock = new AtomicLong(1);
        var handler = new JdbcTimingConfiguration.ConnectionTimings(meters, () -> null, clock::get);
        var registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(handler).observationFilter(handler);
        var context = new ConnectionContext();
        var observation = Observation.start("jdbc.connection", () -> context, registry);
        clock.addAndGet(TimeUnit.SECONDS.toNanos(5));

        observation.error(new IllegalStateException("Cannot acquire")).stop();

        assertEquals(5, meters.get("meeting.jdbc.connection.acquire").tag("outcome", "error").timer()
                .totalTime(TimeUnit.SECONDS), 0.001);
        assertNull(meters.find("meeting.jdbc.connection.hold").timer());
        assertEquals(KeyValue.of("jdbc.connection.acquired", "false"),
                context.getHighCardinalityKeyValues().stream().filter(value -> value.getKey().equals("jdbc.connection.acquired")).findFirst().orElseThrow());
        assertSame(context, handler.map(context));
        assertSame(context, handler.map(context));
        assertFalse(handler.supportsContext(new QueryContext()));
        var other = new Observation.Context();
        assertSame(other, handler.map(other));
    }

    @Test
    void queryNamesAreBoundedAndSqlAndParametersAreNotExported() {
        var convention = new JdbcTimingConfiguration().queryObservationConvention();
        String[] sql = {"select * from meetings where title='private'",
                "select count (*) from meetings", "select * from meeting_attendees", "select 1"};
        String[] names = {"meeting.list", "meeting.count", "meeting.attendees", "other"};
        for (int i = 0; i < sql.length; i++) {
            var context = new QueryContext();
            context.setDataSourceName("meeting");
            context.getQueries().add(sql[i]);
            context.getParams().add("private-value");
            assertEquals(names[i], convention.getContextualName(context));
            assertEquals(KeyValue.of("jdbc.operation", names[i]),
                    convention.getLowCardinalityKeyValues(context).stream().filter(value -> value.getKey().equals("jdbc.operation")).findFirst().orElseThrow());
            assertTrue(convention.getHighCardinalityKeyValues(context).stream().findAny().isEmpty());
        }
    }

    @Test
    void jdbcProxyEmitsQueryUnderItsConnectionAndPreservesJdbcCalls() throws Exception {
        var meters = new SimpleMeterRegistry();
        var handler = new JdbcTimingConfiguration.ConnectionTimings(meters, () -> null, System::nanoTime);
        var registry = ObservationRegistry.create();
        var contexts = new ArrayList<Observation.Context>();
        registry.observationConfig().observationHandler(handler).observationFilter(handler)
                .observationHandler(new ObservationHandler<Observation.Context>() {
                    @Override
                    public boolean supportsContext(Observation.Context context) {
                        return context instanceof QueryContext;
                    }
                    @Override
                    public void onStop(Observation.Context context) {
                        contexts.add(context);
                    }
                });
        var listener = new DataSourceObservationListener(registry);
        listener.setQueryObservationConvention(new JdbcTimingConfiguration().queryObservationConvention());
        DataSource target = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(target.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getURL()).thenReturn("jdbc:postgresql://localhost/meeting");
        when(connection.createStatement()).thenReturn(statement);
        var dataSource = ProxyDataSourceBuilder.create(target).name("meeting")
                .listener(listener).methodListener(listener).build();

        try (var observedConnection = dataSource.getConnection();
                var observedStatement = observedConnection.createStatement()) {
            observedStatement.execute("select 1");
        }

        verify(statement).execute("select 1");
        verify(connection).close();
        assertEquals(1, contexts.size());
        assertInstanceOf(ConnectionContext.class,
                contexts.getFirst().getParentObservation().getContextView());
        assertEquals(1, meters.get("meeting.jdbc.connection.acquire").timer().count());
        assertEquals(1, meters.get("meeting.jdbc.connection.hold").timer().count());
    }
}
