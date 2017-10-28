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
        new TracedExecutionContextTest(null, mockTracer);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testIllegalTracer() throws Exception {
        new TracedExecutionContextTest(ExecutionContext.global(), null);
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
