package io.opentracing.akka;

import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Random;
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
import static akka.dispatch.Futures.sequence;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static io.opentracing.akka.RefCountSpan.startActive;

public class TracedRefCountExecutionContextTest {
    static final MockTracer mockTracer = new MockTracer(new ThreadLocalScopeManager(),
        MockTracer.Propagator.TEXT_MAP);

    @Before
    public void before() throws Exception {
        mockTracer.reset();
    }

    @Test(expected=IllegalArgumentException.class)
    public void testIllegalContext() throws Exception {
        new TracedRefCountExecutionContext(null, mockTracer);
    }

    @Test(expected=IllegalArgumentException.class)
    public void testIllegalTracer() throws Exception {
        new TracedRefCountExecutionContext(ExecutionContext.global(), null);
    }

    @Test
    public void testSimple() throws Exception {
        ExecutionContext ec = new TracedRefCountExecutionContext(ExecutionContext.global(), mockTracer);
        Future f = null;
        Span span = null;

        try (Scope scope = startActive(mockTracer.buildSpan("one"), mockTracer)) {
            span = scope.span();

            f = future(new Callable<Span>() {
                @Override
                public Span call() {
                    assertNotNull(mockTracer.scopeManager().active());

                    Span activeSpan = mockTracer.scopeManager().active().span();
                    activeSpan.setTag("done", Boolean.TRUE);
                    return activeSpan;
                }
            }, ec);
        }

        Object result = Await.result(f, TestUtils.getDefaultDuration());
        assertEquals(span, result);
        assertEquals(1, mockTracer.finishedSpans().size());

        MockSpan mockSpan = mockTracer.finishedSpans().get(0);
        assertEquals("one", mockSpan.operationName());
        assertEquals(Boolean.TRUE, mockSpan.tags().get("done"));
    }

    @Test
    public void testMultiple() throws Exception {
        ExecutionContext ec = new TracedRefCountExecutionContext(ExecutionContext.global(), mockTracer);
        List<Future<Span>> futures = new LinkedList<Future<Span>>();
        Random rand = new Random();

        try (Scope scope = startActive(mockTracer.buildSpan("one"), mockTracer)) {

            for (int i = 0; i < 5; i++) {
                futures.add(future(new Callable<Span>() {
                    @Override
                    public Span call() {
                        int sleepMs = rand.nextInt(500);
                        try { Thread.sleep(sleepMs); } catch (InterruptedException e) {}

                        Span activeSpan = mockTracer.scopeManager().active().span();
                        assertNotNull(activeSpan);
                        activeSpan.setTag(Integer.toString(sleepMs), Boolean.TRUE);
                        return activeSpan;
                    }
                }, ec));
            }
        }

        Future f = sequence(futures, ExecutionContext.global());
        Await.result(f, TestUtils.getDefaultDuration());
        assertEquals(1, mockTracer.finishedSpans().size());
        assertEquals(5, mockTracer.finishedSpans().get(0).tags().size());
    }

    @Test
    public void testPipeline() throws Exception {
        ExecutionContext ec = new TracedRefCountExecutionContext(ExecutionContext.global(), mockTracer);
        Future f = null;

        try (Scope scope = startActive(mockTracer.buildSpan("one"), mockTracer)) {
            f = future(new Callable<Future>() {
                @Override
                public Future call() {
                    assertNotNull(mockTracer.scopeManager().active());
                    mockTracer.scopeManager().active().span().setTag("1", Boolean.TRUE);

                    return future(new Callable<Boolean>() {
                        @Override
                        public Boolean call() {
                            assertNotNull(mockTracer.scopeManager().active());
                            mockTracer.scopeManager().active().span().setTag("2", Boolean.TRUE);
                            return true;
                        }
                    }, ec);
                }
            }, ec);
        }

        Future f2 = (Future)Await.result(f, TestUtils.getDefaultDuration());
        Await.result(f2, TestUtils.getDefaultDuration());
        assertEquals(1, mockTracer.finishedSpans().size());

        MockSpan mockSpan = mockTracer.finishedSpans().get(0);
        assertEquals("one", mockSpan.operationName());
        assertEquals(2, mockSpan.tags().size());
        assertEquals(Boolean.TRUE, mockSpan.tags().get("1"));
        assertEquals(Boolean.TRUE, mockSpan.tags().get("2"));
    }
}
