package com.hmall.cart.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Collection;

@Data
public class CartClearMessageDTO {
    private Long userId;
    private Collection<Long> itemIds;
}
