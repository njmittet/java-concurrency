package no.njm.concurrency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ApplicationTest {

    @BeforeEach
    public void setUp() {
    }

    @Test
    public void completedFutureExample() {
        CompletableFuture<String> cf = CompletableFuture.completedFuture("Message");
        assertTrue(cf.isDone());
        assertEquals("Message", cf.getNow(null));
    }

    @Test
    public void incompleteFutureExample() {
        CompletableFuture<String> cf = new CompletableFuture<>();
        assertTrue(!cf.isDone());
        assertNull(cf.getNow(null));
    }
}