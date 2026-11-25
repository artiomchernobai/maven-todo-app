package com.example.todo;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:todo-tests",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class TodoApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @BeforeEach
    void clearDatabase() {
        jdbc.update("delete from tasks");
        jdbc.update("delete from categories");
    }

    @Test
    void createsEditsCompletesAndDeletesATask() throws Exception {
        long school = createCategory("School");
        long personal = createCategory("Personal");
        MvcResult created = mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":" Draft ", "priority":"LOW", "categoryId":%d}
                                """.formatted(school)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Draft"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andReturn();
        long taskId = idOf(created);
        String taskUrl = "/api/tasks/" + taskId;
        header().string("Location", endsWith(taskUrl)).match(created);

        mvc.perform(put(taskUrl).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Submit", "description":"Upload PDF", "status":"IN_PROGRESS",
                                 "priority":"HIGH", "dueDate":"2026-12-01", "categoryId":%d}
                                """.formatted(personal)))
                .andExpect(status().isOk());
        mvc.perform(get(taskUrl))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Submit"))
                .andExpect(jsonPath("$.description").value("Upload PDF"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.dueDate").value("2026-12-01"))
                .andExpect(jsonPath("$.categoryId").value(personal));

        mvc.perform(put(taskUrl).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Submit PDF", "priority":"HIGH", "categoryId":%d}
                                """.formatted(personal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.dueDate").isEmpty());

        mvc.perform(patch(taskUrl + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
        mvc.perform(get(taskUrl)).andExpect(jsonPath("$.status").value("DONE"));
        mvc.perform(patch(taskUrl + "/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));

        mvc.perform(delete(taskUrl)).andExpect(status().isNoContent());
        mvc.perform(get(taskUrl)).andExpect(status().isNotFound());
    }

    @Test
    void combinesStatusPriorityCategoryAndDateFilters() throws Exception {
        long school = createCategory("School");
        long personal = createCategory("Personal");
        long match = createTask(school, "DONE", "HIGH", "2026-12-01");
        createTask(school, "TODO", "HIGH", "2026-12-01");
        createTask(school, "DONE", "LOW", "2026-12-01");
        createTask(personal, "DONE", "HIGH", "2026-12-01");
        createTask(school, "DONE", "HIGH", "2026-12-02");

        mvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5));
        mvc.perform(get("/api/tasks")
                        .param("status", "DONE")
                        .param("priority", "HIGH")
                        .param("categoryId", Long.toString(school))
                        .param("dueDate", "2026-12-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(match));
    }

    @Test
    void managesCategoriesAndProtectsAssignedTasks() throws Exception {
        long category = createCategory("School");
        String categoryUrl = "/api/categories/" + category;

        mvc.perform(put(categoryUrl).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" Courses "}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Courses"));
        mvc.perform(get("/api/categories")).andExpect(jsonPath("$[0].name").value("Courses"));
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Courses"}
                                """))
                .andExpect(status().isConflict());

        long task = createTask(category, "TODO", "MEDIUM", "2026-12-01");
        mvc.perform(delete(categoryUrl))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Move or delete the tasks in this category first."));
        mvc.perform(get(categoryUrl)).andExpect(status().isOk());
        mvc.perform(get("/api/tasks/" + task)).andExpect(status().isOk());

        mvc.perform(delete("/api/tasks/" + task)).andExpect(status().isNoContent());
        mvc.perform(delete(categoryUrl)).andExpect(status().isNoContent());
        mvc.perform(get(categoryUrl)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsInvalidRequestsAndMissingResources() throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"  ", "categoryId":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.priority").exists())
                .andExpect(jsonPath("$.errors.categoryId").exists());
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Draft", "priority":0, "categoryId":1}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Draft", "priority":"LOW", "categoryId":1}
                                """))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/tasks").param("status", "OPEN")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/tasks").param("dueDate", "tomorrow")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/tasks/1/complete")).andExpect(status().isNotFound());
    }

    @Test
    void storesCategoryColorsAndPreservesThemForNameOnlyUpdates() throws Exception {
        long category = createCategory("School");
        String url = "/api/categories/" + category;
        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").isEmpty());

        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"School", "color":"#1A2b3C"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").value("#1A2b3C"));
        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Courses"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").value("#1A2b3C"));
        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Courses", "color":"#12345"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.color").exists());
        mvc.perform(get(url))
                .andExpect(jsonPath("$.name").value("Courses"))
                .andExpect(jsonPath("$.color").value("#1A2b3C"));
    }

    private long createCategory(String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                        .content(json.createObjectNode().put("name", name).toString()))
                .andExpect(status().isCreated())
                .andReturn();
        long id = idOf(result);
        header().string("Location", endsWith("/api/categories/" + id)).match(result);
        return id;
    }

    private long createTask(long category, String taskStatus, String priority, String dueDate) throws Exception {
        return idOf(mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Task", "status":"%s", "priority":"%s", "dueDate":"%s", "categoryId":%d}
                                """.formatted(taskStatus, priority, dueDate, category)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private long idOf(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray()).get("id").asLong();
    }
}
