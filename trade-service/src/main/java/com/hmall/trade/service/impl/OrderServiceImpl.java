package com.hmall.trade.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.api.client.CartClient;
import com.hmall.api.client.ItemClient;
import com.hmall.api.dto.ItemDTO;
import com.hmall.api.dto.OrderDetailDTO;
import com.hmall.common.exception.BadRequestException;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.UserContext;
import com.hmall.trade.constants.MQConstants;
import com.hmall.trade.domain.dto.CartClearMessageDTO;
import com.hmall.trade.domain.dto.OrderFormDTO;
import com.hmall.trade.domain.po.Order;
import com.hmall.trade.domain.po.OrderDetail;
import com.hmall.trade.mapper.OrderMapper;
import com.hmall.trade.service.IOrderDetailService;
import com.hmall.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    private final ItemClient itemClient;
    private final IOrderDetailService detailService;
    private final CartClient cartClient;
    private final RabbitTemplate rabbitTemplate;


    @Override
    @Transactional
    public Long createOrder(OrderFormDTO orderFormDTO) {
        // 1.订单数据
        Order order = new Order();
        // 1.1.查询商品
        List<OrderDetailDTO> detailDTOS = orderFormDTO.getDetails();
        // 1.2.获取商品id和数量的Map
        Map<Long, Integer> itemNumMap = detailDTOS.stream()
                .collect(Collectors.toMap(OrderDetailDTO::getItemId, OrderDetailDTO::getNum));
        Set<Long> itemIds = itemNumMap.keySet();
        // 1.3.查询商品
        List<ItemDTO> items = itemClient.queryItemByIds(itemIds);
        if (items == null || items.size() < itemIds.size()) {
            throw new BadRequestException("商品不存在");
        }
        // 1.4.基于商品价格、购买数量计算商品总价：totalFee
        int total = 0;
        for (ItemDTO item : items) {
            total += item.getPrice() * itemNumMap.get(item.getId());
        }
        order.setTotalFee(total);
        // 1.5.其它属性
        order.setPaymentType(orderFormDTO.getPaymentType());
        order.setUserId(UserContext.getUser());
        order.setStatus(1);
        // 1.6.将Order写入数据库order表中
        save(order);

        // 2.保存订单详情
        List<OrderDetail> details = buildDetails(order.getId(), items, itemNumMap);
        detailService.saveBatch( details);

        // 3.扣减库存
        try {
            itemClient.deductStock(detailDTOS);
        } catch (Exception e) {
            throw new RuntimeException("库存不足！");
        }

        // TODO 4.清理购物车商品  改为mq
//        cartClient.deleteCartItemByIds(itemIds);
        CartClearMessageDTO msg = new CartClearMessageDTO();
        msg.setUserId(UserContext.getUser());
        msg.setItemIds(itemIds);
        try {
            rabbitTemplate.convertAndSend(
                    "trade.topic",        // 交换机
                    "order.create",       // RoutingKey
                    msg                   // 消息体（会被序列化为 JSON）
            );
        } catch (AmqpException e) {
            log.error("发送消息失败！购物车未清理,订单信息：{}", msg, e);
        }
        // 5.发送延迟消息
        rabbitTemplate.convertAndSend(
                MQConstants.DELAY_EXCHANGE_NAME,
                MQConstants.DELAY_ROUTING_KEY,
                order.getId(),
                message -> {
                    message.getMessageProperties().setDelay(10000);
                    return message;
                });
        return order.getId();

    }

    @Override
    public void markOrderPaySuccess(Long orderId) {
        com.hmall.trade.domain.po.Order order = new com.hmall.trade.domain.po.Order();
        order.setId(orderId);
        order.setStatus(2);
        order.setPayTime(LocalDateTime.now());
        updateById(order);
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId) {
        //TODO 取消订单和增加库存
        com.hmall.trade.domain.po.Order order = new com.hmall.trade.domain.po.Order();
        order.setId(orderId);
        order.setStatus(5);
        updateById(order);
        // 3. 查出订单明细，准备回滚库存
        List<OrderDetail> details = detailService.list(
                new QueryWrapper<OrderDetail>().eq("order_id", orderId)
        );
        if (!details.isEmpty()) {
            // 转成 DTO 调用库存服务
            List<OrderDetailDTO> rollbackList = details.stream()
                    .map(d -> {
                        OrderDetailDTO dto = new OrderDetailDTO();
                        dto.setItemId(d.getItemId());
                        dto.setNum(d.getNum());
                        return dto;
                    })
                    .collect(Collectors.toList());

            // 4. 调用库存服务批量回滚
            try {
                itemClient.rollbackStock(rollbackList);
            } catch (Exception ex) {
                // 库存回滚失败，抛异常让事务回滚或在外层做补偿
                throw new BizIllegalException("回滚库存失败，订单取消未完成", ex);
            }
        }

        // 5. （可选）发送取消通知、日志记录
        log.info("订单 {} 已取消，且已回滚 {} 件商品库存", orderId, details.size());

    }

    private List<OrderDetail> buildDetails(Long orderId, List<ItemDTO> items, Map<Long, Integer> numMap) {
        List<OrderDetail> details = new ArrayList<>(items.size());
        for (ItemDTO item : items) {
            OrderDetail detail = new OrderDetail();
            detail.setName(item.getName());
            detail.setSpec(item.getSpec());
            detail.setPrice(item.getPrice());
            detail.setNum(numMap.get(item.getId()));
            detail.setItemId(item.getId());
            detail.setImage(item.getImage());
            detail.setOrderId(orderId);
            details.add(detail);
        }
        return details;
    }
}