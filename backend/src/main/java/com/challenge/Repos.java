package com.challenge;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
interface UserRepo extends JpaRepository<AppUser, Long> { Optional<AppUser> findByUsername(String u); Optional<AppUser> findByToken(String t); }
interface ChallengeRepo extends JpaRepository<Challenge, Long> { List<Challenge> findByUserIdOrderByIdDesc(Long userId); }
interface DayRepo extends JpaRepository<ProgressHistory, Long> {
  List<ProgressHistory> findByChallengeIdOrderByDayNumber(Long cid);
  Optional<ProgressHistory> findByChallengeIdAndDayNumber(Long cid, int n);
}
interface TaskRepo extends JpaRepository<DailyTask, Long> { List<DailyTask> findByChallengeIdAndDayNumberOrderById(Long cid, int n); }
