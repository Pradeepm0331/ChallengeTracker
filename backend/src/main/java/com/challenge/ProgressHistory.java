package com.challenge;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.*;
@Entity @Table(uniqueConstraints = @UniqueConstraint(columnNames = {"challenge_id", "dayNumber"}))
public class ProgressHistory {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
  @ManyToOne(optional = false) @JsonIgnore public Challenge challenge;
  public int dayNumber;
  public LocalDate date;
  public int planned;
  public int completed;
  public Instant updatedAt = Instant.now();
  public int getPercent() { return planned == 0 ? 0 : completed * 100 / planned; }
  public boolean isComplete() { return planned > 0 && completed == planned; }
}
