package com.siemens.internship.controller; // Make sure this matches your test package structure

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siemens.internship.model.Item;
// import com.siemens.internship.repository.ItemRepository; // Only if directly used and not fully mocked
import com.siemens.internship.service.ItemService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest // Load the whole Spring app context for these tests.
@AutoConfigureMockMvc // Sets up MockMvc for us to make fake HTTP requests.
@ActiveProfiles("test") // Good idea to use a 'test' profile (e.g., for H2 in-memory db).
class ItemControllerIntegrationTests {

	@Autowired
	private MockMvc mockMvc; // Our tool for making HTTP requests.

	@Autowired
	private ObjectMapper objectMapper; // For turning Java objects into JSON and back.

	// For these controller tests, we'll mock the ItemService.
	// We want to test if the controller handles requests/responses correctly,
	// not the full service logic here (that's for ItemServiceTests).
	@MockBean
	private ItemService itemService;

	// @Autowired // If I needed the real repo for some reason (e.g. H2 setup/teardown)
	// private ItemRepository itemRepository;

	private Item item1, item2;

	@BeforeEach
	void setUp() {
		// Some test data to work with.
		// itemRepository.deleteAll(); // If using a real DB and need clean slate.
		item1 = new Item(1L, "Test Item 1", "Description 1", "NEW", "test1@example.com");
		item2 = new Item(2L, "Test Item 2", "Description 2", "NEW", "test2@example.com");
	}

	@AfterEach
	void tearDown() {
		// itemRepository.deleteAll(); // Clean up if using real DB.
	}

	// Sanity check: did Spring even load? Is MockMvc there?
	@Test
	void contextLoads() {
		assertNotNull(mockMvc);
		assertNotNull(itemService); // Mocked service should be there.
	}

	// Testing the GET all items endpoint.
	@Test
	void getAllItems_shouldReturnListOfItems() throws Exception {
		// Tell our mocked service what to return when findAll() is called.
		when(itemService.findAll()).thenReturn(Arrays.asList(item1, item2));

		mockMvc.perform(get("/api/items"))
				.andExpect(status().isOk()) // Expect 200 OK
				.andExpect(content().contentType(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$", hasSize(2))) // Expect a list of 2 items
				.andExpect(jsonPath("$[0].name", is("Test Item 1")))
				.andExpect(jsonPath("$[1].name", is("Test Item 2")));

		verify(itemService, times(1)).findAll(); // Make sure findAll was called once.
	}

	// Check GET by ID - happy path.
	@Test
	void getItemById_whenItemExists_shouldReturnItem() throws Exception {
		when(itemService.findById(1L)).thenReturn(Optional.of(item1));

		mockMvc.perform(get("/api/items/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id", is(1))) // Check parts of the JSON response.
				.andExpect(jsonPath("$.name", is("Test Item 1")));

		verify(itemService, times(1)).findById(1L);
	}

	// Check GET by ID - when item's not there (expect 404).
	@Test
	void getItemById_whenItemDoesNotExist_shouldReturnNotFound() throws Exception {
		when(itemService.findById(99L)).thenReturn(Optional.empty()); // Simulate item not found.

		mockMvc.perform(get("/api/items/99"))
				.andExpect(status().isNotFound()); // Expect 404.

		verify(itemService, times(1)).findById(99L);
	}

	// Create a new item - should work (201 Created).
	@Test
	void createItem_withValidData_shouldReturnCreatedItem() throws Exception {
		Item newItem = new Item("New Item", "New Desc", "PENDING", "new@example.com");
		Item savedItem = new Item(3L, "New Item", "New Desc", "PENDING", "new@example.com"); // What the service returns.

		when(itemService.save(any(Item.class))).thenReturn(savedItem);

		mockMvc.perform(post("/api/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(newItem))) // Send JSON in request body.
				.andExpect(status().isCreated()) // Expect 201
				.andExpect(jsonPath("$.id", is(3)))
				.andExpect(jsonPath("$.name", is("New Item")))
				.andExpect(header().string("Location", "/api/items/3")); // Check Location header.

		verify(itemService, times(1)).save(any(Item.class));
	}

	// Try to create an item with bad data - expect 400 Bad Request.
	@Test
	void createItem_withInvalidData_shouldReturnBadRequest() throws Exception {
		Item invalidItem = new Item("", "Desc", "PENDING", "not-an-email"); // Blank name, bad email.

		// Don't need to mock itemService.save() here, validation should stop it before that.
		MvcResult result = mockMvc.perform(post("/api/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(invalidItem)))
				.andExpect(status().isBadRequest()) // Expect 400
				.andExpect(jsonPath("$.name", containsString("cannot be blank"))) // Check specific error messages.
				.andExpect(jsonPath("$.email", containsString("valid email address")))
				.andReturn();

		// System.out.println(result.getResponse().getContentAsString()); // Handy for debugging error response format.

		verify(itemService, never()).save(any(Item.class)); // Make sure 'save' was NOT called.
	}

	// Update an existing item.
	@Test
	void updateItem_whenItemExistsAndValidData_shouldReturnOk() throws Exception {
		Item updatedDetails = new Item(1L, "Updated Item 1", "Updated Desc 1", "UPDATED", "updated1@example.com");

		// When findById is called for item 1, return the original item1.
		when(itemService.findById(1L)).thenReturn(Optional.of(item1));
		// When save is called, just return the item that was passed to it (simulates successful update).
		when(itemService.save(any(Item.class))).thenAnswer(invocation -> invocation.getArgument(0));

		mockMvc.perform(put("/api/items/1")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(updatedDetails)))
				.andExpect(status().isOk()) // Expect 200 OK.
				.andExpect(jsonPath("$.id", is(1)))
				.andExpect(jsonPath("$.name", is("Updated Item 1")))
				.andExpect(jsonPath("$.email", is("updated1@example.com")));

		ArgumentCaptor<Item> itemCaptor = ArgumentCaptor.forClass(Item.class); // To capture the item passed to save().
		verify(itemService, times(1)).findById(1L);
		verify(itemService, times(1)).save(itemCaptor.capture());

		// Check that the item passed to save() had the right changes.
		assertEquals("Updated Item 1", itemCaptor.getValue().getName());
		assertEquals(item1.getId(), itemCaptor.getValue().getId()); // ID should not have changed from payload.
	}

	// Try to update an item that doesn't exist (expect 404).
	@Test
	void updateItem_whenItemDoesNotExist_shouldReturnNotFound() throws Exception {
		Item updatedDetails = new Item(99L, "Updated Item", "Desc", "S", "update@example.com");
		when(itemService.findById(99L)).thenReturn(Optional.empty()); // Item not found.

		mockMvc.perform(put("/api/items/99")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(updatedDetails)))
				.andExpect(status().isNotFound());

		verify(itemService, times(1)).findById(99L);
		verify(itemService, never()).save(any(Item.class)); // Save shouldn't be called.
	}

	// Delete an item.
	@Test
	void deleteItem_whenItemExists_shouldReturnNoContent() throws Exception {
		when(itemService.deleteById(1L)).thenReturn(true); // Service says deletion was successful.

		mockMvc.perform(delete("/api/items/1"))
				.andExpect(status().isNoContent()); // Expect 204 No Content.

		verify(itemService, times(1)).deleteById(1L);
	}

	// Try to delete a non-existent item (expect 404).
	@Test
	void deleteItem_whenItemDoesNotExist_shouldReturnNotFound() throws Exception {
		when(itemService.deleteById(99L)).thenReturn(false); // Service says item wasn't found for deletion.

		mockMvc.perform(delete("/api/items/99"))
				.andExpect(status().isNotFound()); // Expect 404.

		verify(itemService, times(1)).deleteById(99L);
	}

	// Test the async process endpoint - just checking it returns 202 and calls the service.
	@Test
	void processItems_shouldReturnAcceptedAndTriggerAsyncProcessing() throws Exception {
		// The service's async method returns a CompletableFuture.
		CompletableFuture<List<Item>> future = CompletableFuture.completedFuture(Arrays.asList(item1, item2));
		when(itemService.processItemsAsync()).thenReturn(future);

		mockMvc.perform(get("/api/items/process"))
				.andExpect(status().isAccepted()) // Expect 202 Accepted.
				.andExpect(jsonPath("$.message", is("Item processing initiated asynchronously.")));

		verify(itemService, times(1)).processItemsAsync();

		// Make sure our mocked future completes, otherwise the test might be flaky
		// or hang if there's a whenComplete callback in the controller.
		future.get(5, TimeUnit.SECONDS);
	}
}