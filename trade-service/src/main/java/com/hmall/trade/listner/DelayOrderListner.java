package com.hmall.trade.listner;

import com.hmall.api.client.PayClient;
import com.hmall.api.dto.PayOrderDTO;
import com.hmall.trade.constants.MQConstants;
import com.hmall.trade.domain.po.Order;
import com.hmall.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DelayOrderListner {
    private final IOrderService orderService;
    private final PayClient payClient;

    @RabbitListener(
            bindings = @QueueBinding(
                    value = @Queue(value = MQConstants.DELAY_QUEUE_NAME),
                    exchange = @Exchange(value = MQConstants.DELAY_EXCHANGE_NAME,  delayed = "true"),
                    key = MQConstants.DELAY_ROUTING_KEY
            )
    )
    public void delayOrder(Long orderId) {
        // 1. 获取订单信息
        Order order = orderService.getById(orderId);
        // 2. 检测订单状态
        if (order == null || order.getStatus() != 1) {
            return;
        }
        // 3. 未支付，需要查询支付流水
        PayOrderDTO orderInfo = payClient.queryPayOrderByBizOrderNo(orderId);
        //4.判断是否支付
        if (orderInfo != null && orderInfo.getStatus() == 3) {
            //4.1 已支付，标记订单支付成功
            orderService.markOrderPaySuccess(orderId);
        } else {
            //4.2 未支付，取消订单恢复库存
            orderService.cancelOrder(orderId);
        }


    }

}
