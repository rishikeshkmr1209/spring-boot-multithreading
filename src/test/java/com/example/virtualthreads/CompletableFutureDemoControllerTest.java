package com.example.virtualthreads;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CompletableFutureDemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testChainingAndComposition() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/completable-future/chaining?userId=U-99"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("U-99"))
                .andExpect(jsonPath("$.pattern").value("supplyAsync -> thenApply -> thenCompose"))
                .andExpect(jsonPath("$.permissions").isArray());
    }

    @Test
    void testCombineParallel() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/completable-future/combine-parallel"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weatherReport").value("Sunny, 24°C"))
                .andExpect(jsonPath("$.trafficReport").value("Light Traffic (12 min ETA)"));
    }

    @Test
    void testFastestWins() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/completable-future/fastest-wins"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.winningGateway").exists());
    }

    @Test
    void testAllOfAggregation() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/completable-future/all-of-aggregation"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results").isArray());
    }

    @Test
    void testErrorHandlingRecovery() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/completable-future/error-handling?shouldFail=true"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recoveredFromFailure").value(true))
                .andExpect(jsonPath("$.data").exists());
    }
}
