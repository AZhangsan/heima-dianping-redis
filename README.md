# heima-redis

## redis黑马点评介绍

<img src="./images/项目简介.png" alt=" " style="zoom:33%;" />

## 功能介绍

###### <img src="./images/功能介绍.png" alt="功能介绍" style="zoom: 33%;" />

## 项目结构

######  

<img src="./images/项目结构.png" alt="![功能介绍]" style="zoom: 33%;" />

## 短信登录模块

### Session问题

1、多台Tomcat之间并不共享Session存储空间，当请求切换到不同的服务器时会导致数据丢失的问题。

2、Session在重启服务器时也会将数据丢失。

### Session替代方案

- 数据共享
- 内存存储
- key、value结构

### 基于Redis实现共享Session登录

- 验证码存储使用String结构
- 用户信息存储
  - 字符串结构：将对象序列化为JSON存储在Redis中。
  - HASH结构：将对象的每个字段进行存储，支持单个字段CRUD，内存占用比String更少

###### <img src="./images/改造为redis存储信息.png" alt="改造为redis存储信息" style="zoom: 25%;" />

### 拦截器优化

.

**新增token拦截器：负责刷新redis中token的有效时间**

## 商户查询模块

店铺增加缓存

![店铺增加缓存](./images/店铺增加缓存.png)

**练习**：店铺类型查询业务添加缓存

<img src="./images/店铺类型添加缓存.png" alt="店铺类型添加缓存" style="zoom: 50%;" />























