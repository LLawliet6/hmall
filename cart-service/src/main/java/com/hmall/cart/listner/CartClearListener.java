package com.hmall.cart.listner;

import com.hmall.cart.domain.dto.CartClearMessageDTO;
import com.hmall.cart.service.ICartService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class CartClearListener {

    private final ICartService cartService;

    @RabbitListener(bindings = @QueueBinding(
            value    = @Queue(name = "cart.clear.queue", durable = "true"),
            exchange = @Exchange(name = "trade.topic", type = ExchangeTypes.TOPIC),
            key      = "order.create"
    ))
    public void handleCartClear(CartClearMessageDTO msg) {
        Long userId = msg.getUserId();
        Collection<Long> itemIds = msg.getItemIds();
        // 直接用 msg.userId，不再调用 UserContext.getUser()
        cartService.removeByUserIdAndItemIds(userId, itemIds);
    }

}
