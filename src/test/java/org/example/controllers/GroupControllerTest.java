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
}
