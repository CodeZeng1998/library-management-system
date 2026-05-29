# 图书管理系统

基于 Spring Boot 3、Spring Security、Spring Data JPA、Thymeleaf 和 Layui 的单体图书管理系统。项目按功能设计文档实现了第一阶段核心能力，并根据产品分析报告补齐预约通知、站内消息、定时提醒、批量导入导出和移动端适配等 P0 能力。

## 已实现功能

- 登录认证、角色权限、7 天记住登录状态
- 超级管理员、图书管理员、普通读者角色模型
- 图书信息管理：新增、编辑、查询、软删除、库存字段
- 读者信息管理：编号、类型、会员等级、状态、押金
- 借阅管理：借书、还书、续借、逾期扫描、罚款生成、到期提醒
- 预约管理：无库存可预约、单书最多 5 人排队、取消预约、归还后自动通知下一位预约读者、过期释放
- 消息中心：站内信通知、未读/已读状态、预约到书/预约过期/到期/逾期提醒
- 批量处理：图书和读者 CSV 批量导入、批量导出
- 移动端适配：侧边栏、工具栏、表格和导入控件的响应式布局
- 罚款管理：缴纳、减免
- 数据看板、系统配置、操作日志
- 默认使用本地 H2 内存库快速启动，MySQL 通过环境变量切换
- 国际化支持：简体中文、繁體中文、英文界面切换，业务枚举和分类字段支持多语言显示

## 技术栈

- Java 17
- Spring Boot 3.3
- Spring Security
- Spring Data JPA
- Thymeleaf
- Layui 2.x
- MySQL 8
- Maven

## 快速启动

```bash
mvn clean package
set LMS_BOOTSTRAP_ADMIN_PASSWORD=your-strong-password
java -jar target/library-management-system.jar
```

访问：

```text
http://localhost:8090
```

首次启动会创建一个 bootstrap 管理员。生产或共享环境请提前设置密码：

```bash
set LMS_BOOTSTRAP_ADMIN_PASSWORD=your-strong-password
java -jar target/library-management-system.jar
```

如果启用 bootstrap 管理员或 demo 种子数据，必须通过环境变量显式配置 `LMS_BOOTSTRAP_ADMIN_PASSWORD` 和 `LMS_DEMO_PASSWORD`。系统不会生成或在日志中输出临时密码，避免凭证泄露。

## 国际化扩展

- 前端页面使用 Thymeleaf `#{...}` 和 `messages*.properties` 管理界面文案，侧边栏语言选择通过 `lang` 参数切换当前会话语言。
- 后端控制器和扫码接口通过 `MessageSource` 解析提示文案、枚举标签和 API 返回消息。
- 数据库业务字段使用 `localized_text` 表保存翻译，当前已用于图书分类名称；字段维度为 `entityType + entityId + fieldKey + localeTag`。
- 新增语言时，增加对应 `messages_{locale}.properties`，在 `i18n.js` 和导航语言下拉中加入 locale 选项，并按需写入 `localized_text` 翻译数据。

## 数据库初始化

创建数据库：

```sql
CREATE DATABASE library_management_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

使用 MySQL 时启用 `mysql` profile，并通过环境变量提供连接凭证：

```bash
set LMS_MYSQL_USERNAME=your-db-user
set LMS_MYSQL_PASSWORD=your-db-password
java -jar target/library-management-system.jar --spring.profiles.active=mysql
```

## 后续计划

- 座位预约系统：分区、签到、爽约惩罚、实时空座展示
- 消息中心多渠道发送：邮件、短信、微信或企业微信
- Excel xlsx 批量导入导出和失败数据下载
- 毕业生离校清算流程
- 图书推荐、热门榜单、学科书单
- 电子资源管理和使用统计
- PDF/Excel 报表
- 图书封面上传与 ISBN 自动识别
- 更细粒度的菜单权限、按钮权限和数据权限
