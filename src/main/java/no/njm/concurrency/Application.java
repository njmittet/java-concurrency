package no.njm.concurrency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

public class Application {

    private static final Logger log = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        Application application = new Application();
        application.completableFutures();
        waitBeforeExit();
    }

    private void completableFutures() {
        // Chaining callbacks. supplyAsync() is used when the result should be used.

        CompletableFuture.supplyAsync(() -> 5 * 2)
                         .thenApply(i -> i * 2)
                         .thenAccept(i -> log.debug("Calculated i to be {}.", i))
                         .thenRun(() -> log.debug("CompletableFuture finished."));

        // All completion stages run in the same thread unless specified. runAsync() does not
        // care about the result.
        CompletableFuture.runAsync(() -> log.info("Thread: {}", Thread.currentThread().getName()))
                         .thenRun(() -> log.debug("Running in same thread: {}", Thread.currentThread().getName()))
                         .thenRunAsync(() -> log.debug("Can run in new thread: {}", Thread.currentThread().getName()));

        // Completable futures can be composed and combined.
        CompletableFuture.supplyAsync(() -> "Hello")
                         .thenCompose(s -> CompletableFuture
                                 .supplyAsync(() -> s + " World"))
                         .thenAccept(log::debug);

    }

    private static void waitBeforeExit() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            log.error("Thread interrupted.", e);
        }
    }
}
