package no.njm.concurrency;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

public class ApplicationTest {

    @Before
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