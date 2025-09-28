package com.miguec.locking.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.Transient;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import com.miguec.locking.domain.Product;
import com.miguec.locking.domain.ProductWithVersion;
import com.miguec.locking.repositories.ProductRepository;
import com.miguec.locking.repositories.ProductWithVersionRepository;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class ProductServiceTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductWithVersionRepository productWithVersionRepository;

    @Test
    void testWithoutOptimisticLock() throws InterruptedException {

        var product = Product.builder()
                .name("Product 1")
                .description("Description 1")
                .stock(100)
                .build();

        var productSaved = productRepository.save(product);
        log.info("Product saved with ID: {}", productSaved.getId());

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        var thread1 = new Thread(() -> {
            try {
                startLatch.await();
                var productToUpdate = productRepository.findById(productSaved.getId()).get();
                doSomeLogic();
                productRepository.save(productToUpdate.toBuilder().stock(10).build());
                log.info("Thread 1 updated product stock to 10");
            } catch (InterruptedException e) {
                log.error("Thread 1 interrupted", e);
            } finally {
                doneLatch.countDown();
            }
        }, "Alice");

        var thread2 = new Thread(() -> {
            try {
                startLatch.await();
                var productToUpdate = productRepository.findById(productSaved.getId()).get();
                doSomeLogic();
                productRepository.save(productToUpdate.toBuilder().stock(20).build());
                log.info("Thread 2 updated product stock to 20");
            } catch (InterruptedException e) {
                log.error("Thread 2 interrupted", e);
            } finally {
                doneLatch.countDown();
            }
        }, "Bob");

        thread1.start();
        thread2.start();

        startLatch.countDown();
        doneLatch.await();

        var finalProductSaved = productRepository.findById(productSaved.getId()).get();
        log.info("Product stock after threads: {}", finalProductSaved.getStock());

        assertThat(finalProductSaved.getId()).isEqualTo(productSaved.getId());
        assertThat(finalProductSaved.getStock()).isIn(10, 20);
    }

    private void doSomeLogic() throws InterruptedException {
        Thread.sleep((long) (Math.random() * 1000));
    }

    @Test
    void testWithOptimisticLock() throws InterruptedException {

        var product = ProductWithVersion.builder()
                .name("Product 1")
                .description("Description 1")
                .stock(50)
                .build();

        var productSaved = productWithVersionRepository.save(product);
        log.info("Product saved with ID: {} and version: {}", productSaved.getId(), productSaved.getVersion());

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        AtomicReference<Exception> aliceThreadException = new AtomicReference<>();
        AtomicReference<Exception> bobThreadException = new AtomicReference<>();
        AtomicBoolean aliceThreadSuccess = new AtomicBoolean(false);
        AtomicBoolean bobThreadSuccess = new AtomicBoolean(false);

        var aliceThread = new Thread(() -> {
            try {
                startLatch.await();
                var productUpdated = updateProduct(productSaved, 10);
                log.info("Thread {} updated product stock to {} and version: {}", Thread.currentThread().getName(),
                        productUpdated.getStock(),
                        productUpdated.getVersion());
                aliceThreadSuccess.set(true);
            } catch (InterruptedException e) {
                log.error("Thread 1 interrupted", e);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.error("Thread 1 failed with optimistic lock", e);
                aliceThreadException.set(e);
            } finally {
                doneLatch.countDown();
            }
        }, "Alice");

        var bobThread = new Thread(() -> {
            try {
                startLatch.await();
                var productUpdated = updateProduct(productSaved, 20);
                log.info("Thread {} updated product stock to {} and version: {}", Thread.currentThread().getName(),
                        productUpdated.getStock(),
                        productUpdated.getVersion());
                bobThreadSuccess.set(true);
            } catch (InterruptedException e) {
                log.error("Thread 2 interrupted", e);
            } catch (ObjectOptimisticLockingFailureException e) {
                log.error("Thread 2 failed with optimistic lock", e);
                bobThreadException.set(e);
            } finally {
                doneLatch.countDown();
            }
        }, "Bob");

        aliceThread.start();
        bobThread.start();

        startLatch.countDown();
        doneLatch.await();

        int successCount = (aliceThreadSuccess.get() ? 1 : 0) + (bobThreadSuccess.get() ? 1 : 0);
        int optimisticLockFailures = 0;

        if (aliceThreadException.get() instanceof ObjectOptimisticLockingFailureException) {
            optimisticLockFailures++;
        }
        if (bobThreadException.get() instanceof ObjectOptimisticLockingFailureException) {
            optimisticLockFailures++;
        }

        assertThat(optimisticLockFailures).isEqualTo(1); // Exactly one should fail with optimistic lock
        assertThat(successCount).isEqualTo(1); // Exactly one should succeed
    }

    @Transactional
    private ProductWithVersion updateProduct(ProductWithVersion productSaved, int stock) throws InterruptedException {
        var currentProduct = productWithVersionRepository.findById(productSaved.getId()).get();
        log.info("Thread {} found product with stock: {} and version: {}", Thread.currentThread().getName(),
                currentProduct.getStock(),
                currentProduct.getVersion());
        doSomeLogic();

        return productWithVersionRepository.save(currentProduct.toBuilder().stock(stock).build());
    }
}
