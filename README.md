# 2021 Spring Amazon Hackathon Championship Team. 
### Team Leader: Weixiang Wang. Members: Ziqi Zhang, Kun Zhou.


## Technical stack：

1. The front end uses HTML, CSS, jQuery, thymeleaf and bootstrap technical frameworks.
2. The backend uses springboot to build the project, jsr303 as the verifier, and mybatis persistence layer framework.
3. The middleware uses message queue rabbitmq for asynchronous ordering; Redis is used for resource caching and distributed session; Druid connection pool.
4. Use relational database mysql.
5. Use Tomcat server cluster and application layer load balancer provided by AWS.
6. In addition, the load balancer in the fourth layer of the network layer is used to realize the server cluster composed of nginx cluster and Tomcat cluster.

## Project highlights：

**Complete system architecture**: serve cluster, and use the load balancer of OSI layer 4 network and the load balancer of application layer provided by AWS.


**Middleware**: redis cache is used to realize distributed session and static page and hotspot data. Use message queue rabbitmq to cut peak and fill valley.


**In terms of user data**: use jsr303 verifier to verify the user name, and MD5 twice to protect the user password.


**Ensure that e-books are not oversold in high concurrency scenarios**: 1. Add a verification code at the front end to prevent users from sending multiple requests at the same time. 2. Hide the sale promotion address to prevent users from grabbing the web page in advance. 3. In the order table, add a unique index to the user ID and e-book ID to ensure that one user will not generate two orders. 4. Add the judgment of database quantity to the SQL statement of inventory reduction.


**Others**: Graphic verification code and interface current limiting and anti brushing, etc.

## Project outline：

As a global enterprise, Amazon has a huge user base. Amazon is bound to face extremely high concurrency when it carries out the sale promotion Kindle e-book activity. In order to meet this demand, we optimized the original ordinary Kindle e-book sale promotion system six times. Finally, our project reached a system architecture that can theoretically support millions of concurrent connection level connections. Use advanced technical concepts to increase the reliability of the system, improve QPS in high concurrency scenarios, increase user experience, and realize the sale promotion system close to the actual scenario.


## Version Description.

### Initial version.

We implemented a usable and complete Amazon Kindle e-book sale promotion system in the initial version overall technically as the overall framework of our project. The drawback of this version is that it does not perform well in actual promotion business scenarios and has a low QPS in high concurrency scenarios. The subsequent six versions are optimized on top of this for the sale promotion scenario. We verified the effect of our optimization by Jmeter stress test.

We implemented three pages, namely the login page, the Amazon eBooks list page, and the sale promotion page.

The user places an order by clicking the promotion button on the sale promotion page, the request is sent to the Tomcat server, and according to the logic of our code, the server fetches data from Mysql and returns the page.

#### Initial implementation of the sale promotion functionality

Our interface is divided into three main interfaces, the login interface, the Amazon eBook interface, and the sale promotion interface. The sale promotion function of the sale promotion interface is the core part of our project, which is implemented in the control layer of the MiaoshaController class.

In summary, there are three parts to performing a promotion.

<u>First, determine the inventory;</u> and

<u>Second, determine if the promotion has already been made;</u

<u>Third, decreasing the inventory, placing the order, and writing the promotion order. </u

Here is the implementation of the three parts


```java
public class GoodsService {
	
    //Dao layer injection
	@Autowired
	GoodsDao goodsDao;
	
    //list all amazon books
	public List<GoodsVo> listGoodsVo(){
		return goodsDao.listGoodsVo();
	}
	//get a specific information of a book
	public GoodsVo getGoodsVoByGoodsId(long goodsId) {
		return goodsDao.getGoodsVoByGoodsId(goodsId);
	}
	//reduce the stock ofa book
	public void reduceStock(GoodsVo goods) {
		MiaoshaGoods g = new MiaoshaGoods();
		g.setGoodsId(goods.getId());
		goodsDao.reduceStock(g);
	}

	
}
```

With this goodsService, we can retrieve a specific e-book by its id, and then get its inventory information to determine whether it is in stock or not

The implementation is as follows.

```java
    
    	GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
    	int stock = goods.getStockCount();
    	if(stock <= 0) {
    		model.addAttribute("errmsg", CodeMsg.MIAO_SHA_OVER.getMsg());
    		return "miaosha_fail";
    	}
```



##### Part II Determining whether a second has been.

We also have an implementation class in the Service layer is orderService, get the data is also through the Dao layer methods to call Mysql data. orderService class has two methods.



1. is based on the user id and e-book id to get the second order, if not exist then return null.

2. is to create a second order for a user and an e-book. Because in the process of creating an order, there is more than one step, but these steps need to be successful if all of them succeed, and if one step fails, then all steps need to go back to the state before they started. Because we are using a Mysql database, and Mysql supports transactional operations, we use the "@Transactional" annotation to make creating an order a transactional operation.



The following is the implementation of the two methods.

```java
@Service
public class OrderService {
	
	@Autowired
	OrderDao orderDao;
    
	public MiaoshaOrder getMiaoshaOrderByUserIdGoodsId(long userId, long goodsId) {
		return orderDao.getMiaoshaOrderByUserIdGoodsId(userId, goodsId);
	}
	
    //Transactional operation, create order, if successful then all successful, if a step fails then all rolled back
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

With this orderService we can determine whether the user has already killed by user id and e-book id, to avoid repeating the second. The specific code is as follows.

```java
    	MiaoshaOrder order = orderService.getMiaoshaOrderByUserIdGoodsId(user.getId(), goodsId);
    	if(order != null) {
    		model.addAttribute("errmsg", CodeMsg.REPEATE_MIAOSHA.getMsg());
    		return "miaosha_fail";
    	}
```



##### Part 3 Reducing Inventory, Placing Orders, and Writing Seconds Orders.

In addition to GoodsService, OrderService we also implement MiaoshaService in the business layer. in this class we have only one method, called miaosha, which consists of reducing inventory for an ebook and creating an order for the current user and this ebook. This method is also transactional.


The implementation is as follows.

```java
@Service
public class MiaoshaService {
	@Autowired
	GoodsService goodsService;
	@Autowired
	OrderService orderService;
    
    @Transactional
	public OrderInfo miaosha(MiaoshaUser user, GoodsVo goods) {
		goodsService.reduceStock(goods);
		//order_info maiosha_order
		return orderService.createOrder(user, goods);
	}
	
}
```

With this method we can complete the last step in the MiaoshaController: reduce the inventory, place the order, and write the second order.

The implementation is as follows.
```java
    	OrderInfo orderInfo = miaoshaService.miaosha(user, goods);
    	model.addAttribute("orderInfo", orderInfo);
    	model.addAttribute("goods", goods);
        return "order_detail";
```

------

### First optimization.

Add product page caching, hot data object caching, static product details, static order details, static resource optimization to the initial version; thus reducing the number of accesses to the database.

Even after page caching, the client still needs to download the page data from the server. If the page is made static, the client can cache the page from redis instead of rendering the page directly, thus speeding up the access speed.

Caching of e-book sale promotion pages (the purpose is to prevent excessive pressure on the server caused by an instantaneous surge in user access).

```java
//In the getGoodsDetail() method of the GoodsKey class, we set the validity period of the cached pages to prevent the timely delivery of the pages due to the long cache time.
String html = redisService.get(GoodsKey.getGoodsDetail, "" + goodsId, String.class);
if(!StringUtils.isEmpty(html)) {
   return html;
}
WebContext ctx = new WebContext(request,response,
                                request.getServletContext(),request.getLocale(), model.asMap());
html = thymeleafViewResolver.getTemplateEngine().process("goods_detail", ctx);
if(!StringUtils.isEmpty(html)) {
	 redisService.set(GoodsKey.getGoodsDetail, ""+goodsId, html);
}
return html;
```
Caching of the hot data object "user" (as opposed to page caching, where the object cache can wait for the object to expire on its own):

```java
public MiaoshaUser getById(long id) {
   MiaoshaUser user = redisService.get(MiaoshaUserKey.getById, "" + id, MiaoshaUser.class);
   if(user != null) {
      return user;
   }
   user = miaoshaUserDao.getById(id);
   if(user != null) {
      redisService.set(MiaoshaUserKey.getById, ""+id, user);
   }
   return user;
}
```

```java
public boolean updatePassword(String token, long id, String formPass) {
   MiaoshaUser user = getById(id);
   if(user == null) {
      throw new GlobalException(CodeMsg.MOBILE_NOT_EXIST);
   }
   MiaoshaUser toBeUpdate = new MiaoshaUser();
   toBeUpdate.setId(id);
   toBeUpdate.setPassword(MD5Util.formPassToDBPass(formPass, user.getSalt()));
   miaoshaUserDao.update(toBeUpdate);
   //Once the user information changes, the user information in the cache also needs to change
   redisService.delete(MiaoshaUserKey.getById, ""+id);
   user.setPassword(toBeUpdate.getPassword());
   redisService.set(MiaoshaUserKey.token, token, user);
   return true;
}
```
Page statics (caching of pages directly to the user browser, without interaction with the server).

```html
<! -- Before the page is static, the "product details" page needs to be redirected via the server -->
<td><a th:href="'/goods/to_detail/'+${goods.id}">detail</a></td>  
```

```html
<! --After product staticization, the "product details" page jumps directly through the client -->
<td><a th:href="'/goods_detail.htm?goodsId='+${goods.id}">detail</a></td>  
```

Because we jump to the "product details" page from the client and make the "product details" page static, the page is a mix of HTML and JavaScript. For details, see src/main/resources/static/goods_detail.htm

------

### Second optimization.

Using RabbitMQ, and Redis pre-reduced inventory

Main idea: synchronous order placement for e-books is converted to asynchronous order placement. First initialize the sale promotion system and load the number of promoted eBooks into Redis. When a promotion request is received, the inventory is pre-reduced by Redis and returned directly if the inventory is insufficient, thus reducing access to the database; if the number of promoted eBooks is sufficient, the user's request is put into the RabbitMQ message queue instead of directly accessing the database. Then the user's promotion order will request "out of queue", and when the client receives the request of "out of queue", the client will poll to determine whether the order is successful, and the server will generate ebook orders and reduce inventory.

In the code implementation, the Direct switch in RabbitMQ is used to make the e-book order enter the message queue.

```java
MiaoshaMessage mm = new MiaoshaMessage(); 
mm.setUser(user);
mm.setGoodsId(goodsId);
sender.sendMiaoshaMessage(mm);
return Result.success(0);//in the queue list
```

```java
@Autowired
AmqpTemplate amqpTemplate ;

public void sendMiaoshaMessage(MiaoshaMessage mm) {
   String msg = RedisService.beanToString(mm);
   log.info("send message:"+msg);
   amqpTemplate.convertAndSend(MQConfig.MIAOSHA_QUEUE, msg);
}
```

```java
@RabbitListener(queues=MQConfig.MIAOSHA_QUEUE)
public void receive(String message) {
   log.info("receive message:"+message);
   MiaoshaMessage mm  = RedisService.stringToBean(message, MiaoshaMessage.class);
   MiaoshaUser user = mm.getUser();
   long goodsId = mm.getGoodsId();
   GoodsVo goods = goodsService.getGoodsVoByGoodsId(goodsId);
   int stock = goods.getStockCount();
   if(stock <= 0) {
      return;
   }
   //Determine whether it is a repeat order, if it is a repeat order for the same user, it will directly jump out
   MiaoshaOrder order = orderService.getMiaoshaOrderByUserIdGoodsId(user.getId(), goodsId);
   if(order != null) {
      return;
   }
   miaoshaService.miaosha(user, goods);
}
```

------

### Third optimization.

Graphical verification code and malicious brush prevention (interface flow restriction)

The purpose of the verification code: 1. to disperse user requests when a large number of users flock to the server during a promotion. 2. to prevent users from malicious swiping.

```java
@RequestMapping(value="/verifyCode", method=RequestMethod.GET)
@ResponseBody
public Result<String> getMiaoshaVerifyCod(HttpServletResponse response,MiaoshaUser user,
                                          @RequestParam("goodsId")long goodsId) {
  if(user == null) {
      return Result.error(CodeMsg.SESSION_ERROR);
   }
   try {
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

Method for generating random CAPTCHA.

```java
public BufferedImage createVerifyCode(MiaoshaUser user, long goodsId) {
   if(user == null || goodsId <=0) {
      return null;
   }
   int width = 80;
   int height = 32;
   BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
   Graphics g = image.getGraphics();
   g.setColor(new Color(0xDCDCDC));
   g.fillRect(0, 0, width, height);
   g.setColor(Color.black);
   g.drawRect(0, 0, width - 1, height - 1);
   Random rdm = new Random();
   //Generate 50 interference points on the image
   for (int i = 0; i < 50; i++) {
      int x = rdm.nextInt(width);
      int y = rdm.nextInt(height);
      g.drawOval(x, y, 0, 0);
   }
   //generate random math question as validation code
   String verifyCode = generateVerifyCode(rdm);
   g.setColor(new Color(0, 100, 0));
   g.setFont(new Font("Candara", Font.BOLD, 24));
   g.drawString(verifyCode, 8, 24);
   g.dispose();
   //store the answer to Redis
   int rnd = calc(verifyCode);
   redisService.set(MiaoshaKey.getMiaoshaVerifyCode, user.getId()+","+goodsId, rnd);
   return image;
}
```

With the method of generating random CAPTCHA, it is necessary to prevent users from malicious swiping.

```java
public boolean checkVerifyCode(MiaoshaUser user, long goodsId, int verifyCode) {//verifyCode是验证码正确的结果
   if(user == null || goodsId <=0) {
      return false;
   }
   //get the result from Redis
   Integer codeOld = redisService.get(MiaoshaKey.getMiaoshaVerifyCode, user.getId()+","+goodsId, Integer.class);
   if(codeOld == null || codeOld - verifyCode != 0 ) {
      return false;
   }
   redisService.delete(MiaoshaKey.getMiaoshaVerifyCode, user.getId()+","+goodsId);
   return true;
}
```

The interface is an anti-flush limit (restricting the total number of users accessing the server over a period of time through the Redis cache), and when creating the class we extend a HandlerInterceptorAdapter interceptor; then implement the preHandle() method inside, in order to do the interception before the method is executed. The following is part of the code in preHandle to achieve interception.

```java
AccessKey ak = AccessKey.withExpire(seconds);
//Get the total number of users accessing the server
Integer count = redisService.get(ak, key, Integer.class);
   if(count  == null) {
   		redisService.set(ak, key, 1);	
   }else if(count < maxCount) {
      redisService.incr(ak, key);
   }else {
      render(response, CodeMsg.ACCESS_LIMIT_REACHED);
      return false;
   }
```

```java
private void render(HttpServletResponse response, CodeMsg cm)throws Exception {
   response.setContentType("application/json;charset=UTF-8");
   OutputStream out = response.getOutputStream();
   String str = JSON.toJSONString(Result.error(cm));
   out.write(str.getBytes("UTF-8"));
   out.flush();
   out.close();
}
```

------

### Fourth optimization.

#### Tomact optimization parameters and configuration

In order to improve performance and performance, we chose to make some optimizations to tomcat's configuration.

##### 1) Memory optimization

We added a statement to catalina.sh in the Tomcat bin directory

```bash
JAVA_OPTS="-server -Xms2048M -Xmx2048M -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$CATALINA_HOME/logs/heap.dump"
```

Here Xms 2048M and Xmx 2048M are setting the maximum and minimum memory of the java virtual machine to 2G. what follows is if OutOfMemeory occurs then dump out a snapshot of memory heap.dump is used to analyze the memory.


##### 2) HTTP Connector Concurrency Optimization

In addition, we read some of the official Tomcat documentation to see if Tomact had any other room for optimization for high concurrency scenarios like promotion. While reading The HTTP Connector documentation in the webapp/docs/config directory of Tomcat
Official documentation link: https://tomcat.apache.org/tomcat-8.5-doc/config/http.html
we identified a few parameters that affect performance and tweaked their initial values. The following four parameters we modified in tomact/conf/server.xml.

1. **maxConnections** The default value for this configuration is 10000, which means that the maximum number of connections supported is 10,000. 2.
2. **acceptCount** This value defaults to 100, representing the length of the request waiting queue if all threads are occupied.
3. **maxThreads** This value defaults to 200, representing the maximum number of 200 threads to be created.
4. **minSpareThreads** This value defaults to 10 because Tomact uses a thread pool. minSpareThreads means that there are at least 10 threads in existence even though there are not many requests.

We have adjusted the above values upwards and have actually improved the performance in the Jmeter stress test, see the stress test section for specific results. After adjusting the above four values, our optimization of the connector is finished.



##### 3) Other optimizations

We also read the official Tomcat documentation The Host Container and found some other parameters that affect performance and tweaked them. The official link to the documentation: https://tomcat.apache.org/tomcat-8.5-doc/config/host.html

1. **autoDeploy** This value means that Tomact will periodically check if web applications have been updated. The default value is true, which means that it is on. If we don't need this feature in the final deployment phase, it will affect the performance if it is enabled by default, so we change it to false.
2. **enableLookups ** This value means that if enabled, when request.getRemoteHost() is called, it will first DNS lookup the real host. If disabled, it will skip the DNS lookup and return the ip address directly. In the official documentation, it is explicitly written that if it is turned off, "thereby improving performance" we make it stay off.
3. **reloadable** This value means that if turned on, Catalina will monitor the classes under /WEB-INF/classes and /WEB-INF/lib and automatically reload the web application if it detects changes. The official documentation immediately describes that this feature is useful during the development phase, but can cause significant runtime overhead. So our final phase is to keep this feature off.



**4) Using the APR protocol**

Tomcat supports three types of request processing, BIO, NIO and APR. BIO is synchronous blocking and cannot handle high concurrency scenarios, Nio is buffer based and provides non-blocking IO operations, which has better concurrency performance than BIO. APR, on the other hand, uses Tomcat's Native library, which is an optimization from the OS level and is asynchronous IO, and performs better than the former in highly concurrent scenarios.

After installing the apr library, because it uses the tomcat native library and some native methods, the native memory used by jvm increases, and based on our knowledge of jvm, we think we need to increase the Metaspace space. The same way we did the first memory optimization for tomcat, we added "-XX:MetaspaceSize = 128m" to the catalina.sh file after "JAVA_OPTS =".

------

### Fifth optimization.

The fifth optimization of the Nginx configuration we used for local development first, and the optimization of parameters for high concurrency, is described in detail later. There are two Nginxes in total, but when deployed on AWS, we found that AWS provides an Application layer Load Balancer (ALB) that can replace the main functionality of Nginx, so we used the AWS-provided ALB for the actual deployment.

#### Application Load Balancer

ALB is an application layer load balancer that supports HTTP/HTTPS protocols and also supports request path-based distribution. We add a new Tomcat to the server and a new EC2 server to the existing EC2, and also configure two Tomcat's. Now we have a total of two servers and four Tomcat's. Then we do the preparation work before starting the Application Load Balancer (ALB) in AWS, setting up our When registering instances, we start two instances on each EC2, on ports 8080 and 8090. Then, we create pairs of HTTP/HTTPS load balancers and create two listeners that listen on ports 8080 and 8090 respectively. Then the security configuration is performed. Finally, using Route53 DNS resolution service, the corresponding domain name CNAME to the domain name of this ALB.

The following figure shows the load balancers used in our project, where the red lines are marked by the ALBs we use. each balancer is configured with a Target Group, and each target group includes two EC2 instances.

Each target group contains two EC2 instances. [image-20210423230951657](https://github.com/Kentwwx/Hackathon_Team_Project/blob/main/Img/ALB.png)

Using the hostname/path-based traffic distribution feature of application load balancers, customers can use only one set of application load balancers to route traffic to multiple back-end services, which can greatly simplify the customer's architecture and distribute the server processing pressure rationally.

We use Nginx for local deployments and ALB for actual project deployments, and here's how we configured Nginx for local testing

#### Configuring Nginx

Here are the steps on how we deployed Nginx locally.

We adjusted some parameters in the nginx.conf configuration file in our Nginx files to improve performance: **1.

**1. Concurrency-related**

```bash
worker_processes 2; #cpu, if nginx is on a separate machine
worker_processes auto;
events {
    worker_connections 25000; #max number of open connections per process, including connections between nginx and client and nginx and upstream
    multi_accept on; # multiple connections can be established at once
    use epoll;
}
```

We adjust the worker_processes parameter, since we are using a dual-core processor, we change the number to 2.

Another parameter is worker_connections which represents how many connections each worker process has. Since we support up to 50,000 concurrent connections, we chose to adjust this number to 20480.

In Nginx, when a worker establishes a connection, the process will open a copy of the file, so the maximum number of connections is actually limited by the maximum number of files the process can open.

```bash
worker_rlimit_nofile 40960; #max_files_per-process=worker_connections*2 is safe and limited by the operating system /etc/security/limits.conf
```

The maximum number of open files is "worker_rlimit_nofile" but this parameter is also limited by the OS, so we need to change the /etc/security/limits.conf file.

```bash
* hard nofile 40960
* soft nofile 40960
* soft core unlimited
* soft stack 40960
```



**2. Configuring Long Connections**

Nginx makes long connections to clients by default, but does not use long connections to the server Tomcat. If Nginx does not use long connections with the Tomcat server cluster in a frequent operation scenario, you will need to establish and disconnect connections frequently, which will consume a lot of resources and affect performance.

However, for long connections between clients and Nginx, we have also adjusted the following parameters (written in the http area of the nginx.conf file).

```bash
keepalive_timeout 60s; #Timeout for long connections
keepalive_requests 200; #Close connection after 200 requests
keepalive_disable msie6; #ie6 disabled
```



For Nginx and the upstream server, which is our Tomcat server cluster, there are no long connections by default, so we add the following configuration to the http section of nginx.conf.

```bash
upstream server_pool{
        server localhost:8080 weight=1 max_fails=2 fail_timeout=30s;
        server localhost:8081 weight=1 max_fails=2 fail_timeout=30s;
        keepalive 200; #200 long connections
}
```

This means that our Tomcat server cluster has two servers at localhost:8080 and localhost:8081. the weight distributed to their requests are equal and a maximum of two hundred long connections are established with them.

Also to be set in the location under the server area as follows.

```bash
location / {
            proxy_http_version 1.1;
	proxy_set_header Upgrade $http_upgrade;
	proxy_set_header Connection "upgrade";
}
```

Because only http 1.1 can use long connections



**3. Enabling compression**

Nginx supports gzip, which is a server-side compression of information that the browser receives to parse and decompress. The compressed information is smaller than the original, effectively saving bandwidth and increasing the speed of response to the client. The specific configuration is as follows

```bash
gzip on;
gzip_http_version 1.1;
gzip_disable "MSIE [1-6]\. (?!. *SV1)"; #ie 1-6 disabled
gzip_proxied any;# enable compression from any proxy
gzip_types text/plain text/css application/javascript application/x-javascript application/json application/xml application/vnd.ms- fontobject application/x-font-ttf application/svg+xml application/x-icon;#Applicable type
gzip_vary on; #Vary: Accept-Encoding
gzip_static on; #If there is a compressed one, use it directly
```



**4. Timeout time**

```bash
   #Timeout time
    proxy_connect_timeout 5; # connect proxy timeout
    proxy_send_timeout 5; # proxy connect nginx timeout
    proxy_read_timeout 60; # proxy response timeout
```

**5.access_log**

Turn on access logging and set cache caching to improve performance



```bash
    access_log logs/access.log main;
    # default write logs: open file write off, max:number of file descriptors cached, inactive cache time, valid: check interval, min_uses: how many times the cache was used in the inactive time period to join
    open_log_file_cache max=200 inactive=20s valid=1m min_uses=2;
```



**6. Get user ip and forward request to server_pool**

Our Tomcat server cluster is server_pool, and we need to configure the location to forward requests to the cluster: ``bash

```bash
      location / {
            #long_connections
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            #Tomcat get real user ip
            proxy_set_header Host $http_host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $remote_addr;
            proxy_set_header X-Forwarded-Proto $scheme;
            proxy_pass http://server_pool;# request sent to server_pool
        }
```

**7. Status monitoring**

We have configured Nginx's state monitoring so that you can see the state of Nginx's connections and requests, so that you can later tune it to test how many connections are appropriate, and the state monitoring can provide us with intuitive information

```bash
        # Status monitoring
        location /nginx_status {
            stub_status on;
            access_log off;
			allow all;
        }
```



**8. Configuring caching**

We need to configure caching in Nginx to store static files, such as css or js, so that static resources are stored in Nginx is sufficient, if the request requires information that is available in the Nginx cache, then there is no need to forward the request to the server cluster, it can be returned directly, increasing the performance of the system. The part that turns on caching needs to be placed in the http section of nginx.conf, while the contextual cache, static files plus cache, is placed under the server area: ``bash

```bash
     # Enable cache, level 2 directory
    proxy_cache_path /usr/local/nginx/proxy_cache levels=1:2 keys_zone=cache_one:200m inactive=1d max_size=20g;
    proxy_ignore_headers X-Accel-Expires Expires Cache-Control;
    proxy_hide_header Cache-Control;
    proxy_hide_header Pragma;
  
  
  # for purge cache
  location ~ /purge(/. *)
  {
  allow all;
  proxy_cache_purge cache_one $host$1$is_args$args;
  }
  
    # Static file caching
    location ~ . *\. (gif|jpg|jpeg|png|bmp|swf|js|css|ico)? $
    {
    expires 1d;
    proxy_cache cache_one;
    proxy_cache_valid 200 304 1d;
    proxy_cache_valid any 1m;
    proxy_cache_key $host$uri$is_args$args;
    proxy_pass http://server_pool;
    }

```

#### Optimization for OS kernel

We are using AWS linux, and under the /etc/sysctl.d/ folder we created a file called 100-sysctl.conf and configured the following according to our understanding of tcp

```bash
net.ipv4.tcp_syncookies=1#Prevent a socket from overloading if too many attempted connections arrive
net.core.somaxconn=1024#default 128, connection queue
net.ipv4.tcp_fin_timeout=10 # timeout for timewait
net.ipv4.tcp_tw_reuse=1 #os direct connections using timewait
net.ipv4.tcp_tw_recycle = 0 # recycle disabled
```



------

### Sixth optimization.

#### Four-tier load balancing

When we used ALB as a reverse proxy in our project, in theory our whole system could already support about 100,000 concurrent connections. However, it occurred to us that the business scenario we needed to implement was a promotion system for Amazon eBooks, and Amazon, as a global company, has a very large number of users. We were reading the "Letter to Shareholders" written by Amazon CEO Bezos, and Bezos mentioned that Amazon's Prime members have reached 200 million people worldwide. So, based on this data, we realized that it would be difficult to handle this level of concurrency using only Nginx and Tomcat service clusters. We needed to use a solution that would increase our system's ability to support high concurrency to another level.

We came up with the familiar LVS technology, which is located at layer 4 of the network layer and is therefore very efficient.

When a user makes a request, the request is first sent to a virtual IP generated by LVS, and then LVS forwards the request to our server cluster according to a load balancing algorithm, and the returned Response is sent directly to the user.

So we created a Network Load Balancer on our account and let it connect to our server cluster. After using Layer 4 load balancing, we have a unique DNS resolution address behind which our server cluster is connected.

(Image is the Layer 4 Load Balancer provided by AWS)

! [image-20210421170719488](https://github.com/Kentwwx/Hackathon_Team_Project/blob/develop/Img/image-20210421170719488.png)

Because of budget constraints, we only set up two EC2s as our Real Servers, each with an Application Load Balancer and two Tomcat, and all Tomcat connected to our NLB (Network layer Load Balancer).

! [image-20210423233415633](https://github.com/Kentwwx/Hackathon_Team_Project/blob/main/Img/NLB.png)

With this system architecture, it is possible to support millions of concurrent connections with sufficient servers.

## Stress testing.

We have done several stress tests, testing the throughput (QPS) of the original version and the throughput of the final version after optimization. In order to ensure that the most basic variables of the experiment are unique, we will all send out Requests from the same computer with the same IP address for testing. Our backend Server is always kept as the same EC2 instance running on AWS. However, network reasons are beyond our control, so the data is not completely accurate. In order to reduce the impact of the network, we kept the code and server configuration files from the very first version when we finished the final version, and then did the tests separately at the same time period. This minimizes the impact of other factors on our data twice.

### Initial testing No optimizations.

After we deployed the first original version of the code without any optimizations to our Amazon Cloud Tomcat, we did a set of stress tests with jmeter for 10 rounds with 5000 users. This time only the GET method was tested, specifically the /goods/to_list page, fetching information about the page's goods.

! [image-20210422183835805](https://github.com/Kentwwx/Hackathon_Team_Project/blob/main/Img/jmeter%E4%B8%80%E6%AC%A1.png)

Here is the aggregated report from the Jmeter stress test. Because the server is on AWS in Virginia, USA, and the request we sent is in Northern China, the test data will have some error rate because of the network (we tested several times on localhost, and the error rate is always 0), and the QPS, that is, the QPS in the image is low because of the network, only 485.

! [image-20210422183835805](https://github.com/Kentwwx/Hackathon_Team_Project/blob/develop/Img/Jmeter%E4%B8%80%E6%AC%A1%E6%95%B0%E6% 8D%AE.png)



### Final test Optimization complete.

After the complete optimization, we tested the same interface in order to first do the same concurrency, and as you can see from the graph, the throughput (QPS) is 3 times higher than without the optimization.

! [image-20210423234220660](https://github.com/Kentwwx/Hackathon_Team_Project/blob/main/Img/%E6%9C%80%E7%BB%88goodslist.png)

But our architecture does not only improve the throughput, it also improves from before in supporting higher concurrent connections. However, this feature cannot be seen from the tests and can only be based on the data provided by the official theories (including, Tomcat, Redis, Mysql, ALB and NLB).



In addition to that, we also tested a single product page, because we thought that people had already figured out which ebook to buy before the promotion started, and then kept refreshing on this ebook page, so we tested this page separately again in the end. Here are the results, you can see that the throughput reached over 3300.

! [image-20210423234821541](https://github.com/Kentwwx/Hackathon_Team_Project/blob/main/Img/%E5%8D%95%E4%B8%AA%E5%95%86%E5%93%81%E6% 9C%80%E7%BB%88.png)

Here is the path to a single ebook.

! [image-20210423235006943](https://github.com/Kentwwx/Hackathon_Team_Project/blob/main/Img/%E5%8D%95%E4%B8%AA%E5%95%86%E5%93%81%E8% B7%AF%E5%BE%84.png)

## Additional details.

#### To protect user data, use MD5 encryption twice

Because data is transmitted in clear text over the network, if the packet is hijacked, the user's clear text password will be intercepted. The first MD5 is added after the user enters the password and is to prevent the user's password from being transmitted in plaintext over the network. The **method is: MD5 (user input + fixed salt)**.

The second MD5 is added before loading the database, in order to prevent the database from being stolen and someone cracking the data that is only MD5 once through the anti-lookup table, so two MD5s are performed for double insurance. **Method: MD5 (last result + random salt)**

``` java
	public static String md5(String src) {
		return DigestUtils.md5Hex(src);
	}
	// fixed salt
	private static final String salt = "1a2b3c4d";

	//add salt after the first MD5
	public static String inputPassToFormPass(String inputPass) {
		String str = ""+salt.charAt(0)+salt.charAt(2) + inputPass +salt.charAt(5) + salt.charAt(4);
		System.out.println(str);
		return md5(str);
	}

	// Before loading the database, add another random salt to the data, and then a second MD5
	public static String formPassToDBPass(String formPass, String salt) {
		String str = ""+salt.charAt(0)+salt.charAt(2) + formPass +salt.charAt(5) + salt.charAt(4);
		return md5(str);
	}
```

##### **Example:**

The user enters a password of:**123456**

In the network transmission is:**d3b1294a61a07da9b49b6e22b2cbd7f9**

Using a random salt of "5e6f7g8h", the database is loaded as:**bcb03326aab1575265da58be91b24382**

User information is protected.

------

#### JSR303 Verifier

We use JSR303 to check the data entered by the user, and implement the Exception Handler to prompt the user for input errors and other information

Jsr303 annotation "**@NotNull**" is used to check that the input is not null. And a custom comment "**@IsMobile**" for input checking.

The code is as follows.

Cell phone number format test: can not be empty or illegal cell phone number format

```java
/**
 * Customize a @IsMobile annotation for mobile number detection
 */
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER })
@Retention(RUNTIME)
@Documented
//Use the class IsMobileValidator to do the validation
@Constraint(validatedBy = {IsMobileValidator.class })
public @interface IsMobile {
	
	boolean required() default true;
	
	String message() default "Wrong format of mobile number";

	Class<? >[] groups() default { };

	Class<? extends Payload>[] payload() default { }
}
```

```java
/**
 * Implement the necessary ConstraintValidato interfaces
 */
public class IsMobileValidator implements ConstraintValidator<IsMobile, String> {

	private boolean required = false;
	
	public void initialize(IsMobile constraintAnnotation) {
		required = constraintAnnotation.required();
	}

	public boolean isValid(String value, ConstraintValidatorContext context) {
		if(required) {
            // Call the validatorUtil class to validate the phone number
			return ValidatorUtil.isMobile(value);
		}else {
			if(StringUtils.isEmpty(value)) { if(StringUtils.isEmpty(value)) {
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
 * Used to implement the detection of cell phone number format
 */
public class ValidatorUtil {
	// use regex to determine if the mobile number matches the format: 11 digits starting with 1
	private static final Pattern mobile_pattern = Pattern.compile("1\\\d{10}");
	
	public static boolean isMobile(String src) {
        // determine if it is empty
		if(StringUtils.isEmpty(src)) {
			return false;
		}
        // determine if the format is mobile number
		Matcher m = mobile_pattern.matcher(src);
		return m.matches();
	}
```

#### Exception handling

After implementing this annotation checker, detecting the mobile number input format no longer requires various judgment conditions, just a simple **@IsMobile** annotation. However, when the input value does not pass the check, it will return exceptions. These exceptions are not user-friendly, so we define another Exception Handler to solve this problem.

##### Before using Exception Handler.

When the user enters the wrong format of cell phone number in the login screen, there will not be any prompt in the interface, only the following error message will be returned in the response, which is very unfriendly to read.

```json
{"timestamp":1618815791860, "status":400, "error": "Bad Request", "exception": "org.springframework.validation.BindException", "errors":[ {"codes":["IsMobile.loginVo.mobile", "IsMobile.mobile", "IsMobile.java.lang.String", "IsMobile"], "arguments":[{"codes":["loginVo. mobile", "mobile"], "arguments":null, "defaultMessage": "mobile", "code": "mobile"},true], "defaultMessage": "Mobile number format error", "objectName":" loginVo", "field": "mobile", "rejectedValue": "233333332222", "bindingFailure":false, "code": "IsMobile"}], "message": "Validation failed for object='loginVo'. Error count: 1", "path":"/login/do_login"}
```



##### Following is the implementation of Exception Handler after.

We intercepted the Exception message in the Exception Handler and prompted the user with the core error report as a pop-up window at.

! [image-20210419150910505](https://github.com/Kentwwx/Hackathon_Team_Project/blob/develop/Img/image-20210419150910505.png)



##### The following is a concrete implementation of the Exception Handler.

This class intercepts the Exception and returns the error message wrapped in our own defined **Result.error** method, which is more concise.

```java
@ControllerAdvice
@ResponseBody
public class GlobalExceptionHandler {
	@ExceptionHandler(value=Exception.class)
	public Result<String> exceptionHandler(HttpServletRequest request, Exception e){
		e.printStackTrace();
        
        // GlobalException is our own Exception class, containing some of our custom error messages, such as password error, cell phone number does not exist, etc.
		if(e instanceof GlobalException) {
			GlobalException ex = (GlobalException)e;
			return Result.error(ex.getCm());
            
            //BindException handles parameter check exceptions
		}else if(e instanceof BindException) {
			BindException ex = (BindException)e;
			List<ObjectError> errors = ex.getAllErrors();
			ObjectError error = errors.get(0);
			String msg = error.getDefaultMessage();
			return Result.error(CodeMsg.BIND_ERROR.fillArgs(msg));
		}else {
            //the rest are server exceptions
			return Result.error(CodeMsg.SERVER_ERROR);
		}
	}
}
```

------

#### Configuring Redis

Because we want to store objects in Redis, which is a key value correspondence storage method, we have to do serialization and deserialization of objects. For object serialization we chose to use fastJson, because fastjson is more friendly to view and read. The implementation in the code.

```java
	// Serialization of objects
	private <T> String beanToString(T value) {
		if(value == null) {
			return null;
		}
		Class<? > clazz = value.getClass();
		if(clazz == int.class || clazz == Integer.class) {
			 return ""+value;
		}else if(clazz == String.class) {
			 return (String)value;
		}else if(clazz == Long.class || clazz == Long.class) { return "+value; }else if(clazz == Long.class) {
			return ""+value;
		}else {
			return JSON.toJSONString(value);
		}
	}
	
	// Deserialization of objects
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
			return (T)Long.valueOf(str);
		}else {
			return JSON.toJavaObject(JSON.parseObject(str), clazz);
		}
	}

```

By listing the information needed for the Redis connection pool in a unified configuration file, and then using the annotation "@ConfigurationProperties(prefix="redis")" in the RedisConfig class, the configuration file information is automatically The information from the configuration file is automatically imported into the Redis connection pool for easy creation.

After configuring the Redis connection pool, you can store information in Redis, but in the process of using Redis, we found a problem that it is easy to duplicate the name of the Redis key, for example, I store a user's information, the key is id1, but I also store an Amazon eBook's information, the key is also id1. information is replaced. So to solve this problem completely, we designed a set of Redis key prefixes to avoid duplication of keys. The specific structure is as follows.

##### Interface: KeyPrefix

```java
public interface KeyPrefix {
		
	public int expireSeconds();
	
	public String getPrefix();
	
}

```

##### abstract class that implements KeyPrefix: BasePrefix

``` java
public abstract class BasePrefix implements KeyPrefix{
	
	private int expireSeconds;
	
	private String prefix;
	
	public BasePrefix(String prefix) {//0 means never expires
		this(0, prefix);
	}
	
	public BasePrefix( int expireSeconds, String prefix) {
		this.expireSeconds = expireSeconds;
		this.prefix = prefix;
	}
	
	public int expireSeconds() {//Default 0 means never expire
		return expireSeconds;
	}

	public String getPrefix() {
		String className = getClass().getSimpleName();
		return className + ":" + prefix;
	}

}

```



As well, we inherited BasePrefix for Goods representing Amazon eBooks, MiaoshaUser representing Users, and Order representing Orders, and then implemented different keyPrefixes for each. as follows

##### GoodsKey.

``` java
public class GoodsKey extends BasePrefix{

	private GoodsKey(int expireSeconds, String prefix) {
		super(expireSeconds, prefix);
	}
	public static GoodsKey getGoodsList = new GoodsKey(60, "gl");
	public static GoodsKey getGoodsDetail = new GoodsKey(60, "gd");
}

```

##### MiaoshaUserkey:

``` java
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

``` java
public class OrderKey extends BasePrefix {

	public OrderKey(String prefix) {
		super(prefix);
	}
	public static OrderKey getMiaoshaOrderByUidGid = new OrderKey("moug");
}

```

So, when we use the set method to store the information in Redis, the code implementation looks like this.

``` java
	public <T> boolean set(KeyPrefix prefix, String key, T value) {
		 Jedis jedis = null;
		 try {
			 jedis = jedisPool.getResource();
             // object serialization
			 String str = beanToString(value);
			 if(str == null || str.length() <= 0) {
				 return false;
			 }
			//generate the real key
			 String realKey = prefix.getPrefix() + key;
			 int seconds = prefix.expireSeconds();
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

##### Practical example.

When we use the Redis set, storing a user id of 1 into Redis

``` java
    	MiaoshaUser user = new MiaoshaUser();
    	user.setId(1L);
    	redisService.set(MiaoshaUserKey.getById, ""+1, user);
```



Looking at the Redis key, I see that it is actually stored as "MiaoshaUserKey:id1". This solves the Redis key conflict problem nicely.

! [image-20210419234525403](https://github.com/Kentwwx/Hackathon_Team_Project/blob/develop/Img/image-20210419234525403.png)

------

#### Distributed Session

Sessions record the user's login status, and are used by all major web pages today. But if there are multiple servers, a user's session is stored in only one of them, but his request is forwarded to another server, then his session cannot be accurately retrieved. So we designed the distributed session method to solve this problem. This is done by storing the user's session information in the Redis cache, so that each time we look for a session, we look for it from the only Redis that is shared.

We first implement an addCookie method in the User Service class, with the following code.

```java
	private void addCookie(HttpServletResponse response, String token, MiaoshaUser user) {
        // store token as key and user's information as value in Redis
		redisService.set(MiaoshaUserKey.token, token, user);
        
		cookie cookie = new Cookie(COOKI_NAME_TOKEN, token);
        //Cookie's expire time is the same as the expire time of the corresponding key in redis
		cookie.setMaxAge(MiaoshaUserKey.token.expireSeconds());
		cookie.setPath("/");
        //add the cookie with the token to the response
		response.addCookie(cookie);
	}

```

addCookie method is called in the login method, as the user logs in, the user gets a cookie with a token, and the next time the user enters the page, the system will first find out if there is a token from Redis, and if so, return the user information and refresh the Session existence time. The code is as follows.

```java
	public MiaoshaUser getByToken(HttpServletResponse response, String token) {
		if(StringUtils.isEmpty(token)) {
			return null;
		}
		MiaoshaUser user = redisService.get(MiaoshaUserKey.token, token, MiaoshaUser.class);
		//extend the validity
		if(user ! = null) {
			addCookie(response, token, user);
		}
		return user;
	}
```

We implement an Argument Resolver class and do the thing to determine if there is this user Session in the resolve Argument method. Such a structure first resolves the Argument, and then passes it into the controller, so that different pages have the ability to determine whether the Session exists, but the code is written once is enough to make the code more concise. resolve Argument method is implemented as follows.

```java
	public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest webRequest, WebDataBinderFactory binderFactory) throws Exception {
        //get Request and Response
		HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
		HttpServletResponse response = webRequest.getNativeResponse(HttpServletResponse.class);
		//Token may exist in the parameter, or in the cookie
		String paramToken = request.getParameter(MiaoshaUserService.COOKI_NAME_TOKEN);
		String cookieToken = getCookieValue(request, MiaoshaUserService.COOKI_NAME_TOKEN);
        
		if(StringUtils.isEmpty(cookieToken) && StringUtils.isEmpty(paramToken)) {
			return null;
		}
		String token = StringUtils.isEmpty(paramToken)?cookieToken:paramToken;
		return userService.getByToken(response, token);
	}

```

