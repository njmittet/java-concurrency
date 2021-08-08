package no.njm.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompletableFutureTest {

    private static final Logger log = LoggerFactory.getLogger(CompletableFutureTest.class);

    private static final int USER_ID = 100;
    private static final int USER_RATING = 1000;

    private static ExecutorService executorService;

    @BeforeAll
    static void setUp() {
        // Create a pool of threads with custom naming.
        executorService = Executors.newFixedThreadPool(3, new ThreadFactory() {
            int count = 1;

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "custom-thread-" + count++);
                // Set the thread to be a user thread. Threads may be either user threads or a daemon threads depending
                // on which type the parent thread is.
                thread.setDaemon(false);
                return thread;
            }
        });
    }

    @AfterAll
    static void tearDown() {
        // User threads are high-priority threads, and the JVM will usually not exit before all user threads are
        // terminated, e.g. by calling executorService.shutdown(). When used in unittests the explicit shutdown is
        // not strictly necessary as the test runner calls System.exit, which kills all threads.
        executorService.shutdown();
    }

    @Test
    public void completableFutureHasCompleted() {
        var completableFuture = CompletableFuture.completedFuture("Completed");

        assertTrue(completableFuture.isDone());
        assertEquals("Completed", completableFuture.getNow(null));
    }

    @Test
    public void completableFutureHasNotCompleted() {
        var completableFuture = new CompletableFuture<String>();

        assertFalse(completableFuture.isDone());
        assertEquals("NULL", completableFuture.getNow("NULL"));
    }

    @Test
    public void handleFailedFutureWithHandle() throws ExecutionException, InterruptedException {
        var completableFuture = CompletableFuture.failedFuture(new RuntimeException("ERROR"));

        // handle() gives access to both the result and the possible exception thrown by the completable future,
        // which allows error recovery and result transformation.
        var failedFuture = completableFuture.handle((result, throwable) -> {
            if (throwable == null) {
                return result;
            } else {
                return throwable.getMessage();
            }
        });

        assertEquals("ERROR", failedFuture.get());
    }

    @Test
    public void handleFailedFutureWithWhenComplete() {
        var completableFuture = CompletableFuture.failedFuture(new RuntimeException("ERROR"));

        // whenComplete() gives access to both the result and the possible exception thrown by the completable
        // future so they can be consumed and acted upon, but error recovery and result transformation is not
        // supported. When an error occurs, an ExecutionException will be thrown when 'get()' is called.
        var failedFuture = completableFuture.whenComplete((result, throwable) -> {
            if (throwable == null) {
                log.info("Handling successful result {}.", result);
            } else {
                log.error("Error!", throwable);
            }
        });

        assertThrows(ExecutionException.class, failedFuture::get);
    }

    @Test
    public void handleFailedFutureWithExceptionally() throws ExecutionException, InterruptedException {
        var completableFuture = CompletableFuture.failedFuture(new RuntimeException("ERROR"));

        // exceptionally() only gives access to exceptions thrown by the completable future. The intended
        // use is only recovery from error. If the completable future completed successfully, the 'exceptionally'
        // logic is skipped.
        var exceptionally = completableFuture.exceptionally(Throwable::getMessage);

        assertEquals("ERROR", exceptionally.get());
    }

    @Test
    public void handleExceptionWithHandle() throws ExecutionException, InterruptedException {
        String name = null;

        // This example is equivalent to the example in #handleFailedCompletableFutureWithHandle.
        var completableFuture = CompletableFuture.supplyAsync(() -> {
            if (name == null) {
                throw new IllegalArgumentException("ERROR");
            }
            return name;
        }).handle((result, throwable) -> throwable == null
                ? result
                : throwable.getCause().getMessage());

        assertEquals("ERROR", completableFuture.get());
    }

    @Test
    public void completeWithCompleteExceptionally() {
        var completableFuture = new CompletableFuture<>();
        completableFuture.completeExceptionally(new RuntimeException("ERROR"));

        // When a completable future is completed exceptionally, it WILL throw when 'get()' is called.
        var executionException = assertThrows(ExecutionException.class, completableFuture::get);
        assertEquals(RuntimeException.class, executionException.getCause().getClass());
    }

    @Test
    public void completableFutureWithResult() throws ExecutionException, InterruptedException {
        // Using a custom class to verify that the last completion stage completed successfully.
        var completer = new Completer<Integer>();

        // supplyAsync() is used when the result should be used as it takes a Supplier as argument and returns a
        // CompletableFuture<U> as result value. thenApply() is similar, but it takes a Function as input and applies it
        // on the result from the previous CompletionStage before it returns a CompletableFuture<U> that holds a
        // value returned by the function. thenAccept() takes as Consumer as input, but returns a
        // CompletableFuture<Void>, which makes it useful when the input must be processed but returning a value is
        // not required.
        var completableFuture = CompletableFuture.supplyAsync(() -> 5 * 2)
                .thenApply(i -> i * 2)
                .thenAccept(completer::setCompleted);

        completableFuture.get();
        assertTrue(completer.isCompleted());
        assertEquals(20, completer.getCompletionValue());
    }

    @Test
    public void completableFutureWithNoResult() throws ExecutionException, InterruptedException {
        // Using a custom class to verify that the last completion stage completed successfully.
        var completer = new Completer<Void>();

        // Both runAsync() and thenRun() takes Runnable, which has no arguments, as input parameter and returns a
        // CompletableFuture<Void>, which means that neither of the methods returns a result that can be used by the
        // next CompletionStage.
        var completableFuture = CompletableFuture.runAsync(() -> log.debug("2 + 2 = {}.", 2 + 2))
                .thenRun(completer::setCompleted);

        completableFuture.get();
        assertTrue(completer.isCompleted());
    }

    /**
     * A CompletionStage is executed asynchronously when the method ends with the keyword 'Async', which is the case for
     * supplyAsync() and runAsync(). Chained completion stages like thenApply(), thenAccept() and thenRun() are executed
     * on the same thread as the initial CompletionStage, while Async chained completion stages like thenApplyAsync(),
     * thenAcceptAsync(), thenRunAsync() may be executed on a different thread.
     */
    @Test
    public void completableFutureUsingCommonForkJoinCommonPool() throws ExecutionException, InterruptedException {
        var completableFuture = CompletableFuture.runAsync(this::logThread)
                .thenRun(this::logThread)
                .thenRunAsync(this::logThread);

        completableFuture.get();
        assertTrue(completableFuture.isDone());
    }

    /**
     * Java offers two types of threads: user threads and daemon threads. By default (when no custom Executor is
     * specified) asynchronous execution uses daemon threads obtained from the common ForkJoinPool implementation to
     * execute Runnable tasks.
     */
    @Test
    public void completableFutureUsingCustomThreadPool() throws ExecutionException, InterruptedException {
        // The initial CompletionStage runs on a user thread from the custom thread pool.
        var completableFuture = CompletableFuture.runAsync(this::logThread, executorService)
                // Runs on the same thread as the initial CompletionStage.
                .thenRun(this::logThread)
                // May run on a new daemon thread from the common ForkJoinPool.
                .thenRunAsync(this::logThread)
                // Runs on a new user thread from the custom thread pool.
                .thenRunAsync(this::logThread, executorService);

        completableFuture.get();
        assertTrue(completableFuture.isDone());
    }

    /**
     * The ability to combine CompletableFuture instances in a chain of computation steps, which result also is a
     * CompletableFuture, is often referred to as a monadic design pattern. thenApply() and thenCompose() implements the
     * basic building blocks of the monadic pattern and does conceptually relate to the methods map() and flatMap().
     * <p>
     * Given a chained call to two methods that both returns completable futures: First a call getUserInfo() is made,
     * and on its completion, a call getUserRating() returns the final result:
     */
    @Test
    public void chainCompletableFutureWithThenApply() throws ExecutionException, InterruptedException {
        // thenApply() returns the nested futures, making it necessary to call get() twice in order to get hold of
        // the final result.
        CompletableFuture<CompletableFuture<UserRating>> userRatingResult =
                getUserInfo(USER_ID).thenApply(this::getUserRating);

        CompletableFuture<UserRating> userRatingCompletableFuture = userRatingResult.get();
        UserRating userRating = userRatingCompletableFuture.get();

        assertEquals(USER_ID, userRating.userId());
        assertEquals(USER_RATING, userRating.userRating());
    }

    /**
     * thenApply() is useful when the result should be transformed to new result. Hence the reference to map().
     * thenCompose() is similar to thenApply() since it also returns a new completion stage, but when the chained calls
     * returns all returns completable futures, thenCompose() flattens the futures and returns a single future holding
     * the result directly. If the idea is to chain CompletableFuture methods then it’s better to use thenCompose().
     */
    @Test
    public void chainCompletableFutureWithThenCompose() throws ExecutionException, InterruptedException {
        // thenCompose() flattens the nested futures futures, making just a single get() necessary in order to get
        // hold of the final result.
        CompletableFuture<UserRating> userRatingResult = getUserInfo(USER_ID).thenCompose(this::getUserRating);

        UserRating userRating = userRatingResult.get();

        assertEquals(USER_ID, userRating.userId());
        assertEquals(USER_RATING, userRating.userRating());
    }

    /**
     * The thenCombine() method can be used to combine the result of one future with the result of another future.
     */
    @Test
    public void combineCompletableFuturesWithThenCombine() throws ExecutionException, InterruptedException {
        // thenCombine() accepts both a completable future and a function with two arguments, which is used to
        // process the result of both the completable futures.
        var combinedResult = CompletableFuture.supplyAsync(() -> "First")
                .thenCombine(CompletableFuture.supplyAsync(() -> "Second"),
                        (firstFutureResult, secondFutureResult) -> firstFutureResult + " " + secondFutureResult)
                .get();

        assertEquals("First Second", combinedResult);
    }

    /**
     * A simpler variant of thenCombine() is thenAcceptBoth(), which can be used when the combined result of two futures
     * can be used without being returned.
     */
    @Test
    public void useResultFromCompletableFuturesWithThenAcceptBoth() throws ExecutionException, InterruptedException {
        // Using a custom class to verify that the last completion stage completed successfully.
        var completer = new Completer<String>();

        CompletableFuture.supplyAsync(() -> "First")
                .thenAcceptBoth(CompletableFuture.supplyAsync(() -> "Second"),
                        (firstFutureResult, secondFutureResult) ->
                                completer.setCompleted(firstFutureResult + " " + secondFutureResult))
                .get();

        assertTrue(completer.isCompleted());
        assertEquals("First Second", completer.getCompletionValue());
    }

    /**
     * Execute multiple completable futures in parallel and wait for all of them to execute and then process their
     * combined results.
     */
    @Test
    public void runMultipleCompletableFuturesInParallel() throws ExecutionException, InterruptedException {
        CompletableFuture<String> firstFuture = CompletableFuture.supplyAsync(() -> "First");
        CompletableFuture<String> secondFuture = CompletableFuture.supplyAsync(() -> "Second");
        CompletableFuture<String> thirdFuture = CompletableFuture.supplyAsync(() -> "Third");

        // the return type of the CompletableFuture.allOf() is a CompletableFuture<Void>, hence it does not return
        // the result of the executed futures.
        CompletableFuture<Void> combinedResult = CompletableFuture.allOf(firstFuture, secondFuture, thirdFuture);
        combinedResult.get();

        assertTrue(firstFuture.isDone());
        assertTrue(secondFuture.isDone());
        assertTrue(thirdFuture.isDone());

        // Get and combine the results from the futures.
        String joinedResult = Stream.of(firstFuture, secondFuture, thirdFuture)
                .map(CompletableFuture::join)
                .collect(Collectors.joining(" "));

        assertEquals("First Second Third", joinedResult);
    }

    /**
     * Helper method used by several tests.
     */
    private void logThread() {
        log.debug("Thread: {}, isDaemon: {}", Thread.currentThread().getName(), Thread.currentThread().isDaemon());
    }

    /**
     * Helper class used by several tests when the completable future does not return a result.
     */
    private static class Completer<T> {

        private boolean completed;
        private T completionValue;

        public void setCompleted() {
            completed = true;
        }

        public void setCompleted(T completionValue) {
            this.completionValue = completionValue;
            setCompleted();
        }

        public boolean isCompleted() {
            return completed;
        }

        public T getCompletionValue() {
            return completionValue;
        }
    }

    /**
     * Helper method used by tests {@link #chainCompletableFutureWithThenApply} and {@link
     * #chainCompletableFutureWithThenCompose()}
     */
    public CompletableFuture<UserInfo> getUserInfo(int userId) {
        return CompletableFuture.supplyAsync(() -> new UserInfo(userId));
    }

    /**
     * Helper method used by tests {@link #chainCompletableFutureWithThenApply} and {@link
     * #chainCompletableFutureWithThenCompose()}
     */
    public CompletableFuture<UserRating> getUserRating(UserInfo userInfo) {
        return CompletableFuture.supplyAsync(() -> new UserRating(userInfo.userId(), USER_RATING));
    }

    /**
     * Record used by tests {@link #chainCompletableFutureWithThenApply} and {@link
     * #chainCompletableFutureWithThenCompose()}
     */
    private record UserInfo(int userId) {

    }

    /**
     * Record used by tests {@link #chainCompletableFutureWithThenApply} and {@link
     * #chainCompletableFutureWithThenCompose()}
     */
    private record UserRating(int userId, int userRating) {

    }

}