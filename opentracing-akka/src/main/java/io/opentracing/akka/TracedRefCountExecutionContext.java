package io.opentracing.akka;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.OnComplete;
import scala.concurrent.ExecutionContext;
import scala.concurrent.ExecutionContextExecutor;

import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public final class TracedRefCountExecutionContext implements ExecutionContextExecutor {
    final ExecutionContext ec;
    final Tracer tracer;

    public TracedRefCountExecutionContext(ExecutionContext ec) {
        this(ec, GlobalTracer.get());
    }

    public TracedRefCountExecutionContext(ExecutionContext ec, Tracer tracer) {
        if (ec == null)
            throw new IllegalArgumentException("ec");
        if (tracer == null)
            throw new IllegalArgumentException("tracer");

        this.ec = ec;
        this.tracer = tracer;
    }

    @Override
    public ExecutionContext prepare() {
        if (tracer.scopeManager().active() == null)
            return ec; // Nothing to propagate/do.

        return new TracedRefCountExecutionContextImpl();
    }

    @Override
    public void execute(Runnable runnable) {
        ec.execute(runnable);
    }

    @Override
    public void reportFailure(Throwable cause) {
        ec.reportFailure(cause);
    }

    class TracedRefCountExecutionContextImpl implements ExecutionContextExecutor {
        RefCountSpan span;

        public TracedRefCountExecutionContextImpl() {
            Span activeSpan = tracer.scopeManager().active().span();
            if (!(activeSpan instanceof RefCountSpan))
                throw new IllegalStateException("Active Span is not an instance of RefCountSpan");

            span = (RefCountSpan)activeSpan;
            span.capture(); // Signal we are holding a reference.
        }

        @Override
        public void execute(Runnable runnable) {
            ec.execute(new Runnable() {
                @Override
                public void run() {
                    try (Scope scope = tracer.scopeManager().activate(span, true)) {
                        runnable.run();
                    }
                }
            });
        }

        @Override
        public void reportFailure(Throwable cause) {
            ec.reportFailure(cause);
        }
    }
}
