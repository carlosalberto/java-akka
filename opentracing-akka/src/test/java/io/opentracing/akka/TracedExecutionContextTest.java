package io.opentracing.akka;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.tag.Tags;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.ThreadLocalScopeManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import scala.concurrent.Await;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import static akka.dispatch.Futures.future;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class TracedExecutionContextTest {
    static final MockTracer mockTracer = new MockTracer(new ThreadLocalScopeManager(),
        MockTracer.Propagator.TEXT_MAP);

    @Before
    public void before() throws Exception {
        mockTracer.reset();
    }

    @Test(expected=IllegalArgumentException.class)
    public void testIllegalContext() throws Exception {
        new TracedExecutionContext(null, mockTracer, false);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testIllegalTracer() throws Exception {
        new TracedExecutionContext(ExecutionContext.global(), null, false);
    }

    @Test
    public void testPropagation() throws Exception {
        ExecutionContext ec = new TracedExecutionContext(ExecutionContext.global(), mockTracer);
        Future f = null;
        Span span = null;

        try (Scope scope = mockTracer.buildSpan("one").startActive(false)) {
            span = scope.span();

            f = future(new Callable<Span>() {
                @Override
                public Span call() {
                    assertNotNull(mockTracer.scopeManager().active());
                    return mockTracer.scopeManager().active().span();
                }
            }, ec);
        }

        Object result = Await.result(f, TestUtils.getDefaultDuration());
        assertEquals(span, result);
        assertEquals(0, mockTracer.finishedSpans().size());

        span.finish();
        assertEquals(1, mockTracer.finishedSpans().size());
        assertEquals(span, mockTracer.finishedSpans().get(0));
    }

    @Test
    public void testCreateSpans() throws Exception {
        ExecutionContext ec = new TracedExecutionContext(ExecutionContext.global(), mockTracer, true);
        Future f = null;
        Span parentSpan = null;

        try (Scope scope = mockTracer.buildSpan("parent").startActive()) {
            parentSpan = scope.span();

            f = future(new Callable<Span>() {
                @Override
                public Span call() {
                    assertNotNull(mockTracer.scopeManager().active());
                    return mockTracer.scopeManager().active().span();
                }
            }, ec);
        }

        Span span = (Span)Await.result(f, TestUtils.getDefaultDuration());
        List<MockSpan> finishedSpans = mockTracer.finishedSpans();
        assertEquals(2, finishedSpans.size());
        assertEquals(parentSpan, finishedSpans.get(0));
        assertEquals(span, finishedSpans.get(1));
        assertEquals(finishedSpans.get(0).context().spanId(), finishedSpans.get(1).parentId());
    }

    @Test
    public void testNoActiveSpan() throws Exception {
        ExecutionContext ec = new TracedExecutionContext(ExecutionContext.global(), mockTracer);

        Future f = future(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                assertNull(mockTracer.scopeManager().active());
                return mockTracer.scopeManager().active() != null;
            }
        }, ec);

        Boolean isActive = (Boolean)Await.result(f, TestUtils.getDefaultDuration());
        assertFalse(isActive);
        assertEquals(0, mockTracer.finishedSpans().size());
    }

    @Test
    public void testGlobalTracer() throws Exception {
        ExecutionContext ec = new TracedExecutionContext(ExecutionContext.global());

        Future f = future(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                assertNull(mockTracer.scopeManager().active());
                return mockTracer.scopeManager().active() != null;
            }
        }, ec);

        Boolean isActive = (Boolean)Await.result(f, TestUtils.getDefaultDuration());
        assertFalse(isActive);
        assertEquals(0, mockTracer.finishedSpans().size());
    }
}
