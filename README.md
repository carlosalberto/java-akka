# OpenTracing Akka Instrumentation
OpenTracing instrumentation for Akka.

## Usage

Please see the examples directory. Overall, an `ExecutionContext` is wrapped
so the active Span can be captured and activated for a given Scala `Future`.

Create a `TracedExecutionContext` wrapping the actually used `ExecutionContext`,
and pass it around when creating `Future`s:

```java
// Instantiate tracer
Tracer tracer = ...
ExecutionContext ec = new TracedExecutionContext(executionContext, tracer);
```

### Span Propagation

```java
future(new Callable<String>() {
    @Override
    public String call() {
	// The active Span at Future creation time, if any,
	// will be captured and restored here.
        tracer.scopeManager().active().setTag("status.code", getStatusCode());
    }
}, ec);
```

`Future.onComplete` and other `Future` methods will
capture too *any* active `Span` by the time they were registered, so you have
to make sure that both happened under the same active `Span`/`Scope` for this
to work smoothly.

`Span` lifetime handling is not done at the `TracedExecutionContext`,
and hence explicit calls to `Span.finish()` must be put in place - usually
either in the last `Future`/message block or in a `onComplete` callback
function:

```java
future(new Callable<String>() {
    ...
}, ec)
.onComplete(new OnComplete<String>{
    @Override
    public void onComplete(Throwable t, String s) {
        tracer.scopeManager().active().span().finish();
    }
}, ec);
```


### Reference-Count Span handling

Reference-count style Span handling is also supported through a `Span` wrapper
class, which will keep track of how many `Span.finish()` calls need to happen
before the wrapped `Span` is actually finished.

A `TracedRefCountExecutionContext` is provided to be used along `RefCountSpan`,
and will automatically increase/decrease the reference count for the `Span` (besided propagating such
`Span`):

```java
ExecutionContext ec = new TracedRefCountExecutionContext(executionContext, tracer);
...
try (Scope scope = RefCountSpan.startActive(tracer.buildSpan("request"), tracer)) {
    future(new Callable<String>() {
	// Span will be reactivated here
	...
	future(new Callable<String>() {
	    // Span will be reactivated here as well.
            // By the time this future is done,
            // the Span will be automatically finished.
	}, ec);
    }, ec)
} 
```

Reference count is also increased when registering `onComplete`, `andThen`, `map`, and similar
`Future` methods, and decreased as well upon being executed:

```java
future(new Callable<String>() {
    ...
}, ec)
.map(new Mapper<String, String>() {
    ...
}, ec)
.onComplete(new OnComplete<String>() {
    // No need to call `Span.finish()` here at all, as
    // lifetime handling is done implicitly.
    ...
}, ec);
```

### Issues.

The approach to do reference count here was to use a `Span` wrapper, which
finishes the wrapped `Span` depending on the number of `finish()` calls.
This wouldn't work just fine with `Span.finish(long)` as the parameter
would then be ignored. Should we probably then throw an Exception here?

Currently `TracedRefCountExecutionContext` expects any active `Span`
to be an instance of `RefCountSpan` - we could easily make it support
standard `Span` instances - but in this case a question arises: should
we call `Span.finish()` upon completion?
