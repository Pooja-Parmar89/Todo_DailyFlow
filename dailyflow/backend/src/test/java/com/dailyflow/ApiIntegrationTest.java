package com.dailyflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dailyflow.repository.TaskOccurrenceRepository;
import com.dailyflow.repository.TaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end tests of the REST API through the real security filter chain, services, Hibernate and the Flyway
 * schema (H2 in MySQL mode). Each test registers its own users, so tests do not depend on each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiIntegrationTest {

    private static final String PASSWORD = "Passw0rdOK";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper om;
    @Autowired
    private TaskRepository taskRepository;
    @Autowired
    private TaskOccurrenceRepository occurrenceRepository;

    // ------------------------------------------------------------ helpers

    private static String newEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }

    private static String dueIn(int days) {
        return LocalDateTime.now(ZoneOffset.UTC).plusDays(days).withNano(0).toString();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return om.readTree(result.getResponse().getContentAsString());
    }

    private String register(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("displayName", "Test User", "email", email,
                                "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("token").asText();
    }

    private String registerNew() throws Exception {
        return register(newEmail());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode getJson(String url, String token) throws Exception {
        return read(mvc.perform(get(url).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn());
    }

    private Map<String, Object> taskBody(String title, String priority, String dueAt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("priority", priority);
        if (dueAt != null) {
            body.put("dueAt", dueAt);
        }
        return body;
    }

    private long createTask(String token, Map<String, Object> body) throws Exception {
        MvcResult result = mvc.perform(post("/api/tasks").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return read(result).get("id").asLong();
    }

    private long categoryId(String token, String name) throws Exception {
        for (JsonNode c : getJson("/api/categories", token)) {
            if (c.get("name").asText().equals(name)) {
                return c.get("id").asLong();
            }
        }
        throw new AssertionError("Category not found: " + name);
    }

    private JsonNode tasks(String token, String query) throws Exception {
        return getJson("/api/tasks" + query, token);
    }

    /** Uses .param() so characters such as % are encoded exactly once. */
    private JsonNode search(String token, String q) throws Exception {
        return read(mvc.perform(get("/api/tasks").header("Authorization", bearer(token)).param("q", q))
                .andExpect(status().isOk()).andReturn());
    }

    private List<String> titles(JsonNode page) {
        List<String> titles = new ArrayList<>();
        page.get("content").forEach(t -> titles.add(t.get("title").asText()));
        return titles;
    }

    // ----------------------------------------------------- authentication

    @Test
    void requestsWithoutTokenAreRejectedWithJsonError() throws Exception {
        mvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void invalidOrTamperedTokenIsRejected() throws Exception {
        String token = registerNew();

        mvc.perform(get("/api/tasks").header("Authorization", bearer(token + "x")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/tasks").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void registerThenLoginWorksAndPasswordIsNeverReturned() throws Exception {
        String email = newEmail();
        register(email);

        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email.toUpperCase(), "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                .andExpect(jsonPath("$.user.email", is(email)))
                .andReturn();

        assertThat(login.getResponse().getContentAsString()).doesNotContain("password");
        String token = read(login).get("token").asText();
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(email)));
    }

    @Test
    void duplicateEmailIsRejectedWith409() throws Exception {
        String email = newEmail();
        register(email);

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("displayName", "Again", "email", email,
                                "password", PASSWORD))))
                .andExpect(status().isConflict());
    }

    @Test
    void weakPasswordAndInvalidEmailAreRejectedWithFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("displayName", "X", "email", "not-an-email",
                                "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email", notNullValue()))
                .andExpect(jsonPath("$.fieldErrors.password", notNullValue()));
    }

    @Test
    void wrongPasswordAndUnknownEmailGiveTheSameGenericError() throws Exception {
        String email = newEmail();
        register(email);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email, "password", "WrongPass1"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid email or password")));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", newEmail(), "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid email or password")));
    }

    @Test
    void logoutInvalidatesTheToken() throws Exception {
        String email = newEmail();
        String token = register(email);
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token))).andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());

        // signing in again issues a working token
        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(read(login).get("token").asText())))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------- task CRUD

    @Test
    void taskLifecycleCreateEditCompleteReopenDeletePersistsInTheDatabase() throws Exception {
        String token = registerNew();

        Map<String, Object> body = taskBody("Write report", "HIGH", dueIn(2));
        body.put("description", "Quarterly numbers");
        long id = createTask(token, body);
        assertThat(taskRepository.findById(id)).isPresent();

        mvc.perform(get("/api/tasks/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Write report")))
                .andExpect(jsonPath("$.status", is("TODO")))
                .andExpect(jsonPath("$.description", is("Quarterly numbers")))
                .andExpect(jsonPath("$.completedAt").doesNotExist());

        Map<String, Object> edit = taskBody("Write final report", "LOW", dueIn(3));
        edit.put("description", "Quarterly numbers");
        edit.put("status", "IN_PROGRESS");
        mvc.perform(put("/api/tasks/" + id).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(edit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Write final report")))
                .andExpect(jsonPath("$.priority", is("LOW")))
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));

        mvc.perform(post("/api/tasks/" + id + "/complete").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.completedAt", notNullValue()));

        mvc.perform(post("/api/tasks/" + id + "/reopen").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("TODO")))
                .andExpect(jsonPath("$.completedAt").doesNotExist());

        mvc.perform(patch("/api/tasks/" + id + "/status").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("IN_PROGRESS")));

        // history was written: created, updated, completed, reopened, status change
        JsonNode history = getJson("/api/activity?taskId=" + id, token);
        assertThat(history.get("totalElements").asInt()).isGreaterThanOrEqualTo(5);

        mvc.perform(delete("/api/tasks/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        assertThat(taskRepository.findById(id)).isEmpty();
        mvc.perform(get("/api/tasks/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isNotFound());

        // the audit trail of a deleted task is retained
        JsonNode afterDelete = getJson("/api/activity?taskId=" + id, token);
        assertThat(afterDelete.get("content").get(0).get("action").asText()).isEqualTo("TASK_DELETED");
    }

    @Test
    void validationErrorsAreReportedPerField() throws Exception {
        String token = registerNew();

        mvc.perform(post("/api/tasks").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"  \",\"priority\":\"HIGH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title", notNullValue()));

        mvc.perform(post("/api/tasks").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"No priority\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.priority", notNullValue()));

        Map<String, Object> badRange = taskBody("Backwards", "LOW", dueIn(1));
        badRange.put("startAt", dueIn(5));
        mvc.perform(post("/api/tasks").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(badRange)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.dueAt", notNullValue()));

        mvc.perform(post("/api/tasks").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\",\"priority\":\"URGENT\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/tasks").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    // --------------------------------------------------- authorization

    @Test
    void aUserCannotReadChangeOrDeleteAnotherUsersTasks() throws Exception {
        String owner = registerNew();
        String intruder = registerNew();
        long id = createTask(owner, taskBody("Private task", "HIGH", dueIn(1)));
        long ownerCategory = categoryId(owner, "Other");

        mvc.perform(get("/api/tasks/" + id).header("Authorization", bearer(intruder)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/tasks/" + id).header("Authorization", bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(taskBody("Hijacked", "LOW", null))))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/tasks/" + id + "/complete").header("Authorization", bearer(intruder)))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/tasks/" + id + "/status").header("Authorization", bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/tasks/" + id).header("Authorization", bearer(intruder)))
                .andExpect(status().isNotFound());

        assertThat(tasks(intruder, "").get("totalElements").asInt()).isZero();

        // the intruder cannot attach their own task to the owner's category, or touch the category itself
        Map<String, Object> body = taskBody("Mine", "LOW", null);
        body.put("categoryId", ownerCategory);
        mvc.perform(post("/api/tasks").header("Authorization", bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.categoryId", notNullValue()));
        mvc.perform(delete("/api/categories/" + ownerCategory).header("Authorization", bearer(intruder)))
                .andExpect(status().isNotFound());

        // the owner's task is untouched
        mvc.perform(get("/api/tasks/" + id).header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title", is("Private task")))
                .andExpect(jsonPath("$.status", is("TODO")));
        assertThat(taskRepository.findById(id)).isPresent();
    }

    @Test
    void occurrencesOfAnotherUsersRecurringTaskCannotBeChanged() throws Exception {
        String owner = registerNew();
        String intruder = registerNew();
        Map<String, Object> body = taskBody("Daily habit", "LOW", dueIn(0));
        body.put("recurrenceType", "DAILY");
        long id = createTask(owner, body);

        mvc.perform(post("/api/tasks/" + id + "/occurrences/" + today() + "/complete")
                        .header("Authorization", bearer(intruder)))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------ search, filter, sort, page

    @Test
    void searchFilterSortAndPaginationWorkOnTheBackend() throws Exception {
        String token = registerNew();
        long gate = categoryId(token, "GATE 2027");

        Map<String, Object> a = taskBody("Prepare GATE notes", "HIGH", dueIn(1));
        a.put("categoryId", gate);
        a.put("description", "algorithms chapter");
        createTask(token, a);
        long b = createTask(token, taskBody("Java interview mock", "MEDIUM", dueIn(3)));
        createTask(token, taskBody("Buy groceries", "LOW", null));
        createTask(token, taskBody("100% focus session", "HIGH", dueIn(10)));
        mvc.perform(post("/api/tasks/" + b + "/complete").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        assertThat(tasks(token, "").get("totalElements").asInt()).isEqualTo(4);

        // search in title and in description, case-insensitive
        assertThat(titles(tasks(token, "?q=JAVA"))).containsExactly("Java interview mock");
        assertThat(titles(tasks(token, "?q=algorithms"))).containsExactly("Prepare GATE notes");
        // LIKE wildcards typed by the user are literal ("%" alone must not match everything)
        assertThat(titles(search(token, "100%"))).containsExactly("100% focus session");
        assertThat(search(token, "%").get("totalElements").asInt()).isEqualTo(1);

        // category, priority, status
        assertThat(titles(tasks(token, "?categoryId=" + gate))).containsExactly("Prepare GATE notes");
        assertThat(tasks(token, "?priority=HIGH").get("totalElements").asInt()).isEqualTo(2);
        assertThat(tasks(token, "?priority=HIGH,LOW").get("totalElements").asInt()).isEqualTo(3);
        assertThat(titles(tasks(token, "?status=COMPLETED"))).containsExactly("Java interview mock");
        assertThat(tasks(token, "?status=TODO,IN_PROGRESS").get("totalElements").asInt()).isEqualTo(3);

        // due date range (inclusive); tasks without a due date never match a range
        assertThat(titles(tasks(token, "?dueFrom=" + today() + "&dueTo=" + today().plusDays(4)
                + "&sort=dueAt")))
                .containsExactly("Prepare GATE notes", "Java interview mock");

        // sorting: due date puts "no due date" last; priority ascending puts HIGH first
        assertThat(titles(tasks(token, "?sort=dueAt&direction=asc")).get(3)).isEqualTo("Buy groceries");
        assertThat(titles(tasks(token, "?sort=priority&direction=asc")).get(3)).isEqualTo("Buy groceries");
        assertThat(titles(tasks(token, "?sort=title&direction=asc")).get(0)).isEqualTo("100% focus session");

        // pagination
        JsonNode page = tasks(token, "?size=2&page=1&sort=title");
        assertThat(page.get("totalElements").asInt()).isEqualTo(4);
        assertThat(page.get("totalPages").asInt()).isEqualTo(2);
        assertThat(page.get("content")).hasSize(2);

        // unknown sort key is a client error, not a 500
        mvc.perform(get("/api/tasks?sort=password").header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overdueFilterOnlyReturnsIncompleteOneOffTasksThatArePastDue() throws Exception {
        String token = registerNew();
        createTask(token, taskBody("Late", "HIGH", dueIn(-2)));
        long doneLate = createTask(token, taskBody("Late but done", "HIGH", dueIn(-2)));
        createTask(token, taskBody("Future", "HIGH", dueIn(2)));
        mvc.perform(post("/api/tasks/" + doneLate + "/complete").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        JsonNode overdue = tasks(token, "?overdue=true");

        assertThat(titles(overdue)).containsExactly("Late");
        assertThat(overdue.get("content").get(0).get("overdue").asBoolean()).isTrue();
    }

    // ------------------------------------------------------- dashboard

    @Test
    void dashboardStatisticsAreComputedFromPersistedTasks() throws Exception {
        String token = registerNew();

        JsonNode empty = getJson("/api/dashboard", token);
        assertThat(empty.get("total").asInt()).isZero();
        assertThat(empty.get("completionPercentage").asDouble()).isZero();

        createTask(token, taskBody("Overdue one", "HIGH", dueIn(-1)));
        Map<String, Object> inProgress = taskBody("Working on it", "MEDIUM", null);
        inProgress.put("status", "IN_PROGRESS");
        createTask(token, inProgress);
        Map<String, Object> done = taskBody("Finished", "LOW", null);
        done.put("status", "COMPLETED");
        createTask(token, done);
        createTask(token, taskBody("Due tonight", "HIGH", today().atTime(23, 59).toString()));

        JsonNode dash = getJson("/api/dashboard", token);

        assertThat(dash.get("total").asInt()).isEqualTo(4);
        assertThat(dash.get("completed").asInt()).isEqualTo(1);
        assertThat(dash.get("pending").asInt()).isEqualTo(2);
        assertThat(dash.get("inProgress").asInt()).isEqualTo(1);
        assertThat(dash.get("overdue").asInt()).isEqualTo(1);
        assertThat(dash.get("dueToday").asInt()).isEqualTo(1);
        assertThat(dash.get("completionPercentage").asDouble()).isEqualTo(25.0);
        assertThat(dash.get("recentlyCreated")).hasSize(4);
        assertThat(dash.get("weekly")).hasSize(7);
        assertThat(dash.get("weekly").get(6).get("created").asInt()).isEqualTo(4);
        assertThat(dash.get("weekly").get(6).get("completed").asInt()).isEqualTo(1);

        // another user's data never leaks into the numbers
        assertThat(getJson("/api/dashboard", registerNew()).get("total").asInt()).isZero();
    }

    // ------------------------------------------------------ categories

    @Test
    void newAccountsGetTheNineDefaultCategoriesAndCategoryRulesAreEnforced() throws Exception {
        String token = registerNew();
        JsonNode categories = getJson("/api/categories", token);
        assertThat(categories).hasSize(9);

        mvc.perform(post("/api/categories").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"My Goals\",\"color\":\"#112233\",\"icon\":\"star\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("My Goals")));
        mvc.perform(post("/api/categories").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"my goals\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/categories").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Bad color\",\"color\":\"red\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.color", notNullValue()));

        long goals = categoryId(token, "My Goals");
        Map<String, Object> body = taskBody("Use category", "LOW", null);
        body.put("categoryId", goals);
        long taskId = createTask(token, body);

        mvc.perform(delete("/api/categories/" + goals).header("Authorization", bearer(token)))
                .andExpect(status().isConflict());

        mvc.perform(put("/api/categories/" + goals).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Renamed\",\"color\":\"#445566\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Renamed")))
                .andExpect(jsonPath("$.taskCount", is(1)));

        mvc.perform(delete("/api/tasks/" + taskId).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/categories/" + goals).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
    }

    // -------------------------------------------------- recurring tasks

    @Test
    void recurringOccurrencesCanBeCompletedSkippedAndRescheduledWithoutDuplicates() throws Exception {
        String token = registerNew();
        Map<String, Object> body = taskBody("Morning walk", "LOW", today().atTime(7, 0).toString());
        body.put("recurrenceType", "DAILY");
        long id = createTask(token, body);

        LocalDate d0 = today();
        LocalDate d1 = d0.plusDays(1);
        LocalDate d2 = d0.plusDays(2);
        LocalDate d5 = d0.plusDays(5);

        JsonNode week = getJson("/api/calendar?from=" + d0 + "&to=" + d2, token);
        assertThat(week).hasSize(3);
        assertThat(week.get(0).get("recurring").asBoolean()).isTrue();
        assertThat(week.get(0).get("occurrenceStatus").asText()).isEqualTo("PENDING");

        // completing the same occurrence twice (a retried request) leaves exactly one stored row
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/tasks/" + id + "/occurrences/" + d0 + "/complete")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status", is("COMPLETED")));
        }
        assertThat(occurrenceRepository.findForWindow(List.of(id), d0, d0)).hasSize(1);

        mvc.perform(post("/api/tasks/" + id + "/occurrences/" + d1 + "/skip")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/tasks/" + id + "/occurrences/" + d2 + "/reschedule")
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newDate\":\"" + d5 + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("RESCHEDULED")));

        JsonNode calendar = getJson("/api/calendar?from=" + d0 + "&to=" + d5, token);
        List<String> entries = new ArrayList<>();
        calendar.forEach(item -> entries.add(item.get("occurrenceDate").asText() + ":"
                + item.get("occurrenceStatus").asText()));
        // d2 is gone from its original date; the moved occurrence shows up on d5 next to d5's own occurrence
        assertThat(entries).containsExactlyInAnyOrder(
                d0 + ":COMPLETED", d1 + ":SKIPPED",
                d0.plusDays(3) + ":PENDING", d0.plusDays(4) + ":PENDING",
                d5 + ":PENDING", d5 + ":RESCHEDULED");
        calendar.forEach(item -> {
            if ("RESCHEDULED".equals(item.get("occurrenceStatus").asText())) {
                assertThat(item.get("originalDate").asText()).isEqualTo(d2.toString());
            }
        });

        // restoring an occurrence removes the exception
        mvc.perform(delete("/api/tasks/" + id + "/occurrences/" + d0).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/tasks/" + id + "/occurrences/" + d0).header("Authorization", bearer(token)))
                .andExpect(status().isNoContent()); // repeating is harmless
        assertThat(occurrenceRepository.findForWindow(List.of(id), d0, d0)).isEmpty();

        // a date that is not an occurrence of the rule is rejected
        mvc.perform(post("/api/tasks/" + id + "/occurrences/" + d0.minusDays(3) + "/complete")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void occurrenceOperationsRequireARecurringTask() throws Exception {
        String token = registerNew();
        long id = createTask(token, taskBody("One-off", "LOW", dueIn(1)));

        mvc.perform(post("/api/tasks/" + id + "/occurrences/" + today() + "/complete")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void calendarRangeIsLimited() throws Exception {
        String token = registerNew();

        mvc.perform(get("/api/calendar?from=" + today() + "&to=" + today().plusDays(400))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/calendar?from=" + today() + "&to=" + today().minusDays(1))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------- profile

    @Test
    void changingThePasswordInvalidatesOldTokensAndKeepsTheSessionAlive() throws Exception {
        String email = newEmail();
        String token = register(email);

        MvcResult changed = mvc.perform(put("/api/users/me/password").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", "NewPassw0rd"))))
                .andExpect(status().isOk())
                .andReturn();

        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token))).andExpect(status().isUnauthorized());
        String fresh = read(changed).get("token").asText();
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(fresh))).andExpect(status().isOk());

        mvc.perform(put("/api/users/me/password").header("Authorization", bearer(fresh))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(om.writeValueAsString(Map.of("currentPassword", "wrong", "newPassword", "Another1234"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPassword", notNullValue()));
    }

    @Test
    void reminderSummaryListsTasksDueInsideTheLeadWindow() throws Exception {
        String token = registerNew();
        createTask(token, taskBody("Soon", "HIGH", LocalDateTime.now(ZoneOffset.UTC).plusMinutes(20).withNano(0).toString()));
        createTask(token, taskBody("Much later", "HIGH", dueIn(5)));
        createTask(token, taskBody("Already late", "HIGH", dueIn(-1)));

        JsonNode reminders = getJson("/api/reminders", token);

        assertThat(reminders.get("overdueCount").asInt()).isEqualTo(1);
        assertThat(reminders.get("leadMinutes").asInt()).isEqualTo(60);
        List<String> soon = new ArrayList<>();
        reminders.get("dueSoon").forEach(i -> soon.add(i.get("title").asText()));
        assertThat(soon).containsExactly("Soon");
    }
}
