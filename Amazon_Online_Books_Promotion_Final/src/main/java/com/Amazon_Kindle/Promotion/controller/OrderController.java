package com.Amazon_Kindle.Promotion.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.Amazon_Kindle.Promotion.domain.MiaoshaUser;
import com.Amazon_Kindle.Promotion.domain.OrderInfo;
import com.Amazon_Kindle.Promotion.redis.RedisService;
import com.Amazon_Kindle.Promotion.result.CodeMsg;
import com.Amazon_Kindle.Promotion.result.Result;
import com.Amazon_Kindle.Promotion.service.GoodsService;
import com.Amazon_Kindle.Promotion.service.MiaoshaUserService;
import com.Amazon_Kindle.Promotion.service.OrderService;
import com.Amazon_Kindle.Promotion.vo.GoodsVo;
import com.Amazon_Kindle.Promotion.vo.OrderDetailVo;

@Controller
@RequestMapping("/order")
public class OrderController {

	@Autowired
	MiaoshaUserService userService;
	
	@Autowired
	RedisService redisService;
	
	@Autowired
	OrderService orderService;
	
	@Autowired
	GoodsService goodsService;
	
    @RequestMapping("/detail")
    @ResponseBody
    public Result<OrderDetailVo> info(Model model,MiaoshaUser user,
    		@RequestParam("orderId") long orderId) {
    	if(user == null) {
    		return Result.error(CodeMsg.SESSION_ERROR);
    	}
    	OrderInfo order = orderService.getOrderById(orderId);
    	if(order == null) {
    		return Result.error(CodeMsg.ORDER_NOT_EXIST);
    	}
    	long goodsId = order.getGoodsId();
    	GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
    	OrderDetailVo vo = new OrderDetailVo();
    	vo.setOrder(order);
    	vo.setGoods(goods);
    	return Result.success(vo);
    }
    
}
