package com.siemens.internship.repository;

import com.siemens.internship.model.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository // Marking this as a Repo, good habit. Spring Data JPA usually figures it out anyway.
public interface ItemRepository extends JpaRepository<Item, Long> {

    /**
     * Just need the IDs sometimes, faster than grabbing whole Item objects.
     * Useful for the async processing later.
     * @return A list of all item IDs.
     */
    @Query("SELECT i.id FROM Item i") // 'i' is just a common alias for Item in JPQL.
    List<Long> findAllItemIds();
}