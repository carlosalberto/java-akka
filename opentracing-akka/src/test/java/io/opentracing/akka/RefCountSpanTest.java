package io.opentracing.akka;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.ThreadLocalScopeManager;
import org.junit.Before;
import org.junit.Test;

public class RefCountSpanTest {
    static final MockTracer mockTracer = new MockTracer(new ThreadLocalScopeManager(),
        MockTracer.Propagator.TEXT_MAP);

    @Before
    public void before() throws Exception {
        mockTracer.reset();
    }

    @Test(expected=IllegalStateException.class)
    public void testFinishMicros() {
        Span span = new RefCountSpan(mockTracer.buildSpan("one").startManual());
        span.finish(100);
    }
}
