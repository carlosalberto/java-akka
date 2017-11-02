package io.opentracing.akka.examples;

import java.util.concurrent.Callable;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import io.opentracing.Tracer;
import io.opentracing.Scope;
import io.opentracing.Span;
import io.opentracing.akka.RefCountSpan;
import io.opentracing.akka.TracedRefCountExecutionContext;

import static akka.dispatch.Futures.future;

public class RefCountingExample {
    public static void main(String [] args) {
        Tracer tracer = null; // Set your tracer here.
        ExecutionContext ec = new TracedRefCountExecutionContext(ExecutionContext.global(), tracer);

        // Span will be propagated and finished when all the 3 tasks are done.
        try (Scope scope = RefCountSpan.startActive(tracer.buildSpan("one"), tracer)) {
            future(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    tracer.scopeManager().active().span().setTag("server1.result", 7);
                    return Boolean.TRUE;
                }
            }, ec);

            future(new Callable<Boolean>() {
                @Override
                public Boolean call() {
                    future(new Callable<Boolean>() {
                        @Override
                        public Boolean call() {
                            tracer.scopeManager().active().span().setTag("server3.result", 54);
                            return Boolean.TRUE;
                        }
                    }, ec);

                    tracer.scopeManager().active().span().setTag("server2.result", 37);
                    return Boolean.TRUE;
                }
            }, ec);
        }
    }
}
