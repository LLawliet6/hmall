package com.hmall.api.client;


import com.hmall.api.po.Cart;
import feign.Param;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

@FeignClient(name = "cart-service", path = "/carts")
public interface CartClient {

    /** 批量删除：DELETE /carts?ids=1,2,3 **/
    @DeleteMapping
    void deleteCartItemByIds(@RequestParam("ids") Collection<Long> ids);

    /** 单项删除：DELETE /carts/{id} **/
    @DeleteMapping("/{id}")
    void deleteCartItem(@PathVariable("id") Long id);

    /** 更新：PUT /carts **/
    @PutMapping
    void updateCart(@RequestBody Cart cart);


}
