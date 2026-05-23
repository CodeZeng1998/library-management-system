# 图书管理系统

基于 Spring Boot 3、Spring Security、Spring Data JPA、Thymeleaf 和 Layui 的单体图书管理系统。项目按功能设计文档实现了第一阶段核心能力，并为预约、罚款、系统配置和操作日志预留了完整数据模型与页面入口。

## 已实现功能

- 登录认证、角色权限、7 天记住登录状态
- 超级管理员、图书管理员、普通读者角色模型
- 图书信息管理：新增、编辑、查询、软删除、库存字段
- 读者信息管理：编号、类型、会员等级、状态、押金
- 借阅管理：借书、还书、续借、逾期扫描、罚款生成
- 预约管理：无库存可预约、单书最多 5 人排队、取消预约
- 罚款管理：缴纳、减免
- 数据看板、系统配置、操作日志
- H2 开发数据库默认可直接运行，另提供 MySQL 配置文件

## 技术栈

- Java 17
- Spring Boot 3.3
- Spring Security
- Spring Data JPA
- Thymeleaf
- Layui 2.x
- H2 / MySQL 8
- Maven

## 快速启动

```bash
mvn clean package
java -jar target/library-management-system.jar
```

访问：

```text
http://localhost:8090
```

默认账号：

```text
admin / admin123
librarian / librarian123
```

H2 控制台：

```text
http://localhost:8090/h2-console
JDBC URL: jdbc:h2:mem:lms
User: sa
Password: 空
```

## 使用 MySQL

创建数据库：

```sql
CREATE DATABASE library_management_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

修改 `src/main/resources/application-mysql.yml` 中的账号密码，然后运行：

```bash
java -jar target/library-management-system.jar --spring.profiles.active=mysql
```

## 后续计划

- Excel 批量导入导出
- PDF/Excel 报表
- 邮件/短信预约通知
- 图书封面上传与 ISBN 自动识别
- 更细粒度的菜单权限、按钮权限和数据权限
