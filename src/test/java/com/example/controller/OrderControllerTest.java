package com.example.controller;

import com.example.domain.Order;
import com.example.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Test
    void list_returns_200() throws Exception {
        when(orderService.getAllOrders()).thenReturn(List.of(new Order("Widget", 2)));

        mockMvc.perform(get("/orders"))
                .andExpect(status().isOk());
    }

    @Test
    void create_returns_200() throws Exception {
        when(orderService.createOrder(anyString(), anyInt())).thenReturn(new Order("Widget", 2));

        mockMvc.perform(post("/orders")
                .param("product", "Widget")
                .param("quantity", "2"))
                .andExpect(status().isOk());
    }

    @Test
    void confirm_returns_200() throws Exception {
        Order confirmed = new Order("Widget", 2);
        confirmed.confirm();
        when(orderService.confirmOrder(anyLong())).thenReturn(confirmed);

        mockMvc.perform(put("/orders/1/confirm"))
                .andExpect(status().isOk());
    }
}
