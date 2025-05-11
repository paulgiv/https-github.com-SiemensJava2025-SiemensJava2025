package com.siemens.internship.controller;

import com.siemens.internship.model.Item;
import com.siemens.internship.service.ItemService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/items") // All item stuff goes through here.
public class ItemController {

    private static final Logger logger = LoggerFactory.getLogger(ItemController.class); // Controller logs, good for seeing requests.

    private final ItemService itemService;

    @Autowired
    public ItemController(ItemService itemService) { // Injecting the service.
        this.itemService = itemService;
    }

    // Helper to make bad request responses look nice with error details.
    private ResponseEntity<Object> handleValidationErrors(BindingResult result) {
        Map<String, String> errors = result.getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() == null ? "Invalid value" : fieldError.getDefaultMessage()
                ));
        logger.warn("Validation failed: {}", errors);
        return ResponseEntity.badRequest().body(errors); // 400 Bad Request with the errors.
    }

    // GET /api/items - fetch everything.
    @GetMapping
    public ResponseEntity<List<Item>> getAllItems() {
        logger.info("Received request to get all items");
        List<Item> items = itemService.findAll();
        return ResponseEntity.ok(items); // 200 OK.
    }

    // POST /api/items - make a new item. Validate first!
    @PostMapping
    public ResponseEntity<?> createItem(@Valid @RequestBody Item item, BindingResult result) {
        logger.info("Received request to create item: {}", item.getName());
        if (result.hasErrors()) {
            return handleValidationErrors(result); // If validation fails, send back 400.
        }
        Item savedItem = itemService.save(item);
        // Return 201 Created, and a Location header pointing to the new item.
        return ResponseEntity.created(URI.create("/api/items/" + savedItem.getId()))
                .body(savedItem);
    }

    // GET /api/items/{id} - get one item.
    @GetMapping("/{id}")
    public ResponseEntity<Item> getItemById(@PathVariable Long id) {
        logger.info("Received request to get item by id: {}", id);
        return itemService.findById(id)
                .map(item -> {
                    logger.info("Item found with id: {}", id);
                    return ResponseEntity.ok(item); // Found it, 200 OK.
                })
                .orElseGet(() -> {
                    logger.warn("Item not found with id: {}", id);
                    return ResponseEntity.notFound().build(); // Didn't find it, 404 Not Found.
                });
    }

    // PUT /api/items/{id} - update an existing item.
    @PutMapping("/{id}")
    public ResponseEntity<?> updateItem(@PathVariable Long id,
                                        @Valid @RequestBody Item itemDetails, // New details for the item.
                                        BindingResult result) {
        logger.info("Received request to update item with id: {}", id);
        if (result.hasErrors()) {
            return handleValidationErrors(result); // Validation failed, 400.
        }

        return itemService.findById(id) // First, see if the item even exists.
                .map(existingItem -> {
                    // It exists. Now, update its fields with the new details.
                    // Important: update the fields of the *existing* item, don't just swap it out
                    // or let the client change the ID.
                    existingItem.setName(itemDetails.getName());
                    existingItem.setDescription(itemDetails.getDescription());
                    existingItem.setStatus(itemDetails.getStatus());
                    existingItem.setEmail(itemDetails.getEmail());
                    // ID is from path, not payload.

                    Item updatedItem = itemService.save(existingItem); // Save the changes.
                    logger.info("Item updated successfully with id: {}", id);
                    return ResponseEntity.ok(updatedItem); // All good, 200 OK with the updated item.
                })
                .orElseGet(() -> {
                    logger.warn("Item not found with id: {} for update.", id);
                    return ResponseEntity.notFound().build(); // Item wasn't there to update, 404.
                });
    }

    // DELETE /api/items/{id} - remove one item.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteItem(@PathVariable Long id) {
        logger.info("Received request to delete item with id: {}", id);
        if (itemService.deleteById(id)) { // deleteById now tells us if it worked.
            logger.info("Item deleted successfully with id: {}", id);
            return ResponseEntity.noContent().build(); // Deleted, 204 No Content.
        } else {
            logger.warn("Item not found with id: {} for deletion.", id);
            return ResponseEntity.notFound().build(); // Wasn't there to delete, 404.
        }
    }

    /**
     * GET /api/items/process - Kicks off the big async job in ItemService.
     * This endpoint returns 202 Accepted RIGHT AWAY. The actual processing
     * happens in the background.
     */
    @GetMapping("/process")
    public ResponseEntity<Map<String, String>> processItems() {
        logger.info("Received request to process all items asynchronously.");
        CompletableFuture<List<Item>> processingFuture = itemService.processItemsAsync(); // Tell the service to start.

        // This part is just for logging on the server when the async job is done.
        // It doesn't block the HTTP response.
        processingFuture.whenComplete((items, ex) -> {
            if (ex != null) {
                logger.error("Asynchronous item processing failed globally.", ex);
            } else {
                logger.info("Asynchronous item processing completed. {} items processed successfully.", items.size());
            }
        });

        // Return 202 Accepted: means "Okay, I got your request, I'm working on it."
        return ResponseEntity.accepted().body(Map.of("message", "Item processing initiated asynchronously."));
    }


    // Basic error handler for things like ResponseStatusException thrown in the controller.
    // For a real app, a @ControllerAdvice is better for global exception handling.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatusException(ResponseStatusException ex) {
        logger.error("Controller error: {} - {}", ex.getStatusCode(), ex.getReason());
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getReason()));
    }
}