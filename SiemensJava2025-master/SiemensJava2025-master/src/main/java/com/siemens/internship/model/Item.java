package com.siemens.internship.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class Item {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Using IDENTITY, seems more straightforward for simple auto-increment PKs.
    private Long id;

    @NotBlank(message = "Item name cannot be blank.") // Can't have a blank name, obviously.
    @Size(min = 2, max = 100, message = "Item name must be between 2 and 100 characters.")
    private String name;

    @Size(max = 500, message = "Description can be at most 500 characters.")
    private String description; // Description's optional, but can't be crazy long.

    private String status; // Maybe make this an Enum later if the statuses are fixed.

    @NotBlank(message = "Email cannot be blank.")
    @Email(message = "Please provide a valid email address.") // Email needs to look like an email.
    private String email;

    // Handy constructor for when we're making a new item and don't have an ID yet.
    public Item(String name, String description, String status, String email) {
        this.name = name;
        this.description = description;
        this.status = status;
        this.email = email;
    }
}