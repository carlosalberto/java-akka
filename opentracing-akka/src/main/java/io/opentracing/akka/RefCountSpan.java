package io.opentracing.akka;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;

public class RefCountSpan implements Span {
    Span wrapped;
    AtomicInteger refCount = new AtomicInteger(1);

    public RefCountSpan(Span wrapped) {
        this.wrapped = wrapped;
    }

    public static Scope startActive(Tracer.SpanBuilder builder, Tracer tracer) {
        return tracer.scopeManager().activate(new RefCountSpan(builder.startManual()), true);
    }

    public void capture() {
        refCount.incrementAndGet();
    }

    @Override
    public SpanContext context() {
        return wrapped.context();
    }

    @Override
    public void finish() {
        if (refCount.decrementAndGet() == 0) {
            wrapped.finish();
        }
    }

    @Override
    public void finish(long finishMicros) {
        wrapped.finish(finishMicros);
    }

    @Override
    public RefCountSpan setTag(String key, String value) {
        wrapped.setTag(key, value);
        return this;
    }

    @Override
    public RefCountSpan setTag(String key, boolean value) {
        wrapped.setTag(key, value);
        return this;
    }

    @Override
    public RefCountSpan setTag(String key, Number value) {
        wrapped.setTag(key, value);
        return this;
    }

    @Override
    public final Span log(Map<String, ?> fields) {
        wrapped.log(fields);
        return this;
    }

    @Override
    public final RefCountSpan log(long timestampMicros, Map<String, ?> fields) {
        wrapped.log(timestampMicros, fields);
        return this;
    }

    @Override
    public Span log(String event) {
        wrapped.log(event);
        return this;
    }

    @Override
    public RefCountSpan log(long timestampMicroseconds, String event) {
        wrapped.log(timestampMicroseconds, event);
        return this;
    }

    @Override
    public RefCountSpan setBaggageItem(String key, String value) {
        wrapped.setBaggageItem(key, value);
        return this;
    }

    @Override
    public String getBaggageItem(String key) {
        return wrapped.getBaggageItem(key);
    }

    @Override
    public RefCountSpan setOperationName(String operationName) {
        wrapped.setOperationName(operationName);
        return this;
    }

    @Override
    public String toString() {
        return wrapped.toString();
    }
}
