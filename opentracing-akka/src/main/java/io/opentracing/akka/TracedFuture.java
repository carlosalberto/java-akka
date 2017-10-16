package io.opentracing.akka;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.OnComplete;
import scala.concurrent.Future;

import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.tag.Tags;

public final class TracedFuture
{
    private TracedFuture()
    {
    }

    public static <T> Future<T> tracedFuture(Future<T> future, ActorRef actorRef, ActorSystem sys, Tracer tracer) {
        final Span span = tracer.buildSpan(Constants.DEFAULT_OPERATION_NAME).startManual();
        span.setTag(Tags.COMPONENT.getKey(), Constants.COMPONENT_NAME);
        span.setTag(Constants.ACTOR_PATH, actorRef.path().elements().mkString("/"));

        return future.andThen(new OnComplete<T>() {
            @Override
            public void onComplete(Throwable failure, Object success) throws Throwable {
                if (failure != null) {
                    span.setTag(Tags.ERROR.getKey(), Boolean.TRUE);
                }

                span.finish();
            }
        }, sys.dispatcher());
    }
}
