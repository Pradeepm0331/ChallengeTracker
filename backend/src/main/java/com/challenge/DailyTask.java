package com.challenge;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
public class DailyTask {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  @ManyToOne(optional = false) @JsonIgnore public Challenge challenge;
  public int dayNumber;
  public String title;
  public boolean completed;
  public Instant createdAt = Instant.now();
  public Instant completedAt;
}
