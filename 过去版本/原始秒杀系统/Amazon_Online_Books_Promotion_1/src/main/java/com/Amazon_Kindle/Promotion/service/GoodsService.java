package com.Amazon_Kindle.Promotion.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Amazon_Kindle.Promotion.dao.GoodsDao;
import com.Amazon_Kindle.Promotion.domain.MiaoshaGoods;
import com.Amazon_Kindle.Promotion.vo.GoodsVo;

@Service
public class GoodsService {
	
	@Autowired
	GoodsDao goodsDao;
	
	public List<GoodsVo> listGoodsVo(){
		return goodsDao.listGoodsVo();
	}

	public GoodsVo getGoodsVoByGoodsId(long goodsId) {
		return goodsDao.getGoodsVoByGoodsId(goodsId);
	}

	public void reduceStock(GoodsVo goods) {
		MiaoshaGoods g = new MiaoshaGoods();
		g.setGoodsId(goods.getId());
		goodsDao.reduceStock(g);
	}
	
	
	
}
