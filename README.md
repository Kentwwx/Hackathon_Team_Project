## 技术栈：

1. 前端使用 HTML，CSS，JQuery以及Thymeleaf，Bootstrap技术框架。
2. 后端使用SpringBoot 搭建项目，JSR303 做校验器，MyBatis持久层框架。
3. 中间件使用了消息队列RabbitMQ进行异步下单；使用Redis进行资源缓存以及实现分布式Session；Druid连接池。
4. 使用关系型数据库MySQL。
5. 使用Tomcat服务器集群以及Nginx反向代理和缓存静态资源。
6. 此外还使用了处于网络层第四层的Load Balancer，实现了由Nginx 集群+Tomcat集群组成的Server集群。

## 项目亮点：

**系统架构完整**：使用由Application Load Balancer 集群和Tomcat集群组成的Server集群，并使用由AWS提供的处于OSI第四层网络的Load Balancer。

**中间件**：使用Redis 缓存实现分布式Session，以及页面和热点数据静态化。使用消息队列RabbitMQ削峰填谷。

**用户数据方面**：使用JSR303校验器对用户名进行检验，两次MD5对用户密码进行保护

**高并发场景下保证电子书不超卖**：1.前端加验证码，防止用户同时发出多个请求。2.隐藏秒杀地址，防止用户提前抓取网页。3.在订单表中，对用户id和电子书id加唯一索引，确保一个用户不会生成两个订单。4.在减库存的sql 语句加上对数据库数量的判断。

**其他**：图形验证码以及接口限流防刷等。

## 项目概述：

亚马逊作为全球性企业，有着庞大的用户群。亚马逊在进行秒杀Kindle电子书活动时，势必会面临着超高的并发量。我们为了满足这一需求，从最初的普通的Kindle电子书秒杀系统，进行了6次优化，最终，我们的项目达到了理论上可以支撑百万并发连接级别连接的系统架构。运用先进的技术理念，增加系统的可靠性，提高在高并发场景下的QPS，增加用户体验，实现接近实际场景下的秒杀系统。

## 项目网址：
http://4layerroute-ecd955ee14d070ec.elb.us-east-1.amazonaws.com/

## 版本介绍：

### 最初版本：

我们在最初的版本总体技术上实现了一个可用且完整的亚马逊Kindle 电子书秒杀系统，作为我们项目的整体框架。此版本缺点是在实际秒杀业务场景表现不好，在高并发场景下，QPS较低。后续6个版本都是在此之上进行针对秒杀场景的优化。我们通过Jmeter压力测试，验证我们的优化效果。

我们实现了三个页面，分别是登录页面，亚马逊电子书列表页面，以及秒杀页面。

用户通过在秒杀页面点击秒杀按钮下订单，request发送到Tomcat 服务器，根据我们代码的逻辑，服务器从Mysql取数据，返回页面。

#### 秒杀功能的初步实现

我们的界面主要分为三个，登录界面，亚马逊电子书界面，以及秒杀界面。秒杀界面的秒杀功能是我们项目的核心部分，具体内容我们在控制层的实现类 MiaoshaController 实现。

概况的讲，执行一次秒杀需要完成三部分：

<u>第一，判断库存；</u>

<u>第二，判断是否已经秒杀过了；</u>

<u>第三，减库存 下订单 以及写入秒杀订单。</u>

下面是三个部分的实现

##### 第一部分 判断库存：

我们在业务层实现了一个GoodsService类，里面用依赖注入，注入了Dao层中的goodsDao，调用Dao层的方法我们可以从我们的Mysql数据库获取数据。GoodsService实现了三个方法：



1.列出所有亚马逊电子书        2. 通过Id 获取一个电子书        3. 减少某一个电子书的库存



以下是代码实现：

```java
public class GoodsService {
	
    //注入Dao层
	@Autowired
	GoodsDao goodsDao;
	
    //列出所有亚马逊电子书
	public List<GoodsVo> listGoodsVo(){
		return goodsDao.listGoodsVo();
	}
	//通过Id 获取一个电子书. 返回的GoodsVo是一个电子书的具体信息
	public GoodsVo getGoodsVoByGoodsId(long goodsId) {
		return goodsDao.getGoodsVoByGoodsId(goodsId);
	}
	//减少某一个电子书的库存
	public void reduceStock(GoodsVo goods) {
		MiaoshaGoods g = new MiaoshaGoods();
		g.setGoodsId(goods.getId());
		goodsDao.reduceStock(g);
	}

	
}
```

有了这个goodsService，我们可以通过id调取具体的某一个电子书，然后获取它的库存信息，判断是否有库存

实现如下：

```java
    	//判断库存
    	GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
    	int stock = goods.getStockCount();
    	if(stock <= 0) {
    		model.addAttribute("errmsg", CodeMsg.MIAO_SHA_OVER.getMsg());
    		return "miaosha_fail";
    	}
```



##### 第二部分 判断是否已经秒杀过了：

我们同样在Service层还有一个实现类是orderService，获取数据同样是通过Dao层的方法调取Mysql的数据。orderService 类中有两个方法：



1.是根据用户id和电子书id获取秒杀订单，如果不存在则返回null。

2.是为一个用户和一个电子书创建一个秒杀订单。因为在创建订单过程，不止有一个步骤，但这些步骤需要是如果全部成功就成功，如果一个步骤失败，则所有步骤都需要回到开始之前的状态。因为我们使用的是Mysql数据库，而Mysql支持事务操作，所以我们用到 “@Transactional”这个注释，使得创建订单变成了一个事务操作。



以下是两个方法的具体实现：

```java
@Service
public class OrderService {
	
	@Autowired
	OrderDao orderDao;
    
	//是根据用户id和电子书id获取秒杀订单，如果不存在则返回null。
	public MiaoshaOrder getMiaoshaOrderByUserIdGoodsId(long userId, long goodsId) {
		return orderDao.getMiaoshaOrderByUserIdGoodsId(userId, goodsId);
	}
	
    //事务操作，创建订单，如果成功就全成功，如果一个步骤失败则全部回滚
	@Transactional
	public OrderInfo createOrder(MiaoshaUser user, GoodsVo goods) {
		OrderInfo orderInfo = new OrderInfo();
		orderInfo.setCreateDate(new Date());
		orderInfo.setDeliveryAddrId(0L);
		orderInfo.setGoodsCount(1);
		orderInfo.setGoodsId(goods.getId());
		orderInfo.setGoodsName(goods.getGoodsName());
		orderInfo.setGoodsPrice(goods.getMiaoshaPrice());
		orderInfo.setOrderChannel(1);
		orderInfo.setStatus(0);
		orderInfo.setUserId(user.getId());
		long orderId = orderDao.insert(orderInfo);
		MiaoshaOrder miaoshaOrder = new MiaoshaOrder();
		miaoshaOrder.setGoodsId(goods.getId());
		miaoshaOrder.setOrderId(orderId);
		miaoshaOrder.setUserId(user.getId());
		orderDao.insertMiaoshaOrder(miaoshaOrder);
		return orderInfo;
	}
	
}

```

有了这个orderService 我们可以通过用户id和电子书id判断用户是否已经秒杀过了，避免重复秒杀。具体代码如下：

```java
    	//判断是否已经秒杀到了
    	MiaoshaOrder order = orderService.getMiaoshaOrderByUserIdGoodsId(user.getId(), goodsId);
    	if(order != null) {
    		model.addAttribute("errmsg", CodeMsg.REPEATE_MIAOSHA.getMsg());
    		return "miaosha_fail";
    	}
```



##### 第三部分 减库存，下订单，以及写入秒杀订单：

除了GoodsService，OrderService我们在业务层还实现了MiaoshaService。在这个类中我们只有一个方法，叫做miaosha，其中包括对一个电子书减少库存，并为当前用户和这个电子书创建一个订单。这个方法也是事务性的。



具体实现如下：

```java
@Service
public class MiaoshaService {
	@Autowired
	GoodsService goodsService;
	@Autowired
	OrderService orderService;
    
    @Transactional
	public OrderInfo miaosha(MiaoshaUser user, GoodsVo goods) {
		//减库存 下订单 写入秒杀订单
		goodsService.reduceStock(goods);
		//order_info maiosha_order
		return orderService.createOrder(user, goods);
	}
	
}
```

有了这个方法我们就可以在MiaoshaController中完成最后一步内容：减库存，下订单，以及写入秒杀订单。

具体实现如下：

```java
    	//减库存 下订单 写入秒杀订单 
    	OrderInfo orderInfo = miaoshaService.miaosha(user, goods);
    	model.addAttribute("orderInfo", orderInfo);
    	model.addAttribute("goods", goods);
        return "order_detail";
```

------

### 第一次优化：

在最初版本的基础上加上商品页面缓存，热点数据对象缓存，商品详情静态化，订单详情静态化，静态资源优化；从而减少了对数据库的访问次数。

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

------

### 第二次优化：

使用RabbitMQ，以及Redis预减库存

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

------

### 第三次优化：

图形验证码以及恶意防刷（接口限流）

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

------

### 第四次优化：

#### Tomact 优化参数及配置

为了提高性能和表现，我们选择对tomcat的配置进行一些优化。

##### 1）内存优化

我们在Tomcat bin 目录下的 catalina.sh加了一条语句

```bash
JAVA_OPTS="-server -Xms2048M -Xmx2048M  -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$CATALINA_HOME/logs/heap.dump"
```

这里的 Xms 2048M和 Xmx 2048M是把java 虚拟机的最大内存和最小内存都设置为2G。后面的内容是如果发生OutOfMemeory 则dump出一个内存快照 heap.dump用于对内存进行分析。



##### 2) HTTP Connector 并发优化

除此之外，我们阅读了Tomcat官方的一些文档，查看Tomact是否有其他对于像秒杀这种高并发场景优化的空间。在阅读Tomcat中的 webapp/docs/config 目录下的 The HTTP Connector 文档时
官方文档连接：https://tomcat.apache.org/tomcat-8.5-doc/config/http.html
我们找出来了几个影响性能的参数，并对其初始值进行调整。以下这四个参数我们在 tomact/conf/server.xml 中修改。

1. **maxConnections** 这个配置默认值是10000，代表最高支持一万个连接。
2. **acceptCount** 这个值默认是100，代表在所有线程都被占用的情况下，请求等待队列的长度。
3. **maxThreads** 这个值默认是200，代表最多有200个线程被创建。
4. **minSpareThreads**这个值默认是10，因为Tomact是使用了线程池，minSpareThreads的意思是尽管没有很多请求，也最少保持有10个线程存在。

我们对以上分别都调高了，并在Jmeter压力测试中有实际的表现提升，具体测试结果请看压力测试部分。在调整好以上四个值之后，我们对connector的优化就结束了。



##### 3）其他优化

我们同样是在阅读Tomcat 官方文档 The Host Container 中，发现了其他一些影响性能的参数，并对其进行调整。文档官方链接：https://tomcat.apache.org/tomcat-8.5-doc/config/host.html

1. **autoDeploy** 这个值的意思是Tomact 会周期性的查看 web applications 是否有更新。它的值默认是为true，代表开启状态。如果是在最终部署阶段，我们不需要这个功能，如果默认开启的话会很影响性能，所以我们把它修改为false。
2. **enableLookups **这个值的意思是，如果开启，当调用 request.getRemoteHost（）时先会DNS 查找真实的host。 而如果关闭则会跳过DNS查询，直接返回ip地址。在官方文档中，明确写道如果关闭，“thereby improving performance” 我们使它是保持关闭状态。
3. **reloadable** 这个值的意思是如果开启，Catalina会监控在/WEB-INF/classes下和/WEB-INF/lib下的类，如果侦测到改动，则会自动reload the web application。官方文档紧接描述到这个功能在开发阶段很有用，但是会造成significant runtime overhead。 所以我们最后阶段是使这一功能保持关闭。



**4）使用APR 协议**

Tomcat支持三种请求处理方式，分别是BIO，NIO和APR。BIO是同步阻塞式的，不能处理高并发场景，Nio是基于缓冲区，并能提供非阻塞IO操作，比BIO有着更好的并发性能。而APR使用到Tomcat的Native库，则是一个从操作系统级别进行优化，是异步IO，在高并发场景下，表现优于前者。

在安装apr库后，因为他使用到了tomcat native库，以及一些native methods。jvm 使用到的native 内存会增大，根据我们对jvm的了解，我们认为需要调大Metaspace的空间。和我们对tomcat做的第一项内存优化的方式相同，具体是在catalina.sh 文件，“JAVA_OPTS =” 后面新增 “-XX:MetaspaceSize = 128m" 。

------

### 第五次优化：

第五次优化我们先在本地开发时使用的Nginx 配置，以及对高并发的参数优化，在后面有详细介绍。一台Nginx 反向代理两台Tomcat。总共有两台Nginx。但在部署在AWS时，我们发现AWS 提供了Application layer Load Balancer（ALB），可以代替Nginx的主要功能，所以在实际部署时我们使用了AWS 提供的ALB。

#### Application Load Balancer

ALB是应用层负载均衡器，支持HTTP/HTTPS的协议，也支持基于请求路径的分发。我们在服务器上增加一个新的Tomcat，原有的EC2上又增加了一个新的EC2服务器，也同样配置了两个Tomcat。现在，我们总共有两个服务器和四个Tomcat。然后我们在AWS做启动Application Load Balancer(ALB)前的准备工作 ，设置我们的两个EC2为我们的目标集群，在注册实例时，我们在每个EC2上启动两个实例，分别在8080和8090端口。然后，我们创建对HTTP/HTTPS负载均衡器，并创建两个监听器，分别监听在8080和8090端口。然后再进行安全配置。最后使用Route53 DNS解析服务，将相应的域名CNAME到该ALB的域名。

下图中是我们项目用到的负载均衡器，其中红线标出的是我们使用ALB。每个均衡器配置一个Target Group，每个target group 包括两台EC2 实例。

![image-20210423230951657](https://github.com/Kentwwx/Hackathon_Team_Project/blob/main/Img/ALB.png)

使用应用负载均衡器基于主机名/路径的流量分发特性，客户可以仅用一组应用负载均衡器就可实现将流量路由给多个后端服务，这可以大大简化客户的架构、合理分配服务器处理压力。

我们考虑到，在实际的业务场景中，只使用一台Tomcat服务器是完全不够的，需要有一个中间的代理服务器做负载均衡，当用户请求发送给代理服务器，代理服务器就会按需将请求通过内网转发到某一具体Tomcat 服务器中。我们使用Nginx 做反向代理服务器，因为它具有cpu，内存等资源消耗小，启动快，处理静态资源效率高，高并发能力强，并且运行稳定的优点。官方数据是Nginx最高可以支撑50000并发连接。



#### 配置Nginx

以下是我们如何在本地部署Nginx的步骤。

我们调整了在Nginx 文件中 nginx.conf 配置文件中的一些参数，从而提高性能：

**1.并发相关**

```bash
worker_processes 2; #cpu，如果nginx单独在一台机器上
worker_processes auto;
events {
    worker_connections 25000;#每一个进程打开的最大连接数，包含了nginx与客户端和nginx与upstream之间的连接
    multi_accept on; #可以一次建立多个连接
    use epoll;
}
```

我们调整worker_processes参数，因为我们使用的是双核的处理器，我们就把数字改成2。

还有一个参数是 worker_connections 代表每个工作进程，有多少连接数。因为最高支持50000并发，我们选择把这个数字调整到20480。

而Nginx 当一个工作进程建立一个连接之后，进程将打开一个文件副本，所以最大连接数其实还受到进程最大可打开文件数的限制。

```bash
worker_rlimit_nofile 40960; #每个进程打开的最大的文件数=worker_connections*2是安全的，受限于操作系统/etc/security/limits.conf
```

最大可打开文件数是 “worker_rlimit_nofile” 但这个参数还受限于操作系统，所以我们需要改下 /etc/security/limits.conf 这个文件。

```bash
* hard nofile 40960
* soft nofile 40960
* soft core unlimited
* soft stack 40960
```



**2.配置长连接**

Nginx 默认是与客户端进行长连接，但与服务器Tomcat是不使用长连接的。如果在操作频繁的场景下，Nginx与Tomcat 服务器集群不使用长连接，会需要经常建立连接，断开连接，这样会有很多资源消耗，以及影响性能。

但对于客户端与Nginx的长连接，我们也调整了下参数，如下（写在nginx.conf文件中的http区域内）：

```bash
keepalive_timeout  60s; #长连接的超时时间
keepalive_requests 200; #200个请求之后就关闭连接
keepalive_disable msie6; #ie6禁用
```



对于Nginx 与 upstream server 也就是我们的Tomcat 服务器集群，是默认没有长连接的，所以我们在nginx.conf中的http 区域里面，加上如下配置：

```bash
upstream server_pool{
        server localhost:8080 weight=1 max_fails=2 fail_timeout=30s;
        server localhost:8081 weight=1 max_fails=2 fail_timeout=30s;
        keepalive 200;  #200个长连接
}
```

代表着我们的Tomcat 服务器集群有两台服务器，分别在localhost：8080和localhost：8081。分发给他们请求的权重都是相等的，与他们最多建立两百个长连接。

同时要在server 区域下的location中设置如下：

```bash
location /  {
            proxy_http_version 1.1;
	proxy_set_header Upgrade $http_upgrade;
	proxy_set_header Connection "upgrade";
}
```

因为只有http 1.1 才能使用长连接



**3.开启压缩功能**

Nginx 支持压缩功能gzip，具体是服务端压缩信息，浏览器收到解析并解压。压缩过后的信息比原先要小，有效节约带宽，提高响应至客户端的速度。具体配置如下

```bash
gzip on;
gzip_http_version 1.1;
gzip_disable "MSIE [1-6]\.(?!.*SV1)"; #ie 1-6禁用
gzip_proxied any;#从任何代理过来都启用压缩功能
gzip_types text/plain text/css application/javascript application/x-javascript application/json application/xml application/vnd.ms-fontobject application/x-font-ttf application/svg+xml application/x-icon;#适用类型
gzip_vary on; #Vary: Accept-Encoding
gzip_static on; #如果有压缩好的 直接使用
```



**4.超时时间**

```bash
   #超时时间
    proxy_connect_timeout 5; #连接proxy超时
    proxy_send_timeout 5; # proxy连接nginx超时
    proxy_read_timeout 60;# proxy响应超时
```

**5.access_log**

打开访问日志，并设置cache缓存提高性能

```bash
    access_log  logs/access.log  main;
    #默认写日志：打开文件写入关闭，max:缓存的文件描述符数量，inactive缓存时间，valid：检查时间间隔，min_uses：在inactive时间段内使用了多少次加入缓存
    open_log_file_cache max=200 inactive=20s valid=1m min_uses=2;
```



**6.获取用户ip，转发请求给server_pool**

我们的Tomcat服务器集群是server_pool，需要在location 配置下，让请求转发给集群：

```bash
      location / {
            #长连接
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            #Tomcat获取真实用户ip
            proxy_set_header Host $http_host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $remote_addr;
            proxy_set_header X-Forwarded-Proto  $scheme;
            proxy_pass http://server_pool;#请求发送给server_pool
        }
```

**7.状态监控**

我们配置了Nginx的状态监控，可以在查看Nginx对连接和请求的状态,这样以后调优，去测试多少连接数比较合适，状态监控可以提供给我们直观的信息

```bash
        # 状态监控
        location /nginx_status {
            stub_status on;
            access_log   off;
			allow all;
        }
```



**8.配置缓存**

我们需要在Nginx配置缓存，来储存静态文件，如css或者js，这样静态资源都存放在Nginx就足够了,如果请求需要的信息，在Nginx缓存有，则无需把请求转发到服务器集群，直接返回即可，增加系统的表现。开启缓存的部分需要放在nginx.conf的http部分，而情理缓存，静态文件加缓存放在server区域下：

```bash
     # 开启缓存,2级目录
    proxy_cache_path /usr/local/nginx/proxy_cache levels=1:2 keys_zone=cache_one:200m inactive=1d max_size=20g;
    proxy_ignore_headers X-Accel-Expires Expires Cache-Control;
    proxy_hide_header Cache-Control;
    proxy_hide_header Pragma;
  
  
  #用于清除缓存
  location ~ /purge(/.*)
  {
  allow all;
  proxy_cache_purge cache_one $host$1$is_args$args;
  }
  
    # 静态文件加缓存
    location ~ .*\.(gif|jpg|jpeg|png|bmp|swf|js|css|ico)?$
    {
    expires 1d;
    proxy_cache cache_one;
    proxy_cache_valid 200 304 1d;
    proxy_cache_valid any 1m;
    proxy_cache_key $host$uri$is_args$args;
    proxy_pass http://server_pool;
    }

```

#### 对于操作系统内核的优化

我们使用的是AWS linux，在/etc/sysctl.d/ 文件夹下我们创建了一个文件叫 100-sysctl.conf，根据我们对tcp的理解，进行以下内容的配置：

```bash
net.ipv4.tcp_syncookies=1#防止一个套接字在有过多试图连接到达时引起过载
net.core.somaxconn=1024#默认128，连接队列
net.ipv4.tcp_fin_timeout=10 # timewait的超时时间
net.ipv4.tcp_tw_reuse=1 #os直接使用timewait的连接
net.ipv4.tcp_tw_recycle = 0 #回收禁用
```



------

### 第六次优化：

#### 四层负载均衡

当我们在项目中使用ALB作为反向代理后，理论上我们的整个系统已经可以支撑大约十万的并发连接数。但我们想到，我们需要实现的业务场景是亚马逊电子书的秒杀系统，亚马逊作为一个全球性公司，其用户数量是非常庞大的。我们在阅读亚马逊CEO 贝佐斯写的《致股东的信》当中，贝佐斯提到亚马逊的Prime 会员在全球已经达到两亿人。所以，根据这个数据，我们意识到只运用到Nginx 以及Tomcat 服务集群，应付这个级别的并发还是很有困难的。我们需要使用一种方案，把我们系统的支撑高并发的能力再提高一个层次。

我们想到运用我们熟悉的LVS技术。LVS是位于网络层第四层，所以它的效率很高。

当用户发出请求后，请求先被发送到由LVS产生的虚拟IP，接着LVS会根据负载均衡算法，把请求转发到我们的server集群上，返回的Response会直接发送给用户。

而我们看到在AWS上不需要我们自己配置LVS，AWS本身提供给我们在OSI第四层的 load balancer。所以我们在我们的账户上 创建了一个Network Load Balancer，并让它连接我们的server 集群。在使用第四层负载均衡后，我们有一个唯一的DNS解析地址，背后连接着我们的server集群。

（图片为AWS提供的第四层 负载均衡器）

![image-20210421170719488](https://github.com/Kentwwx/Hackathon_Team_Project/blob/develop/Img/image-20210421170719488.png)

因为预算有限的原因，我们只建立了两个EC2 作为我们的Real Server，每个上面都配置一个Application Load Balancer和两个Tomcat，以及所有的Tomcat 都连接到我们的NLB（Network layer Load Balancer）上面。

![image-20210423233415633](https://github.com/Kentwwx/Hackathon_Team_Project/blob/main/Img/NLB.png)

在这样的系统架构之下，在服务器充足的情况下，是可以支持百万级别的并发连接。

## 压力测试：

我们做了几次压力测试，分别测试了原始版本的吞吐量（QPS） 以及优化完最终版本的吞吐量。为了保证实验最基本的变量唯一的方法，我们都将从同一个电脑，同一个ip地址发出Request进行测试。我们的后端Server始终保持是同样的在AWS上运行的EC2实例。但网络原因无法控制，所以数据不是完全准确的。为了减小网络的影响，我们在完成最终版本的时候，保留了最开始版本的代码，和server 配置文件，然后做测试时是在同一个时间段分别进行测试。这样能够尽量减少我们两次数据的其他因素的影响。

### 初始测试  无优化：

我们将第一次原始版本没有任何优化的代码部署到我们亚马逊云的Tomcat上之后，我们用jmeter做了一组5000个用户轮回10次的压力测试。这次只测试GET方法，测的具体的是/goods/to_list 页面，调取页面货物信息。

![image-20210422183835805](https://github.com/Kentwwx/Hackathon_Team_Project/blob/develop/Img/jmeter%E4%B8%80%E6%AC%A1.png)

下面是Jmeter 压力测试得出来的聚合报告。因为服务器是在AWS上的美国弗吉尼亚站点，而我们发出的request请求是在中国北方，因为网络原因测试数据会有一些error率（我们在localhost进行多次测试，Error率始终为0），以及因为网络原因QPS，也就是图片中的QPS较低，只有485。

![image-20210422183835805](https://github.com/Kentwwx/Hackathon_Team_Project/blob/develop/Img/Jmeter%E4%B8%80%E6%AC%A1%E6%95%B0%E6%8D%AE.png)



### 最终测试 优化完整：

完整优化后，我们为了先是对同样的界面做同样并发量的测试，可以从图上看出，吞吐量（QPS） 是没有优化时的3倍。

![image-20210423234220660](https://github.com/Kentwwx/Hackathon_Team_Project/blob/main/Img/%E6%9C%80%E7%BB%88goodslist.png)

但我们的架构不光是可以提高吞吐量，在支撑更高的并发连接上也是与之前有提高。但这个特点无法从测试看出，只能从官方提供的理论（包括，Tomcat，Redis，Mysql，ALB和NLB）数据作为根据。



除此之外，我们还测试了单个的商品页面，因为我们认为在秒杀开始前，大家已经想好买哪个电子书，然后在这个电子书页面不断刷新，所以我们在最终又单独测试了这个页面。下面是结果，可以看到吞吐量到达了3300以上。

![image-20210423234821541](https://github.com/Kentwwx/Hackathon_Team_Project/blob/main/Img/%E5%8D%95%E4%B8%AA%E5%95%86%E5%93%81%E6%9C%80%E7%BB%88.png)

下面是单个电子书的路径。

![image-20210423235006943](https://github.com/Kentwwx/Hackathon_Team_Project/blob/main/Img/%E5%8D%95%E4%B8%AA%E5%95%86%E5%93%81%E8%B7%AF%E5%BE%84.png)

## 其他细节：

#### 为保护用户数据，使用两次MD5加密

因为数据在网络上是明文传输，如果被劫包，用户的明文密码就会被截取。第一次MD5是用户在输入密码时候之后加上，是为了防止用户密码在网络上明文传输。**方式为：MD5（用户输入+固定salt）**。

第二次MD5是在载入数据库之前加上，为了防止数据库被盗，有人通过反查表对只进行一次MD5的数据进行破解，所以进行两次MD5双重保险。**方式为：MD5（上次的结果+随机salt）**

```java
	public static String md5(String src) {
		return DigestUtils.md5Hex(src);
	}
	//固定的盐
	private static final String salt = "1a2b3c4d";

	//加salt后进行第一次MD5
	public static String inputPassToFormPass(String inputPass) {
		String str = ""+salt.charAt(0)+salt.charAt(2) + inputPass +salt.charAt(5) + salt.charAt(4);
		System.out.println(str);
		return md5(str);
	}

	//载入数据库之前，对数据再加上一个随机的盐，然后进行第二次MD5
	public static String formPassToDBPass(String formPass, String salt) {
		String str = ""+salt.charAt(0)+salt.charAt(2) + formPass +salt.charAt(5) + salt.charAt(4);
		return md5(str);
	}
```

##### **举例：**

用户输入密码为：**123456**

在网络传输的是：**d3b1294a61a07da9b49b6e22b2cbd7f9**

使用随机salt为 "5e6f7g8h" ，则载入数据库的是：**bcb03326aab1575265da58be91b24382**

用户信息得以保护。

------

#### JSR303校验器

我门使用JSR303 对用户输入的数据进行校验，并实现了Exception Handler 给用户提示输入错误等信息

用Jsr303 注释 ”**@NotNull**" 进行输入内容非空的检验。并自定义了一个“**@IsMobile**”的注释进行输入检验。

代码如下：

手机号格式检验：不能为空或者非法手机号格式

```java
/**
 * 自定义一个 @IsMobile 的手机号码检测注释
 * */
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER })
@Retention(RUNTIME)
@Documented
//使用IsMobileValidator 这个类去做验证
@Constraint(validatedBy = {IsMobileValidator.class })
public @interface  IsMobile {
	
	boolean required() default true;
	
	String message() default "手机号码格式错误";

	Class<?>[] groups() default { };

	Class<? extends Payload>[] payload() default { };
}
```

```java
/**
 * 实现必要的ConstraintValidato 接口
 * */
public class IsMobileValidator implements ConstraintValidator<IsMobile, String> {

	private boolean required = false;
	
	public void initialize(IsMobile constraintAnnotation) {
		required = constraintAnnotation.required();
	}

	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(required) {
            //调用validatorUtil 类去对手机号码进行校验
			return ValidatorUtil.isMobile(value);
		}else {
			if(StringUtils.isEmpty(value)) {
				return true;
			}else {
				return ValidatorUtil.isMobile(value);
			}
		}
	}

}
```

```java
/**
 * 用于实现对手机号格式的检测
 * */
public class ValidatorUtil {
	//用regex去判断是否符合手机号码的形式：以1开头的11位数字
	private static final Pattern mobile_pattern = Pattern.compile("1\\d{10}");
	
	public static boolean isMobile(String src) {
        //判断是否为空
		if(StringUtils.isEmpty(src)) {
			return false;
		}
        //判断是否为手机号码的格式
		Matcher m = mobile_pattern.matcher(src);
		return m.matches();
	}
```

#### 异常处理

在实现这个注释校验器之后，检测手机号输入格式不再需要各种判断条件，只需要一个简单的 **@IsMobile** 注释就可以了。但是，当输入值没有办法通过校验,则会返回exceptions。 这种Exceptions阅读不友好，所以我们又定义了一个Exception Handler去解决这个问题。

##### 使用Exception Handler之前：

当用户在登录界面输入错误格式的手机号，不会在界面有任何提示，只会在response中返回以下的错误信息，十分的阅读不友好：

```json
{"timestamp":1618815791860,"status":400,"error":"Bad Request","exception":"org.springframework.validation.BindException","errors":[{"codes":["IsMobile.loginVo.mobile","IsMobile.mobile","IsMobile.java.lang.String","IsMobile"],"arguments":[{"codes":["loginVo.mobile","mobile"],"arguments":null,"defaultMessage":"mobile","code":"mobile"},true],"defaultMessage":"手机号码格式错误","objectName":"loginVo","field":"mobile","rejectedValue":"23333332222","bindingFailure":false,"code":"IsMobile"}],"message":"Validation failed for object='loginVo'. Error count: 1","path":"/login/do_login"}
```



##### 以下为实现Exception Handler之后：

我们在Exception Handler中截取了Exception的信息，并把核心报错内容作为跳窗，提示给用户：

![image-20210419150910505](https://github.com/Kentwwx/Hackathon_Team_Project/blob/develop/Img/image-20210419150910505.png)



##### 以下为Exception Handler的具体实现：

这个类截取了Exception，并返回用我们自己定义的**Result.error** 方法所包装的错误信息，更加简明扼要。

```java
@ControllerAdvice
@ResponseBody
public class GlobalExceptionHandler {
	@ExceptionHandler(value=Exception.class)
	public Result<String> exceptionHandler(HttpServletRequest request, Exception e){
		e.printStackTrace();
        
        //GlobalException是我们自己定义的Exception 类，包含我们自定义的一些错误message，比如密码错误，手机号不存在等
		if(e instanceof GlobalException) {
			GlobalException ex = (GlobalException)e;
			return Result.error(ex.getCm());
            
            //BindException 处理参数校验异常
		}else if(e instanceof BindException) {
			BindException ex = (BindException)e;
			List<ObjectError> errors = ex.getAllErrors();
			ObjectError error = errors.get(0);
			String msg = error.getDefaultMessage();
			return Result.error(CodeMsg.BIND_ERROR.fillArgs(msg));
		}else {
            //其余为服务器异常
			return Result.error(CodeMsg.SERVER_ERROR);
		}
	}
}
```

------

#### 配置Redis

因为我们要把对象储存在Redis当中，而Redis是key value对应的储存方式，所以我们要做对象的序列化与反序列化。对于对象序列化我们选择的是使用fastJson，因为fastjson查看读起来比较友好。代码中的实现：

```java
	//对象的序列化
	private <T> String beanToString(T value) {
		if(value == null) {
			return null;
		}
		Class<?> clazz = value.getClass();
		if(clazz == int.class || clazz == Integer.class) {
			 return ""+value;
		}else if(clazz == String.class) {
			 return (String)value;
		}else if(clazz == long.class || clazz == Long.class) {
			return ""+value;
		}else {
			return JSON.toJSONString(value);
		}
	}
	
	//对象的反序列化
	@SuppressWarnings("unchecked")
	private <T> T stringToBean(String str, Class<T> clazz) {
		if(str == null || str.length() <= 0 || clazz == null) {
			 return null;
		}
		if(clazz == int.class || clazz == Integer.class) {
			 return (T)Integer.valueOf(str);
		}else if(clazz == String.class) {
			 return (T)str;
		}else if(clazz == long.class || clazz == Long.class) {
			return  (T)Long.valueOf(str);
		}else {
			return JSON.toJavaObject(JSON.parseObject(str), clazz);
		}
	}

```

我们通过在一个统一配置文件列出Redis连接池需要的信息，然后再RedisConfig 类中用“@ConfigurationProperties(prefix="redis")” 这个注解，就把配置文件的信息自动导入到Redis连接池中，方便创建。



在配置Redis 连接池以后，就可以往Redis里面储存信息了，但在使用过程中我们发现一个问题，Redis的key很容易起名字就重复了，例如我储存user的一个信息，key是id1，但我又储存亚马逊电子书的一个信息，key也是id1。这样的情况就会把user的信息替换掉。所以我们为了彻底解决这个问题，设计了一套Redis key的前缀，避免key的重复。具体结构如下：

##### 接口：KeyPrefix

```java
public interface KeyPrefix {
		
	public int expireSeconds();
	
	public String getPrefix();
	
}

```

##### 实现KeyPrefix的抽象类：BasePrefix

```java
public abstract class BasePrefix implements KeyPrefix{
	
	private int expireSeconds;
	
	private String prefix;
	
	public BasePrefix(String prefix) {//0代表永不过期
		this(0, prefix);
	}
	
	public BasePrefix( int expireSeconds, String prefix) {
		this.expireSeconds = expireSeconds;
		this.prefix = prefix;
	}
	
	public int expireSeconds() {//默认0代表永不过期
		return expireSeconds;
	}

	public String getPrefix() {
		String className = getClass().getSimpleName();
		return className+":" + prefix;
	}

}

```



以及我们对代表亚马逊电子书的Goods，代表用户的MiaoshaUser，代表订单的Order，分别继承了BasePrefix 然后实现了各自不同的keyPrefix。具体如下：

##### GoodsKey：

```java
public class GoodsKey extends BasePrefix{

	private GoodsKey(int expireSeconds, String prefix) {
		super(expireSeconds, prefix);
	}
	public static GoodsKey getGoodsList = new GoodsKey(60, "gl");
	public static GoodsKey getGoodsDetail = new GoodsKey(60, "gd");
}

```

##### MiaoshaUserkey:

```java
public class MiaoshaUserKey extends BasePrefix{

	public static final int TOKEN_EXPIRE = 3600*24 * 2;
	private MiaoshaUserKey(int expireSeconds, String prefix) {
		super(expireSeconds, prefix);
	}
	public static MiaoshaUserKey token = new MiaoshaUserKey(TOKEN_EXPIRE, "tk");
	public static MiaoshaUserKey getById = new MiaoshaUserKey(0, "id");
}

```

##### OrderKey:

```java
public class OrderKey extends BasePrefix {

	public OrderKey(String prefix) {
		super(prefix);
	}
	public static OrderKey getMiaoshaOrderByUidGid = new OrderKey("moug");
}

```

所以，当我们使用set方法，把信息存入Redis当中，代码实现是这样的：

```java
	public <T> boolean set(KeyPrefix prefix, String key,  T value) {
		 Jedis jedis = null;
		 try {
			 jedis =  jedisPool.getResource();
             //对象序列化
			 String str = beanToString(value);
			 if(str == null || str.length() <= 0) {
				 return false;
			 }
			//生成真正的key
			 String realKey  = prefix.getPrefix() + key;
			 int seconds =  prefix.expireSeconds();
			 if(seconds <= 0) {
				 jedis.set(realKey, str);
			 }else {
				 jedis.setex(realKey, seconds, str);
			 }
			 return true;
		 }finally {
			  returnToPool(jedis);
		 }
	}
```

##### 实际例子：

当我们用Redis set，把一个用户id为1的信息储存到Redis当中

```java
    	MiaoshaUser user  = new MiaoshaUser();
    	user.setId(1L);
    	redisService.set(MiaoshaUserKey.getById, ""+1, user);
```



查看Redis的key，发现实际储存的是 "MiaoshaUserKey:id1"。这样就很好的解决了Redis key 冲突的问题了。

![image-20210419234525403](https://github.com/Kentwwx/Hackathon_Team_Project/blob/develop/Img/image-20210419234525403.png)

------

#### 分布式Session

Session可以记录用户的登录状态，是目前主流网页都会用到的。但如果有多个服务器的时候，某一用户的session只储存在其中一个服务器，但他的请求被转发到了另一服务器，这时他的session就不能被准确获取了。所以我们设计了分布式Session的方法来解决这个问题。具体方式是把用户的session信息都储存在Redis缓存当中，这样每次寻找Session都从共用的这唯一的Redis去查找。

我们首先在User Service类中实现了一个addCookie 方法，代码如下：

```java
	private void addCookie(HttpServletResponse response, String token, MiaoshaUser user) {
        //把token作为key，user的信息作为值存入Redis当中
		redisService.set(MiaoshaUserKey.token, token, user);
        
		Cookie cookie = new Cookie(COOKI_NAME_TOKEN, token);
        //Cookie的expire时间和在redis当中对应的key的expire时间相同
		cookie.setMaxAge(MiaoshaUserKey.token.expireSeconds());
		cookie.setPath("/");
        //把带有token的Cookie加在response当中
		response.addCookie(cookie);
	}

```

addCookie方法在login方法中被调用，随着用户登录，用户拿到带有token的Cookie，下次进入页面时，系统会先从Redis查找有无token，如果有，则返回此用户信息，并刷新Session存在时间。代码如下：

```java
	public MiaoshaUser getByToken(HttpServletResponse response, String token) {
		if(StringUtils.isEmpty(token)) {
			return null;
		}
		MiaoshaUser user = redisService.get(MiaoshaUserKey.token, token, MiaoshaUser.class);
		//延长有效期
		if(user != null) {
			addCookie(response, token, user);
		}
		return user;
	}
```

我们实现了一个Argument Resolver类，并在resolve Argument方法中，做了判断有没有这个用户Session的事情。这样的结构先解析Argument，再传入controller，就可以使不同的页面都有判断Session是否存在的功能，但代码写一遍就够了，就可以使代码更简洁。resolve Argument方法实现如下：

```java
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        //拿到Request和Response
		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
		//Token也许存在parameter里，也许存在cookie里
		String paramToken = request.getParameter(MiaoshaUserService.COOKI_NAME_TOKEN);
		String cookieToken = getCookieValue(request, MiaoshaUserService.COOKI_NAME_TOKEN);
        
		if(StringUtils.isEmpty(cookieToken) && StringUtils.isEmpty(paramToken)) {
			return null;
		}
        //如果有token，则返回这个Session的用户信息
		String token = StringUtils.isEmpty(paramToken)?cookieToken:paramToken;
		return userService.getByToken(response, token);
	}

```

