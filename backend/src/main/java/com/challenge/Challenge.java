package com.challenge;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;
@Entity
public class Challenge {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  @ManyToOne(optional = false) @JsonIgnore public AppUser user;
  public String name;
  public LocalDate startDate;
}
