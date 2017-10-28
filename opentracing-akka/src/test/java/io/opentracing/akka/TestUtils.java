package io.opentracing.akka;

import akka.util.Timeout;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

public final class TestUtils {
    private TestUtils() {}

    final static int DEFAULT_TIMEOUT = 5;

    public static FiniteDuration getDefaultDuration() {
        return Duration.create(DEFAULT_TIMEOUT, "seconds");
    }

    public static Timeout getDefaultTimeout() {
        return new Timeout(getDefaultDuration());
    }
}
