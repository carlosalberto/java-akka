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


### Auto finish Span handling

Span auto-finish is supported through a reference-count system using the specific
`AutoFinishScopeManager` -which needs to be provided at `Tracer` creation time-,
along with using `TracedAutoFinishExecutionContext`:

```java
ScopeManager scopeManager = new AutoFinishScopeManager();
Tracer tracer = ... // Use the created scopeManager here.
ExecutionContext ec = new TracedAutoFinishExecutionContext(executionContext, tracer);
...
try (Scope scope = tracer.buildSpan("request").startActive()) {
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

Reference count for `Span`s is set to 1 at creation, and is increased when
registering `onComplete`, `andThen`, `map`, and similar
`Future` methods - and is decreased upon having such function/callback executed:

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
