package io.opentracing.akka;

import java.util.concurrent.Callable;

import akka.util.Timeout;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import io.opentracing.mock.MockTracer;

public final class TestUtils {
    private TestUtils() {}

    public final static int DEFAULT_TIMEOUT = 5;

    public static FiniteDuration getDefaultDuration() {
        return Duration.create(DEFAULT_TIMEOUT, "seconds");
    }

    public static Timeout getDefaultTimeout() {
        return new Timeout(getDefaultDuration());
    }

    public static Callable<Integer> finishedSpansSize(MockTracer tracer) {
        return new Callable<Integer>() {
            @Override
            public Integer call() throws Exception {
                return tracer.finishedSpans().size();
            }
        };
    }
}
