package com.example.service;

import com.example.domain.Order;
import com.example.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder_returns_saved_order() {
        Order saved = new Order("Widget", 3);
        when(orderRepository.save(any(Order.class))).thenReturn(saved);

        Order result = orderService.createOrder("Widget", 3);

        assertThat(result.getProduct()).isEqualTo("Widget");
        assertThat(result.getQuantity()).isEqualTo(3);
        assertThat(result.getStatus()).isEqualTo("PENDING");
        verify(orderRepository, times(1)).save(any(Order.class));
    }

    @Test
    void confirmOrder_changes_status_to_CONFIRMED() {
        Order order = new Order("Widget", 3);
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
        when(orderRepository.save(order)).thenReturn(order);

        Order result = orderService.confirmOrder(1L);

        assertThat(result.getStatus()).isEqualTo("CONFIRMED");
    }

    @Test
    void confirmOrder_throws_when_order_not_found() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.confirmOrder(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Order not found");
    }

    @Test
    void getPendingOrders_returns_only_pending() {
        when(orderRepository.findByStatus("PENDING")).thenReturn(List.of(new Order("A", 1)));

        List<Order> result = orderService.getPendingOrders();

        assertThat(result).hasSize(1);
    }
}
