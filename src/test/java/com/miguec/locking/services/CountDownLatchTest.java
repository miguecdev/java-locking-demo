package com.miguec.locking.services;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class CountDownLatchTest {

    @Test
    void testCountDownLatch() throws InterruptedException {
        // Latch indicating "wait until we give the start signal"
        CountDownLatch startLatch = new CountDownLatch(1);

        // Latch indicating "wait until the 2 threads have finished"
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicInteger counter = new AtomicInteger(0);

        Runnable worker = () -> {
            try {
                // 1. wait until the test says "you can start"
                startLatch.await();

                // 2. simulated concurrent logic
                int value = counter.incrementAndGet();
                System.out.println(Thread.currentThread().getName() + " set value=" + value);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                // 3. indicate that I finished
                doneLatch.countDown();
            }
        };

        // Create and launch threads
        Thread t1 = new Thread(worker, "Worker-1");
        Thread t2 = new Thread(worker, "Worker-2");
        t1.start();
        t2.start();

        // Give the start signal (now both threads start together)
        startLatch.countDown();

        doneLatch.await();

        // At the end, counter should be 2 (both incremented)
        assertThat(counter.get()).isEqualTo(2);
    }

}
