package com.Amazon_Kindle.Promotion.vo;

import com.Amazon_Kindle.Promotion.domain.OrderInfo;

public class OrderDetailVo {
	private GoodsVo goods;
	private OrderInfo order;
	public GoodsVo getGoods() {
		return goods;
	}
	public void setGoods(GoodsVo goods) {
		this.goods = goods;
	}
	public OrderInfo getOrder() {
		return order;
	}
	public void setOrder(OrderInfo order) {
		this.order = order;
	}
}
