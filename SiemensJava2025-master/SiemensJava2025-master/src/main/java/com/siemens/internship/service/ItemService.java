package com.siemens.internship.service;

import com.siemens.internship.model.Item;
import com.siemens.internship.repository.ItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class ItemService {

    private static final Logger logger = LoggerFactory.getLogger(ItemService.class); // Gotta have logs.

    private final ItemRepository itemRepository;
    private final Executor taskExecutor; // This is our custom thread pool for async jobs.

    @Autowired
    public ItemService(ItemRepository itemRepository,
                       @Qualifier("taskExecutor") Executor taskExecutor) { // DI for the repo and our task executor.
        this.itemRepository = itemRepository;
        this.taskExecutor = taskExecutor;
    }

    // Simple enough, get all items.
    public List<Item> findAll() {
        logger.info("Fetching all items");
        return itemRepository.findAll();
    }

    // Find one item. Might not exist, so an Optional is good here.
    public Optional<Item> findById(Long id) {
        logger.info("Fetching item with id: {}", id);
        return itemRepository.findById(id);
    }

    // Saving an item. Could be new or an update. Making it transactional.
    @Transactional
    public Item save(Item item) {
        logger.info("Saving item: {}", item.getName());
        // Could add more checks or logic here before it hits the DB.
        return itemRepository.save(item);
    }

    /**
     * Deleting an item. Check if it's there first, then delete.
     * Transactional too, just in case.
     * Returns true if it was deleted, false if not found.
     */
    @Transactional
    public boolean deleteById(Long id) {
        logger.info("Attempting to delete item with id: {}", id);
        if (itemRepository.existsById(id)) {
            itemRepository.deleteById(id);
            logger.info("Successfully deleted item with id: {}", id);
            return true;
        }
        logger.warn("Item with id: {} not found for deletion.", id);
        return false;
    }

    /**
     * This is the tricky async processing bit.
     * Original problems:
     *  - Returned way too early before anything was done.
     *  - Shared lists/counters without sync = trouble with threads.
     *  - Errors in one item's processing could mess things up silently.
     *
     * How I fixed it:
     *  - Each item gets its own CompletableFuture.
     *  - Use CompletableFuture.allOf() to wait for ALL of them.
     *  - Collect results only from successful futures.
     *  - Using our Spring-managed 'taskExecutor'.
     *  - No more shared list modified by all threads during processing.
     */
    @Async("taskExecutor") // Run this whole method on our custom thread pool.
    @Transactional // One transaction for the whole batch. Could change this if items need to succeed/fail independently.
    public CompletableFuture<List<Item>> processItemsAsync() {
        logger.info("Starting asynchronous processing of all items.");
        List<Long> itemIds = itemRepository.findAllItemIds();

        // No items? Nothing to do, return an empty list fast.
        if (itemIds.isEmpty()) {
            logger.info("No items found to process.");
            return CompletableFuture.completedFuture(List.of());
        }

        // Spin off a separate async job (CompletableFuture) for each item ID.
        List<CompletableFuture<Item>> futures = itemIds.stream()
                .map(id -> processSingleItemAsync(id, taskExecutor)) // Pass the executor along.
                .collect(Collectors.toList());

        // Now, wait for ALL those little jobs to finish (or fail).
        // thenApply runs after allOf is done.
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> { // 'v' is void here, allOf doesn't combine results itself.
                    // Ok, all futures are done. Let's see what we got.
                    // Filter out any 'nulls' - those are items that failed processing.
                    List<Item> successfullyProcessedItems = futures.stream()
                            .map(future -> {
                                try {
                                    return future.join(); // Get the result from the future.
                                } catch (Exception e) {
                                    // Should be rare if processSingleItemAsync handles its errors,
                                    // but good to catch if a future itself blew up.
                                    logger.error("Error joining future for an item: {}", e.getMessage());
                                    return null; // Mark as failed.
                                }
                            })
                            .filter(item -> item != null) // Only keep non-null (successful) items.
                            .collect(Collectors.toList());
                    logger.info("Asynchronous processing completed. Successfully processed {} items.", successfullyProcessedItems.size());
                    return successfullyProcessedItems; // This is the list of successfully processed items.
                });
    }

    /**
     * Helper for processing just one item in the background.
     * Fetches, updates status, saves.
     * Returns a CompletableFuture with the Item, or null if it failed.
     */
    private CompletableFuture<Item> processSingleItemAsync(Long id, Executor executor) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Could put a Thread.sleep(100) here to simulate more work,
                // but can slow down tests.

                Optional<Item> itemOptional = itemRepository.findById(id);
                if (itemOptional.isEmpty()) {
                    logger.warn("Item with ID {} not found during async processing. Skipping.", id);
                    return null; // Item's gone, nothing to do for this one.
                }

                Item item = itemOptional.get();
                // item.setStatus("PROCESSING"); // Could set an intermediate status
                // itemRepository.save(item);

                // Simulate some work if needed
                // try { Thread.sleep(new Random().nextInt(50) + 50); } catch (InterruptedException ignored) {}

                item.setStatus("PROCESSED");
                Item savedItem = itemRepository.save(item); // This is where the actual DB update happens for this item.
                logger.debug("Successfully processed and saved item with ID: {}", id);
                return savedItem; // Success!

            } catch (Exception e) {
                // Catch-all for this one item's processing.
                // Log it and return null so other items can continue.
                logger.error("Error processing item with ID {}: {}", id, e.getMessage(), e);
                return null; // Indicate failure for this specific item.
            }
        }, executor); // Make sure this runs on our executor.
    }
}