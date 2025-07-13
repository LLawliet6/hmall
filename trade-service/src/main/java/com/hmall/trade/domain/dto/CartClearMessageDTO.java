package com.hmall.trade.domain.dto;

import lombok.Data;

import java.util.Collection;
@Data
public class CartClearMessageDTO {
    private Long userId;
    private Collection<Long> itemIds;
}
