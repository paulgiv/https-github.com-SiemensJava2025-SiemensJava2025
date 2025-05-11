package com.siemens.internship.service; // Ensure this package is correct for your tests

import com.siemens.internship.model.Item;
import com.siemens.internship.repository.ItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor; // For a test executor

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class) // This gets Mockito ready for us.
class ItemServiceTests {

    @Mock // We'll mock the ItemRepository for these service unit tests.
    private ItemRepository itemRepository;

    // Need an Executor for testing the async logic. A real one is fine for tests.
    private Executor taskExecutor;

    @InjectMocks // This injects the mocked itemRepository and taskExecutor into itemService.
    private ItemService itemService;

    @BeforeEach
    void setUp() {
        // Set up a simple thread pool for tests.
        // Could also use a synchronous executor (Runnable::run) for more predictable
        // CompletableFuture execution if strict unit testing of chained calls is needed.
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(10);
        executor.setThreadNamePrefix("TestItemSvcProc-");
        executor.initialize();
        taskExecutor = executor;

        // If @InjectMocks doesn't pick up 'taskExecutor' by name,
        // or if the service constructor has @Qualifier, you might need to create itemService manually:
        itemService = new ItemService(itemRepository, taskExecutor);
    }

    // Test the main async path: all items get processed.
    @Test
    void processItemsAsync_shouldProcessAllItems() throws Exception {
        Item item1 = new Item(1L, "Item 1", "Desc 1", "NEW", "email1@test.com");
        Item item2 = new Item(2L, "Item 2", "Desc 2", "NEW", "email2@test.com");

        // What our mocked repo should do:
        when(itemRepository.findAllItemIds()).thenReturn(Arrays.asList(1L, 2L));
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item1));
        when(itemRepository.findById(2L)).thenReturn(Optional.of(item2));
        // When 'save' is called, pretend it worked and return the item (maybe with status updated).
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> {
            Item itemToSave = invocation.getArgument(0);
            // Simulate the status update that would happen in real processing.
            itemToSave.setStatus("PROCESSED");
            return itemToSave;
        });

        // Call the async method.
        CompletableFuture<List<Item>> futureResult = itemService.processItemsAsync();
        // Wait for it to finish (with a timeout, just in case).
        List<Item> processedItems = futureResult.get(10, TimeUnit.SECONDS);

        assertNotNull(processedItems);
        assertEquals(2, processedItems.size(), "Should have processed two items.");
        // Check if all items in the result list actually have "PROCESSED" status.
        assertTrue(processedItems.stream().allMatch(item -> "PROCESSED".equals(item.getStatus())), "All items should be PROCESSED.");

        // Verify repo calls.
        verify(itemRepository, times(1)).findById(1L);
        verify(itemRepository, times(1)).findById(2L);
        verify(itemRepository, times(2)).save(any(Item.class)); // Save called for each.
    }

    // Test case: one item is missing, others should still process.
    @Test
    void processItemsAsync_whenItemNotFound_shouldSkipAndProcessOthers() throws Exception {
        Item item1 = new Item(1L, "Item 1", "Desc 1", "NEW", "email1@test.com");
        // Item with ID 2 will not be found by the repo.

        when(itemRepository.findAllItemIds()).thenReturn(Arrays.asList(1L, 2L));
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item1));
        when(itemRepository.findById(2L)).thenReturn(Optional.empty()); // Uh oh, item 2 is gone!
        when(itemRepository.save(any(Item.class))).thenAnswer(invocation -> { // Save for item1
            Item itemToSave = invocation.getArgument(0);
            itemToSave.setStatus("PROCESSED");
            return itemToSave;
        });

        CompletableFuture<List<Item>> futureResult = itemService.processItemsAsync();
        List<Item> processedItems = futureResult.get(5, TimeUnit.SECONDS);

        assertNotNull(processedItems);
        assertEquals(1, processedItems.size(), "Only item1 should be in the processed list.");
        assertEquals(1L, processedItems.get(0).getId());
        assertEquals("PROCESSED", processedItems.get(0).getStatus());

        // Check repo calls. findById called for both, save only for item1.
        verify(itemRepository, times(1)).findById(1L);
        verify(itemRepository, times(1)).findById(2L);
        verify(itemRepository, times(1)).save(any(Item.class));
    }

    // Test case: DB save fails for one item, others should still go through.
    @Test
    void processItemsAsync_whenSaveFailsForItem_shouldHandleAndProcessOthers() throws Exception {
        Item item1 = new Item(1L, "Item 1", "Desc 1", "NEW", "email1@test.com");
        Item item2 = new Item(2L, "Item 2", "Desc 2", "NEW", "email2@test.com"); // Save for this one will fail.

        when(itemRepository.findAllItemIds()).thenReturn(Arrays.asList(1L, 2L));
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item1));
        when(itemRepository.findById(2L)).thenReturn(Optional.of(item2));

        // item1 saves fine.
        when(itemRepository.save(argThat(item -> item.getId().equals(1L)))).thenAnswer(inv -> {
            Item i = inv.getArgument(0);
            i.setStatus("PROCESSED");
            return i;
        });
        // item2 save throws an error!
        when(itemRepository.save(argThat(item -> item.getId().equals(2L))))
                .thenThrow(new RuntimeException("Simulated database save error for item 2"));

        CompletableFuture<List<Item>> futureResult = itemService.processItemsAsync();
        List<Item> processedItems = futureResult.get(5, TimeUnit.SECONDS);

        assertNotNull(processedItems);
        assertEquals(1, processedItems.size(), "Only item1 should be successfully processed.");
        assertEquals(1L, processedItems.get(0).getId());
        assertEquals("PROCESSED", processedItems.get(0).getStatus());

        // Verify save was attempted for both, even if one failed.
        verify(itemRepository, times(1)).save(argThat(i -> i.getId().equals(1L)));
        verify(itemRepository, times(1)).save(argThat(i -> i.getId().equals(2L)));
    }

}