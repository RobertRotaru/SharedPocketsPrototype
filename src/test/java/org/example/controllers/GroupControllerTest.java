package org.example.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class GroupControllerTest {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    private String groupId;

    @BeforeEach
    public void createGroup() throws Exception {
        var body = mvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Trip\",\"description\":\"Summer trip\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        groupId = mapper.readTree(body).get("id").asText();
    }

    private void addMember(String uid) throws Exception {
        mvc.perform(post("/groups/{id}/members", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"" + uid + "\",\"name\":\"" + uid + "\",\"email\":\"" + uid + "@example.com\"}"))
                .andExpect(status().isCreated());
    }

    private void addExpense(String key, String memberId, String amount) throws Exception {
        mvc.perform(post("/groups/{id}/expenses", groupId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"" + memberId + "\",\"amount\":" + amount + "}"))
                .andExpect(status().isCreated());
    }

    @Test
    public void shouldCreateGroup() throws Exception {
        mvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Flat\",\"description\":\"Rent and bills\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Flat"));
    }

    @Test
    public void shouldAddMember() throws Exception {
        addMember("ana");
    }

    @Test
    public void shouldAddExpenseAndReturnBalances() throws Exception {
        addMember("ana");
        addMember("bob");
        addMember("cris");
        addExpense("expense-1", "ana", "100.00");

        var body = mvc.perform(get("/groups/{id}/balances", groupId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var balances = mapper.readTree(body);
        assertEquals(0, balances.get("ana").decimalValue().compareTo(new BigDecimal("66.66")));
        assertEquals(0, balances.get("bob").decimalValue().compareTo(new BigDecimal("-33.33")));
        assertEquals(0, balances.get("cris").decimalValue().compareTo(new BigDecimal("-33.33")));
    }

    @Test
    public void shouldSettleUp() throws Exception {
        addMember("ana");
        addMember("bob");
        addExpense("expense-1", "ana", "100.00");

        mvc.perform(post("/groups/{id}/settlements", groupId)
                        .header("Idempotency-Key", "settle-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payerId\":\"bob\",\"receiverId\":\"ana\",\"amount\":50.00}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/groups/{id}/balances", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ana").value(0.0))
                .andExpect(jsonPath("$.bob").value(0.0));
    }

    // ---- Payment history (US1) ----

    private void settle(String key, String payer, String receiver, String amount) throws Exception {
        mvc.perform(post("/groups/{id}/settlements", groupId)
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payerId\":\"" + payer + "\",\"receiverId\":\"" + receiver + "\",\"amount\":" + amount + "}"))
                .andExpect(status().isCreated());
    }

    private com.fasterxml.jackson.databind.JsonNode history(String page) throws Exception {
        var url = "/groups/{id}/transactions" + (page == null ? "" : "?page=" + page);
        var body = mvc.perform(get(url, groupId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(body);
    }

    @Test
    public void shouldReturnEmptyHistoryForNewGroup() throws Exception {
        mvc.perform(get("/groups/{id}/transactions", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    public void shouldReturnHistoryNewestFirstWithOnlyPublicFields() throws Exception {
        addMember("ana");
        addMember("bob");
        addExpense("secret-key-1", "ana", "100.00");
        settle("secret-key-2", "bob", "ana", "50.00");

        var raw = mvc.perform(get("/groups/{id}/transactions", groupId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var items = mapper.readTree(raw).get("items");

        assertEquals(2, items.size());
        assertEquals("bob", items.get(0).get("payerId").asText());
        assertEquals("Settlement", items.get(0).get("description").asText());
        assertEquals("ana", items.get(1).get("payerId").asText());
        assertEquals("Expense", items.get(1).get("description").asText());
        for (var item : items) {
            var fields = new java.util.TreeSet<String>();
            item.fieldNames().forEachRemaining(fields::add);
            assertEquals(java.util.Set.of("description", "payerId", "recordedAt"), fields);
        }
        assertFalse(raw.contains("secret-key"));
        assertFalse(raw.toLowerCase().contains("idempotency"));
        assertFalse(raw.toLowerCase().contains("\"id\""));
    }

    @Test
    public void shouldReturnRecordedAtAsIsoInstantNewestFirst() throws Exception {
        addMember("ana");
        addExpense("k1", "ana", "10.00");
        addExpense("k2", "ana", "20.00");

        var items = history(null).get("items");
        var newest = java.time.Instant.parse(items.get(0).get("recordedAt").asText());
        var oldest = java.time.Instant.parse(items.get(1).get("recordedAt").asText());
        assertFalse(newest.isBefore(oldest));
    }

    @Test
    public void shouldPageTwentyFiveExpensesAsTenTenFive() throws Exception {
        addMember("ana");
        for (int i = 1; i <= 25; i++) {
            addExpense("k" + i, "ana", "1.00");
        }

        var p1 = history("1");
        var p2 = history("2");
        var p3 = history("3");
        assertEquals(10, p1.get("items").size());
        assertEquals(10, p2.get("items").size());
        assertEquals(5, p3.get("items").size());
        assertTrue(p1.get("hasNext").asBoolean());
        assertTrue(p2.get("hasNext").asBoolean());
        assertFalse(p3.get("hasNext").asBoolean());
        assertEquals(3, p3.get("page").asInt());
        assertEquals(history(null), p1);
    }

    @Test
    public void shouldReturnEmptyItemsForPageBeyondTheEnd() throws Exception {
        addMember("ana");
        addExpense("k1", "ana", "1.00");

        mvc.perform(get("/groups/{id}/transactions?page=99", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    public void shouldRejectInvalidPageWithErrorBody() throws Exception {
        for (var bad : new String[]{"0", "-1", "abc", "1.5", "99999999999"}) {
            mvc.perform(get("/groups/{id}/transactions?page=" + bad, groupId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").isNotEmpty());
        }
    }

    @Test
    public void shouldTreatEmptyPageValueAsFirstPage() throws Exception {
        mvc.perform(get("/groups/{id}/transactions?page=", groupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    public void shouldIgnoreClientSuppliedPageSize() throws Exception {
        addMember("ana");
        for (int i = 1; i <= 15; i++) {
            addExpense("k" + i, "ana", "1.00");
        }

        var page = mapper.readTree(mvc.perform(get("/groups/{id}/transactions?size=50&pageSize=50&limit=50", groupId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertEquals(10, page.get("pageSize").asInt());
        assertEquals(10, page.get("items").size());
        assertTrue(page.get("hasNext").asBoolean());
    }

    @Test
    public void shouldPreferNotFoundOverInvalidPageForUnknownGroup() throws Exception {
        mvc.perform(get("/groups/{id}/transactions?page=0", "does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void shouldReturnNotFoundForUnknownGroupHistory() throws Exception {
        mvc.perform(get("/groups/{id}/transactions", "does-not-exist"))
                .andExpect(status().isNotFound());
    }

    // ---- Descriptions (US2) ----

    private org.springframework.test.web.servlet.ResultActions postExpense(String key, String body) throws Exception {
        return mvc.perform(post("/groups/{id}/expenses", groupId)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions postSettlement(String key, String body) throws Exception {
        return mvc.perform(post("/groups/{id}/settlements", groupId)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    public void shouldShowExpenseAndSettlementDescriptionsInHistory() throws Exception {
        addMember("ana");
        addMember("bob");

        postExpense("e1", "{\"memberId\":\"ana\",\"amount\":100.00,\"description\":\"Dinner\"}")
                .andExpect(status().isCreated());
        postSettlement("s1", "{\"payerId\":\"bob\",\"receiverId\":\"ana\",\"amount\":10.00,\"description\":\"Paying back dinner\"}")
                .andExpect(status().isCreated());

        var items = history(null).get("items");
        assertEquals("Paying back dinner", items.get(0).get("description").asText());
        assertEquals("Dinner", items.get(1).get("description").asText());
    }

    @Test
    public void shouldRejectInvalidDescriptionsWithoutChangingState() throws Exception {
        addMember("ana");
        addMember("bob");
        var tooLong = "x".repeat(201);

        for (var bad : new String[]{"\"\"", "\"   \"", "\"" + tooLong + "\""}) {
            postExpense("bad-e", "{\"memberId\":\"ana\",\"amount\":100.00,\"description\":" + bad + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").isNotEmpty());
            postSettlement("bad-s", "{\"payerId\":\"bob\",\"receiverId\":\"ana\",\"amount\":10.00,\"description\":" + bad + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").isNotEmpty());
        }

        assertEquals(0, history(null).get("items").size());
        mvc.perform(get("/groups/{id}/balances", groupId))
                .andExpect(jsonPath("$.ana").value(0.0))
                .andExpect(jsonPath("$.bob").value(0.0));
        // the rejected keys were not consumed
        postExpense("bad-e", "{\"memberId\":\"ana\",\"amount\":100.00,\"description\":\"Fixed\"}")
                .andExpect(status().isCreated());
    }

    @Test
    public void shouldReturnConflictWhenKeyIsReusedWithDifferentDescription() throws Exception {
        addMember("ana");
        postExpense("k", "{\"memberId\":\"ana\",\"amount\":10.00,\"description\":\"Dinner\"}")
                .andExpect(status().isCreated());

        postExpense("k", "{\"memberId\":\"ana\",\"amount\":10.00,\"description\":\"Lunch\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").isNotEmpty());

        assertEquals(1, history(null).get("items").size());
    }

    @Test
    public void shouldTreatExplicitNullDescriptionAsOmitted() throws Exception {
        addMember("ana");

        postExpense("k", "{\"memberId\":\"ana\",\"amount\":10.00,\"description\":null}")
                .andExpect(status().isCreated());

        assertEquals("Expense", history(null).get("items").get(0).get("description").asText());
    }

    // ---- Backward compatibility (US3) ----

    @Test
    public void shouldAcceptPreFeatureRequestBodiesExactlyAsBefore() throws Exception {
        addMember("ana");
        addMember("bob");
        addMember("cris");

        postExpense("old-e", "{\"memberId\":\"ana\",\"amount\":100.00}")
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));
        postSettlement("old-s", "{\"payerId\":\"bob\",\"receiverId\":\"ana\",\"amount\":33.33}")
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));

        var balances = mapper.readTree(mvc.perform(get("/groups/{id}/balances", groupId))
                .andReturn().getResponse().getContentAsString());
        assertEquals(0, balances.get("ana").decimalValue().compareTo(new BigDecimal("33.33")));
        assertEquals(0, balances.get("bob").decimalValue().compareTo(BigDecimal.ZERO));
        assertEquals(0, balances.get("cris").decimalValue().compareTo(new BigDecimal("-33.33")));
        var items = history(null).get("items");
        assertEquals("Settlement", items.get(0).get("description").asText());
        assertEquals("Expense", items.get(1).get("description").asText());
    }

    @Test
    public void shouldKeepReplayAndConflictBehaviorOfTheReadmeDemo() throws Exception {
        addMember("ana");
        addMember("bob");
        postExpense("dinner-1", "{\"memberId\":\"ana\",\"amount\":100.00}").andExpect(status().isCreated());

        postExpense("dinner-1", "{\"memberId\":\"ana\",\"amount\":100.00}").andExpect(status().isCreated());
        assertEquals(1, history(null).get("items").size());

        postExpense("dinner-1", "{\"memberId\":\"ana\",\"amount\":999.00}").andExpect(status().isConflict());
        assertEquals(1, history(null).get("items").size());
    }

    @Test
    public void shouldKeepExistingErrorStatuses() throws Exception {
        addMember("ana");

        postExpense("k1", "{\"memberId\":\"ana\",\"amount\":0}").andExpect(status().isBadRequest());
        postExpense("k2", "{\"memberId\":\"ana\",\"amount\":-5}").andExpect(status().isBadRequest());
        postExpense("k3", "{\"memberId\":\"ana\",\"amount\":1.234}").andExpect(status().isBadRequest());
        postExpense("k4", "{\"memberId\":\"ghost\",\"amount\":5}").andExpect(status().isBadRequest());
        postExpense("k5", "{\"memberId\":\"ana\"}").andExpect(status().isBadRequest());
        mvc.perform(post("/groups/{id}/expenses", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"ana\",\"amount\":5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/groups/{id}/members", groupId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"uid\":\"ana\",\"name\":\"ana\",\"email\":\"ana@example.com\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/groups/{id}/expenses", "nope")
                        .header("Idempotency-Key", "x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":\"ana\",\"amount\":5}"))
                .andExpect(status().isNotFound());
        assertEquals(0, history(null).get("items").size());
    }

    @Test
    public void shouldKeepExistingRequestRecordConstructorsCompiling() {
        var expense = new org.example.controllers.dtos.ExpenseRequest("ana", new BigDecimal("1.00"));
        var settlement = new org.example.controllers.dtos.SettlementRequest("bob", "ana", new BigDecimal("1.00"));

        assertEquals(null, expense.description());
        assertEquals(null, settlement.description());
    }

    @Test
    public void shouldNotMixHistoryAcrossGroups() throws Exception {
        addMember("ana");
        addExpense("k1", "ana", "10.00");
        var body = mvc.perform(post("/groups")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Other\",\"description\":\"Other group\"}"))
                .andReturn().getResponse().getContentAsString();
        var otherId = mapper.readTree(body).get("id").asText();

        mvc.perform(get("/groups/{id}/transactions", otherId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
        assertEquals(1, history(null).get("items").size());
    }
}
