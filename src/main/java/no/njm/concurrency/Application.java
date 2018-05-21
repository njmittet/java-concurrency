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

        // Calling a method when two different CompletableFutures completes.
        CompletableFuture<Customer> customerFuture = loadCustomer(1);
        CompletableFuture<Shop> shopFuture = findShop("Oslo");
        customerFuture.thenCombine(shopFuture, this::findRoute)
                      .thenRun(() -> log.debug("Routing complete."));

        customerFuture.thenAcceptBoth(shopFuture, (cust, shop) -> {
            final Route route = findRoute(cust, shop);
            //refresh GUI with route
        });

        // Catching exceptions asynchronously.
        CompletableFuture.supplyAsync(() -> 2 / 0)
                         .thenAccept(i -> log.debug("Result is: {}", i))
                         .exceptionally(e -> {
                             log.error("Divide by zero caused exception.", e);
                             return null;
                         });

        // A more flexible approach is handle() that takes a function receiving either correct
        // result or exception. handle() is called always, with either result or exception argument
        // being not-null. This is a one-stop catch-all strategy.
        CompletableFuture<String> completableFuture = new CompletableFuture<>();
        completableFuture.completeExceptionally(new RuntimeException());
        completableFuture.handle((ok, e) -> {
            if (ok != null) {
                return Integer.parseInt(ok);
            } else {
                log.error("handle() caught exception.", e);
                return -1;
            }
        });

    }

    private Route findRoute(Customer customer, Shop shop) {
        log.debug("Called with customer:{} and shop:{}", customer, shop);
        return new Route();
    }

    private CompletableFuture<Customer> loadCustomer(int customerId) {
        return CompletableFuture.completedFuture(new Customer(customerId));
    }

    private CompletableFuture<Shop> findShop(String city) {
        return CompletableFuture.completedFuture(new Shop(city));
    }

    private static void waitBeforeExit() {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            log.error("Thread interrupted.", e);
        }
    }

    private static class Customer {

        private final int customerId;

        Customer(int customerId) {
            this.customerId = customerId;
        }
    }

    private static class Shop {

        private final String shopName;

        Shop(String shopName) {
            this.shopName = shopName;
        }
    }

    private static class Route {

    }
}
