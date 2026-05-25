package com.codezeng.lms.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;

@Component
@Order(100)
public class MysqlSchemaCommentInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MysqlSchemaCommentInitializer.class);

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    public MysqlSchemaCommentInitializer(DataSource dataSource, JdbcTemplate jdbcTemplate) {
        this.dataSource = dataSource;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String productName = metaData.getDatabaseProductName();
            if (productName == null || !productName.toLowerCase().contains("mysql")) {
                log.debug("Skip MySQL schema comments for database product: {}", productName);
                return;
            }
            applyComments(connection.getCatalog());
        }
    }

    private void applyComments(String schemaName) {
        for (TableComment table : comments()) {
            if (!tableExists(schemaName, table.name())) {
                continue;
            }
            execute("ALTER TABLE `" + table.name() + "` COMMENT = '" + table.comment() + "'");
            for (ColumnComment column : table.columns()) {
                if (columnExists(schemaName, table.name(), column.name())) {
                    execute("ALTER TABLE `" + table.name() + "` MODIFY COLUMN `" + column.name() + "` "
                            + column.definition() + " COMMENT '" + column.comment() + "'");
                }
            }
        }
    }

    private boolean tableExists(String schemaName, String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = ? AND table_name = ?
                """, Integer.class, schemaName, tableName);
        return count != null && count > 0;
    }

    private boolean columnExists(String schemaName, String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ? AND column_name = ?
                """, Integer.class, schemaName, tableName, columnName);
        return count != null && count > 0;
    }

    private void execute(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (RuntimeException ex) {
            log.warn("Failed to apply MySQL schema comment with SQL: {}", sql, ex);
        }
    }

    private List<TableComment> comments() {
        return List.of(
                table("book_info", "图书馆藏信息表",
                        baseColumns(),
                        col("title", "varchar(128) not null", "书名"),
                        col("isbn", "varchar(32) not null", "ISBN 标准书号"),
                        col("author", "varchar(128) not null", "作者"),
                        col("publisher", "varchar(128) not null", "出版社"),
                        col("category_id", "bigint", "图书分类ID"),
                        col("total_quantity", "integer not null", "馆藏总册数"),
                        col("available_quantity", "integer not null", "当前可借册数"),
                        col("subtitle", "varchar(128)", "副标题"),
                        col("translator", "varchar(128)", "译者"),
                        col("publish_date", "date", "出版日期"),
                        col("price", "decimal(38,2)", "定价"),
                        col("pages", "integer", "页数"),
                        col("binding", "varchar(64)", "装帧"),
                        col("summary", "varchar(2000)", "内容简介"),
                        col("cover_url", "varchar(255)", "封面图片地址"),
                        col("location", "varchar(64)", "馆藏位置"),
                        col("borrow_count", "bigint not null", "累计借阅次数"),
                        col("reference_only", "bit not null", "是否仅限馆内阅览")),
                table("book_category", "图书分类表",
                        baseColumns(),
                        col("name", "varchar(64) not null", "分类名称"),
                        col("parent_id", "bigint", "父级分类ID"),
                        col("description", "varchar(200)", "分类说明")),
                table("borrow_record", "借阅记录表",
                        baseColumns(),
                        col("book_id", "bigint not null", "图书ID"),
                        col("reader_id", "bigint not null", "读者ID"),
                        col("borrow_date", "date not null", "借出日期"),
                        col("due_date", "date not null", "应还日期"),
                        col("return_date", "date", "实际归还日期"),
                        col("renew_count", "integer not null", "续借次数"),
                        col("status", "varchar(32) not null", "借阅状态"),
                        col("fine_amount", "decimal(38,2) not null", "本次借阅产生罚款金额")),
                table("fine_record", "罚款记录表",
                        baseColumns(),
                        col("reader_id", "bigint not null", "读者ID"),
                        col("borrow_record_id", "bigint", "关联借阅记录ID"),
                        col("reason", "varchar(64) not null", "罚款原因"),
                        col("amount", "decimal(38,2) not null", "罚款金额"),
                        col("status", "varchar(32) not null", "罚款状态"),
                        col("paid_at", "datetime(6)", "缴纳时间")),
                table("localized_text", "多语言文本表",
                        baseColumns(),
                        col("entity_type", "varchar(64) not null", "实体类型"),
                        col("entity_id", "bigint not null", "实体ID"),
                        col("field_key", "varchar(64) not null", "字段键名"),
                        col("locale_tag", "varchar(32) not null", "语言标识"),
                        col("text", "varchar(500) not null", "本地化文本")),
                table("notification", "站内通知消息表",
                        baseColumns(),
                        col("reader_id", "bigint not null", "接收读者ID"),
                        col("title", "varchar(128) not null", "消息标题"),
                        col("content", "varchar(1000) not null", "消息内容"),
                        col("channel", "varchar(32) not null", "通知渠道"),
                        col("status", "varchar(32) not null", "消息状态"),
                        col("sent_at", "datetime(6) not null", "发送时间"),
                        col("read_at", "datetime(6)", "阅读时间")),
                table("operation_log", "操作日志表",
                        baseColumns(),
                        col("username", "varchar(64) not null", "操作用户"),
                        col("module_name", "varchar(64) not null", "业务模块"),
                        col("operation", "varchar(64) not null", "操作类型"),
                        col("detail", "varchar(1000)", "操作详情"),
                        col("ip_address", "varchar(64)", "操作IP地址")),
                table("reader_info", "读者信息表",
                        baseColumns(),
                        col("reader_no", "varchar(32) not null", "读者证号"),
                        col("name", "varchar(64) not null", "读者姓名"),
                        col("gender", "varchar(16)", "性别"),
                        col("phone", "varchar(32)", "联系电话"),
                        col("email", "varchar(128) not null", "电子邮箱"),
                        col("identity_no", "varchar(64) not null", "证件号或学号"),
                        col("reader_type", "varchar(32) not null", "读者类型"),
                        col("member_level", "varchar(32) not null", "会员等级"),
                        col("registered_at", "datetime(6)", "注册时间"),
                        col("status", "varchar(32) not null", "账号状态"),
                        col("deposit_amount", "decimal(38,2) not null", "押金金额")),
                table("reservation_record", "预约记录表",
                        baseColumns(),
                        col("book_id", "bigint not null", "预约图书ID"),
                        col("reader_id", "bigint not null", "预约读者ID"),
                        col("reserved_at", "datetime(6) not null", "预约时间"),
                        col("expires_at", "datetime(6)", "预约保留截止时间"),
                        col("status", "varchar(32) not null", "预约状态")),
                table("system_config", "系统配置表",
                        baseColumns(),
                        col("config_key", "varchar(100) not null", "配置键"),
                        col("config_value", "varchar(500) not null", "配置值"),
                        col("display_name", "varchar(100) not null", "配置名称"),
                        col("description", "varchar(500)", "配置说明")),
                table("sys_user", "系统用户表",
                        baseColumns(),
                        col("username", "varchar(64) not null", "登录账号"),
                        col("email", "varchar(128) not null", "电子邮箱"),
                        col("password", "varchar(255) not null", "加密后的登录密码"),
                        col("nickname", "varchar(64) not null", "用户昵称"),
                        col("phone", "varchar(32)", "联系电话"),
                        col("reader_no", "varchar(32)", "绑定读者证号"),
                        col("avatar_url", "varchar(255)", "头像地址"),
                        col("managed_location_prefix", "varchar(64)", "馆员管理馆藏位置前缀"),
                        col("permission_codes", "varchar(1000)", "额外权限编码列表"),
                        col("role", "varchar(32) not null", "用户角色"),
                        col("status", "varchar(32) not null", "账号状态"),
                        col("last_login_time", "datetime(6)", "最后登录时间"))
        );
    }

    private List<ColumnComment> baseColumns() {
        return List.of(
                col("id", "bigint not null auto_increment", "主键ID"),
                col("create_time", "datetime(6) not null", "创建时间"),
                col("update_time", "datetime(6) not null", "更新时间"),
                col("deleted", "bit not null", "逻辑删除标记")
        );
    }

    private TableComment table(String name, String comment, List<ColumnComment> baseColumns, ColumnComment... columns) {
        return new TableComment(name, comment,
                java.util.stream.Stream.concat(baseColumns.stream(), java.util.Arrays.stream(columns)).toList());
    }

    private ColumnComment col(String name, String definition, String comment) {
        return new ColumnComment(name, definition, comment);
    }

    private record TableComment(String name, String comment, List<ColumnComment> columns) {
    }

    private record ColumnComment(String name, String definition, String comment) {
    }
}
