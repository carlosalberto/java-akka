package io.opentracing.akka;

import java.util.List;
import java.util.Map;

import akka.actor.ActorSystem;
import akka.actor.ActorRef;
import io.opentracing.ActiveSpan;
import io.opentracing.tag.Tags;
import io.opentracing.mock.MockSpan;
import io.opentracing.mock.MockTracer;
import io.opentracing.util.ThreadLocalActiveSpanSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import akka.util.Timeout;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static akka.pattern.Patterns.ask;
import static io.opentracing.akka.TracedFuture.tracedFuture;

public class TracedFutureTest {
    static final MockTracer mockTracer = new MockTracer(new ThreadLocalActiveSpanSource(),
        MockTracer.Propagator.TEXT_MAP);

    @Before
    public void before() throws Exception {
        mockTracer.reset();
    }

    Timeout getDefaultTimeout() {
        return new Timeout(Duration.create(5, "seconds"));
    }

    @Test
    public void testTraced() {
        ActorSystem actorSystem = ActorSystem.create("actorSystem");
        ActorRef actorRef = actorSystem.actorOf(MockActor.props(), "actor1");

        Timeout timeout = getDefaultTimeout();
        Future future = ask(actorRef, new MockActor.MockMessage(), timeout);
        Future tracedFuture = tracedFuture(future, actorRef, actorSystem, mockTracer);
        Exception exc = null;

        try {
            Await.result(tracedFuture, timeout.duration());
        } catch (Exception e) {
            exc = e;
        }

        assertNull(exc);
        assertEquals(1, mockTracer.finishedSpans().size());

        MockSpan span = mockTracer.finishedSpans().get(0);
        assertEquals("ask", span.operationName());

        assertNull(span.tags().get(Tags.ERROR.getKey()));
        assertEquals(Constants.COMPONENT_NAME, span.tags().get(Tags.COMPONENT.getKey()));
        assertEquals("user/actor1", span.tags().get(Constants.ACTOR_PATH));
    }

    @Test
    public void testParentSpan() {
        ActorSystem actorSystem = ActorSystem.create("actorSystem");
        ActorRef actorRef = actorSystem.actorOf(MockActor.props(), "actor1");

        Timeout timeout = getDefaultTimeout();
        Future future = null;
        Future tracedFuture = null;
        Exception exc = null;

        try (ActiveSpan span = mockTracer.buildSpan("parent").startActive()) {
            future = ask(actorRef, new MockActor.MockMessage(), timeout);
            tracedFuture = tracedFuture(future, actorRef, actorSystem, mockTracer);
        }

        try {
            Await.result(tracedFuture, timeout.duration());
        } catch (Exception e) {
            exc = e;
        }

        assertNull(exc);
        assertEquals(2, mockTracer.finishedSpans().size());

        MockSpan parentSpan = mockTracer.finishedSpans().get(0);
        MockSpan futureSpan = mockTracer.finishedSpans().get(1);
        assertEquals("parent", parentSpan.operationName());
        assertEquals("ask", futureSpan.operationName());

        assertEquals(parentSpan.context().traceId(), futureSpan.context().traceId());
        assertEquals(parentSpan.context().spanId(), futureSpan.parentId());
    }

    @Test
    public void testError() {
        ActorSystem actorSystem = ActorSystem.create("actorSystem");
        ActorRef actorRef = actorSystem.actorOf(MockActor.props(), "actor1");

        Timeout timeout = getDefaultTimeout();
        Future future = ask(actorRef, new MockActor.ErrorMessage(), timeout);
        Future tracedFuture = tracedFuture(future, actorRef, actorSystem, mockTracer);
        Exception exc = null;

        try {
            Await.result(tracedFuture, timeout.duration());
        } catch (Exception e) {
            exc = e;
        }

        assertNotNull(exc);
        assertEquals(1, mockTracer.finishedSpans().size());

        MockSpan span = mockTracer.finishedSpans().get(0);
        assertEquals("ask", span.operationName());

        assertEquals(Boolean.TRUE, span.tags().get(Tags.ERROR.getKey()));
        assertEquals(Constants.COMPONENT_NAME, span.tags().get(Tags.COMPONENT.getKey()));
        assertEquals("user/actor1", span.tags().get(Constants.ACTOR_PATH));
    }
}
