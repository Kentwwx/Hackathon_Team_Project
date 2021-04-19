技术栈：

​	1. Redis作为缓存，目的为了减少数据库的访问次数。

​	2. 使用关系型数据库MySQL。

​	3. RabbitMq作为消息队列来进行接口优化从而减少对数据库的访问。

项目亮点：

项目概述：一个最初的下订单系统，加上五次优化。

最初版本：普通的下订单系统，前端下订单，request发送到唯一的tomcat，然后从mysql取数据，返回response。包括两次md5检验，jsr303参数校验，分布式session，系统通用异常处理。

第一次优化：在最初版本的基础上加上商品页面缓存，热点数据对象缓存，商品详情静态化，订单详情静态化，静态资源优化；从而减少了对数据库的访问次数。

即使做了页面缓存之后，客户端还是需要从服务端下载页面数据。如果做了页面静态化，客户端可以从redis里缓存页面而不是直接渲染页面，从而加快访问速度。

电子书秒杀页面的缓存（目的是为了防止瞬间的用户访问量激增带来的服务器压力过大）：

```java
//取缓存
//在GoodsKey类中的getGoodsDetail()方法里，我们设置了缓存的页面有效期，防止缓存时间过长导致的页面的及时性下降
String html = redisService.get(GoodsKey.getGoodsDetail, "" + goodsId, String.class);
if(!StringUtils.isEmpty(html)) {
   //如果Redis缓存里有当前页面，则直接在缓存中读取
   return html;
}
//手动渲染页面
WebContext ctx = new WebContext(request,response,
                                request.getServletContext(),request.getLocale(), model.asMap());
html = thymeleafViewResolver.getTemplateEngine().process("goods_detail", ctx);
if(!StringUtils.isEmpty(html)) {
   //渲染页面之后，将渲染过的页面保存到Redis缓存中
	 redisService.set(GoodsKey.getGoodsDetail, ""+goodsId, html);
}
return html;
```

对热点数据对象用户"user"的缓存（区别于页面缓存，对象缓存可以等对象自己失效）：

```java
public MiaoshaUser getById(long id) {
   //取Redis缓存
   MiaoshaUser user = redisService.get(MiaoshaUserKey.getById, "" + id, MiaoshaUser.class);
   if(user != null) {
     	//如果user已经在Redis缓存内，直接返回缓存里的user
      return user;
   }
   //读取数据库中的user信息
   user = miaoshaUserDao.getById(id);
   if(user != null) {
      //将user存入Redis缓存内
      redisService.set(MiaoshaUserKey.getById, ""+id, user);
   }
   return user;
}
```

```java
public boolean updatePassword(String token, long id, String formPass) {
   //取user
   MiaoshaUser user = getById(id);
   if(user == null) {
      throw new GlobalException(CodeMsg.MOBILE_NOT_EXIST);
   }
   //更新数据库中user的信息
   MiaoshaUser toBeUpdate = new MiaoshaUser();
   toBeUpdate.setId(id);
   toBeUpdate.setPassword(MD5Util.formPassToDBPass(formPass, user.getSalt()));
   miaoshaUserDao.update(toBeUpdate);
   //一旦user信息改变，缓存中user的信息也需要改变
   redisService.delete(MiaoshaUserKey.getById, ""+id);
   user.setPassword(toBeUpdate.getPassword());
   redisService.set(MiaoshaUserKey.token, token, user);
   //密码修改成功
   return true;
}
```

页面静态化（将页面直接缓存到用户的浏览器上，不需要和服务端进行交互）：

```html
<!--在没有进行页面静态化之前，”商品详情“的页面需要通过服务端跳转-->
<td><a th:href="'/goods/to_detail/'+${goods.id}">详情</a></td>  
```

```html
<!--商品静态化之后，“商品详情”页面直接通过客户端跳转-->
<td><a th:href="'/goods_detail.htm?goodsId='+${goods.id}">详情</a></td>  
```

因为从客户端跳转到“商品详情”页面并将”商品详情“页面做静态化，则页面为HTML和JavaScript混合。详情见src/main/resources/static/goods_detail.htm

第二次优化：使用RabbitMQ，并且是redis预减库存

主要思路：电子书的同步下单转化为异步下单。首先进行秒杀系统的初始化，把可以秒杀的电子书的数量加载到Redis里。当收到秒杀请求的时候，通过Redis预减库存，如果库存不足则直接返回，从而减少对数据库的访问；如果可秒杀的电子书的数量充足，则将用户的秒杀请求放入RabbitMQ的消息队列里面而不是直接访问数据库。然后用户的秒杀订单会请求“出队列”，当客户端收到”出队列“的请求时，客户端会进行轮询，判断是否秒杀成功，服务端会进行生成电子书订单、减少库存等操作。

在代码实现中，运用RabbitMQ中的Direct交换机，使电子书订单进入消息队列。

```java
//MiaoshaMessage类中有用户的名字"user"和下单电子书的ID"goodsID"
MiaoshaMessage mm = new MiaoshaMessage(); 
mm.setUser(user);
mm.setGoodsId(goodsId);
//将该用户的秒杀请求放入消息队列中
sender.sendMiaoshaMessage(mm);
return Result.success(0);//排队中
```

```java
@Autowired
AmqpTemplate amqpTemplate ;

public void sendMiaoshaMessage(MiaoshaMessage mm) {
   //将接受到的bean对象转化为string
   String msg = RedisService.beanToString(mm);
   log.info("send message:"+msg);
   amqpTemplate.convertAndSend(MQConfig.MIAOSHA_QUEUE, msg);
}
```

```java
@RabbitListener(queues=MQConfig.MIAOSHA_QUEUE)
public void receive(String message) {
   log.info("receive message:"+message);
   //将收到的信息转化为bean对象
   MiaoshaMessage mm  = RedisService.stringToBean(message, MiaoshaMessage.class);
   //从bean对象中获取user和可秒杀的电子书id
   MiaoshaUser user = mm.getUser();
   long goodsId = mm.getGoodsId();
   //获取电子书可被秒杀的数量，如果可秒杀的数量不足则直接跳出
   GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
   int stock = goods.getStockCount();
   if(stock <= 0) {
      return;
   }
   //判断是否为重复秒杀，如果为同一用户的重复秒杀，则直接跳出
   MiaoshaOrder order = orderService.getMiaoshaOrderByUserIdGoodsId(user.getId(), goodsId);
   if(order != null) {
      return;
   }
   //如果用户成功秒杀电子书，则进行以下操作：减库存 下订单
   miaoshaService.miaosha(user, goods);
}
```

第三次优化：图形验证码以及恶意防刷（接口限流）

验证码的作用：1. 秒杀时大量用户涌入服务端时，分散用户请求。2. 防止用户恶意刷单。

```java
@RequestMapping(value="/verifyCode", method=RequestMethod.GET)
@ResponseBody
public Result<String> getMiaoshaVerifyCod(HttpServletResponse response,MiaoshaUser user,
                                          @RequestParam("goodsId")long goodsId) {
  //如果user不存在，则出error 
  if(user == null) {
      return Result.error(CodeMsg.SESSION_ERROR);
   }
   try {
      //将验证码写到输出流中
      BufferedImage image = miaoshaService.createVerifyCode(user, goodsId);
      OutputStream out = response.getOutputStream();
      ImageIO.write(image, "JPEG", out);
      out.flush();
      out.close();
      return null;
   }catch(Exception e) {
      e.printStackTrace();
      return Result.error(CodeMsg.MIAOSHA_FAIL);
   }
}
```

生成随机验证码的方法：

```java
public BufferedImage createVerifyCode(MiaoshaUser user, long goodsId) {
   if(user == null || goodsId <=0) {
      return null;
   }
   int width = 80;
   int height = 32;
   //创建验证码图像
   BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
   Graphics g = image.getGraphics();
   //设置背景颜色并填充
   g.setColor(new Color(0xDCDCDC));
   g.fillRect(0, 0, width, height);
   //设置边界
   g.setColor(Color.black);
   g.drawRect(0, 0, width - 1, height - 1);
   //生成随机数
   Random rdm = new Random();
   //在图片上生成50个干扰点
   for (int i = 0; i < 50; i++) {
      int x = rdm.nextInt(width);
      int y = rdm.nextInt(height);
      g.drawOval(x, y, 0, 0);
   }
   //生成随机数学公式作为验证码
   String verifyCode = generateVerifyCode(rdm);
   g.setColor(new Color(0, 100, 0));
   g.setFont(new Font("Candara", Font.BOLD, 24));
   g.drawString(verifyCode, 8, 24);
   g.dispose();
   //计算验证码中的数学公示并把验证码存到Redis缓存中
   int rnd = calc(verifyCode);
   redisService.set(MiaoshaKey.getMiaoshaVerifyCode, user.getId()+","+goodsId, rnd);
   //输出验证码图片 
   return image;
}
```

有了生成随机验证码的方法，则需要防止用户恶意刷单。

```java
public boolean checkVerifyCode(MiaoshaUser user, long goodsId, int verifyCode) {//verifyCode是验证码正确的结果
   if(user == null || goodsId <=0) {
      return false;
   }
   //从Redis中获取验证码的结果
   Integer codeOld = redisService.get(MiaoshaKey.getMiaoshaVerifyCode, user.getId()+","+goodsId, Integer.class);
   //如果redis中没有当前用户输入的验证码或者当前用户验证码输入错误，则返回拒绝用户进行秒杀 
   if(codeOld == null || codeOld - verifyCode != 0 ) {
      return false;
   }
   //如果用户没有恶意刷单，则从redis缓存中删除当前用户验证码结果
   redisService.delete(MiaoshaKey.getMiaoshaVerifyCode, user.getId()+","+goodsId);
   return true;
}
```

接口防刷限流（通过Redis缓存限制一段时间内的访问服务端的用户总数），创建类的时候我们extend一个HandlerInterceptorAdapter的一个拦截器；然后实现里面的preHandle()方法，为了在方法执行之前做拦截。以下为preHandle中部分实现拦截的代码：

```java
//设置某段时间-多少秒
AccessKey ak = AccessKey.withExpire(seconds);
//获取访问服务端用户的总数
Integer count = redisService.get(ak, key, Integer.class);
   if(count  == null) {
   		redisService.set(ak, key, 1);	//通过reedis缓存来实现拦截
   }else if(count < maxCount) {
      redisService.incr(ak, key);
   }else {
      //如果超过这段时间内允许的最大用户访问数量，则报错
      render(response, CodeMsg.ACCESS_LIMIT_REACHED);
      return false;
   }
```

```java
private void render(HttpServletResponse response, CodeMsg cm)throws Exception {
   response.setContentType("application/json;charset=UTF-8");
   //得到输出流
   OutputStream out = response.getOutputStream();
   //转化成json string
   String str = JSON.toJSONString(Result.error(cm));
   out.write(str.getBytes("UTF-8"));
   out.flush();
   out.close();
}
```

第四次优化：tomact 服务端优化，tomcat使用apr连接器，nginx+keepalive，负载均衡

第五次优化：lvs四层负载均衡。





