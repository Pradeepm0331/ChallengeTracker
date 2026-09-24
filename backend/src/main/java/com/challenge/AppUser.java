package com.challenge;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
@Entity @Table(name = "users")
public class AppUser {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  @Column(unique = true, nullable = false) public String username;
  @JsonIgnore public String passwordHash;
  @JsonIgnore public String token;
}
