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

        Span span = new MultiCloseSpan(mockTracer.buildSpan("one").startManual());
        try (Scope scope = mockTracer.scopeManager().activate(span, true)) {
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
        assertEquals(1, mockTracer.finishedSpans().size());
        assertEquals("one", mockTracer.finishedSpans().get(0).operationName());
    }

    @Test
    public void testMulti() throws Exception {
        ExecutionContext ec = new TracedRefCountExecutionContext(ExecutionContext.global(), mockTracer);
        List<Future<Span>> futures = new LinkedList<Future<Span>>();
        Random rand = new Random();

        Span span = new MultiCloseSpan(mockTracer.buildSpan("one").startManual());
        try (Scope scope = mockTracer.scopeManager().activate(span, true)) {

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
}
