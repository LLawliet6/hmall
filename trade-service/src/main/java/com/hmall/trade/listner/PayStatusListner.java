package com.hmall.trade.listner;

import com.hmall.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PayStatusListner {
    private final IOrderService orderService;
    @RabbitListener(
            bindings = @QueueBinding(value = @Queue(value = "trade.pay.success.queue", durable = "true"),
                    exchange = @Exchange(value = "pay.direct"), key = "pay.success")
    )
    public void payStatus(Long orderId) {

        orderService.markOrderPaySuccess((orderId));
    }
}
