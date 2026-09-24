package com.challenge;
import jakarta.servlet.http.HttpServletRequest;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api")
public class ApiController {
  record Cred(String username, String password) {}
  record NewChallenge(String name, LocalDate startDate) {}
  record NewTask(String title) {}
  record Toggle(boolean completed) {}
    record EditTask(String title) {}

  private final UserRepo users; private final ChallengeRepo challenges; private final DayRepo days; private final TaskRepo tasks;
  private final BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
  ApiController(UserRepo u, ChallengeRepo c, DayRepo d, TaskRepo t) { users = u; challenges = c; days = d; tasks = t; }

  // ---- auth ----
  @PostMapping("/auth/register")
  Map<String, Object> register(@RequestBody Cred c) {
    if (c.username() == null || c.username().isBlank() || c.password() == null || c.password().length() < 6)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a username and a password of at least 6 characters.");
    if (users.findByUsername(c.username()).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is taken.");
    AppUser u = new AppUser(); u.username = c.username().trim(); u.passwordHash = enc.encode(c.password());
    return session(u);
  }
  @PostMapping("/auth/login")
  Map<String, Object> login(@RequestBody Cred c) {
    AppUser u = users.findByUsername(c.username()).filter(x -> enc.matches(c.password(), x.passwordHash))
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong username or password."));
    return session(u);
  }
  private Map<String, Object> session(AppUser u) {
    u.token = UUID.randomUUID().toString(); users.save(u);
    return Map.of("token", u.token, "username", u.username);
  }

  // ---- challenges ----
  @GetMapping("/challenges")
  List<Challenge> list(HttpServletRequest r) { return challenges.findByUserIdOrderByIdDesc(me(r).id); }

  @PostMapping("/challenges")
  Challenge create(@RequestBody NewChallenge n, HttpServletRequest r) {
    Challenge c = new Challenge(); c.user = me(r); c.name = (n.name() == null || n.name().isBlank()) ? "My 100-day challenge" : n.name().trim();
    c.startDate = n.startDate() != null ? n.startDate() : LocalDate.now();
    challenges.save(c);
    List<ProgressHistory> rows = new ArrayList<>();
    for (int i = 1; i <= 100; i++) {           // auto-generate days 1..100
      ProgressHistory h = new ProgressHistory(); h.challenge = c; h.dayNumber = i; h.date = c.startDate.plusDays(i - 1); rows.add(h);
    }
    days.saveAll(rows);
    return c;
  }

  @GetMapping("/challenges/{id}/days")
  List<ProgressHistory> days(@PathVariable Long id, HttpServletRequest r) { own(id, r); return days.findByChallengeIdOrderByDayNumber(id); }

  @GetMapping("/challenges/{id}/days/{n}")
  Map<String, Object> day(@PathVariable Long id, @PathVariable int n, HttpServletRequest r) {
    own(id, r);
    return Map.of("day", history(id, n), "tasks", tasks.findByChallengeIdAndDayNumberOrderById(id, n));
  }

  @PostMapping("/challenges/{id}/days/{n}/tasks")
  DailyTask addTask(@PathVariable Long id, @PathVariable int n, @RequestBody NewTask t, HttpServletRequest r) {
    Challenge c = own(id, r); history(id, n);
    if (t.title() == null || t.title().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task title is required.");
    DailyTask d = new DailyTask(); d.challenge = c; d.dayNumber = n; d.title = t.title().trim();
    tasks.save(d); recompute(id, n); return d;
  }

  @PatchMapping("/tasks/{taskId}")
  DailyTask toggle(@PathVariable Long taskId, @RequestBody Toggle t, HttpServletRequest r) {
    DailyTask d = tasks.findById(taskId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    own(d.challenge.id, r);
    d.completed = t.completed(); d.completedAt = t.completed() ? Instant.now() : null;
    tasks.save(d); recompute(d.challenge.id, d.dayNumber); return d;
  }

  // ---- analytics ----
  @GetMapping("/challenges/{id}/analytics")
  Map<String, Object> analytics(@PathVariable Long id, HttpServletRequest r) {
    own(id, r);
    List<ProgressHistory> list = days.findByChallengeIdOrderByDayNumber(id);
    LocalDate today = LocalDate.now();
    int planned = 0, done = 0, complete = 0, best = 0, run = 0, cur = 0;
    for (ProgressHistory h : list) {
      planned += h.planned; done += h.completed;
      if (h.isComplete()) { complete++; run++; best = Math.max(best, run); } else run = 0;
    }
    for (int i = list.size() - 1; i >= 0; i--) {   // current streak: consecutive complete days up to today
      ProgressHistory h = list.get(i);
      if (h.date.isAfter(today)) continue;
      if (h.isComplete()) cur++; else if (h.date.equals(today)) continue; else break;
    }
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("totalPlanned", planned); m.put("totalCompleted", done);
    m.put("overallPercent", planned == 0 ? 0 : done * 100 / planned);
    m.put("daysCompleted", complete); m.put("currentStreak", cur); m.put("bestStreak", best);
    return m;
  }
  
    @PutMapping("/tasks/{taskId}")
  DailyTask edit(@PathVariable Long taskId, @RequestBody EditTask t, HttpServletRequest r) {
    DailyTask d = ownTask(taskId, r);
    if (t.title() == null || t.title().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task title is required.");
    d.title = t.title().trim();
    return tasks.save(d);
  }

  @DeleteMapping("/tasks/{taskId}")
  Map<String, Boolean> delete(@PathVariable Long taskId, HttpServletRequest r) {
    DailyTask d = ownTask(taskId, r);
    Long cid = d.challenge.id; int n = d.dayNumber;
    tasks.delete(d);
    recompute(cid, n);   // keeps planned/completed counts correct
    return Map.of("deleted", true);
  }

  // ---- helpers ----
  private AppUser me(HttpServletRequest r) { return (AppUser) r.getAttribute("user"); }
  private Challenge own(Long id, HttpServletRequest r) {
    return challenges.findById(id).filter(c -> c.user.id.equals(me(r).id)).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
  }
  private ProgressHistory history(Long cid, int n) {
    return days.findByChallengeIdAndDayNumber(cid, n).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Day must be 1-100."));
  }
  /** Keeps the permanent planned-vs-completed record in sync after any task change. */
  private void recompute(Long cid, int n) {
    List<DailyTask> ts = tasks.findByChallengeIdAndDayNumberOrderById(cid, n);
    ProgressHistory h = history(cid, n);
    h.planned = ts.size(); h.completed = (int) ts.stream().filter(t -> t.completed).count(); h.updatedAt = Instant.now();
    days.save(h);
  }
    private DailyTask ownTask(Long taskId, HttpServletRequest r) {
    DailyTask d = tasks.findById(taskId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    own(d.challenge.id, r);
    return d;
  }
}
