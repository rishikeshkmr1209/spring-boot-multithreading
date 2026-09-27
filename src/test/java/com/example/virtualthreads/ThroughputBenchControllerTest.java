package com.example.virtualthreads;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ThroughputBenchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testThreadInfoEndpoint() throws Exception {
        mockMvc.perform(get("/api/benchmark/thread-info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threadName").exists())
                .andExpect(jsonPath("$.isVirtual").exists());
    }

    @Test
    void testIoBoundEndpoint() throws Exception {
        mockMvc.perform(get("/api/benchmark/io-bound?delayMs=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Simulated I/O completed"));
    }

    @Test
    void testBatchSimulation() throws Exception {
        mockMvc.perform(post("/api/benchmark/batch-simulate?tasks=50&delayMs=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasksRequested").value(50))
                .andExpect(jsonPath("$.successfulTasks").value(50));
    }

    @Test
    void testMixedFlowEndpoint() throws Exception {
        mockMvc.perform(post("/api/benchmark/mixed-flow?flowId=TEST-MIXED&cpuIterations=1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flowId").value("TEST-MIXED"))
                .andExpect(jsonPath("$.stages[0].workloadType").value("I/O"))
                .andExpect(jsonPath("$.stages[0].isVirtualThread").value(true))
                .andExpect(jsonPath("$.stages[1].workloadType").value("CPU"))
                .andExpect(jsonPath("$.stages[1].isVirtualThread").value(false))
                .andExpect(jsonPath("$.stages[2].workloadType").value("I/O"))
                .andExpect(jsonPath("$.stages[2].isVirtualThread").value(true));
    }

    @Test
    void testOrderProcessingEndpoint() throws Exception {
        String jsonPayload = """
                {
                    "orderId": "ORD-12345",
                    "customerId": "CUST-99",
                    "amount": 249.99,
                    "itemIds": ["ITEM-1", "ITEM-2"]
                }
                """;

        mockMvc.perform(post("/api/orders/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORD-12345"))
                .andExpect(jsonPath("$.paymentStatus").value("PAYMENT_APPROVED"))
                .andExpect(jsonPath("$.inventoryStatus").value("IN_STOCK"))
                .andExpect(jsonPath("$.fraudCheckStatus").value("FRAUD_CHECK_PASSED"))
                .andExpect(jsonPath("$.notificationStatus").value("NOTIFICATION_SENT"));
    }
}
