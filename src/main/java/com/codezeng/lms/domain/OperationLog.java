package com.codezeng.lms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "operation_log", indexes = {
        @Index(name = "idx_operation_deleted_created", columnList = "deleted,create_time"),
        @Index(name = "idx_operation_user_created", columnList = "username,create_time"),
        @Index(name = "idx_operation_module_created", columnList = "module_name,create_time"),
        @Index(name = "idx_operation_ip_created", columnList = "ip_address,create_time")
})
public class OperationLog extends BaseEntity {

    @Column(nullable = false, length = 64)
    private String username;

    @Column(nullable = false, length = 64)
    private String moduleName;

    @Column(nullable = false, length = 64)
    private String operation;

    @Column(length = 1000)
    private String detail;

    @Column(length = 64)
    private String ipAddress;

    public OperationLog() {
    }

    public OperationLog(String username, String moduleName, String operation, String detail) {
        this.username = username;
        this.moduleName = moduleName;
        this.operation = operation;
        this.detail = detail;
    }

    public OperationLog(String username, String moduleName, String operation, String detail, String ipAddress) {
        this(username, moduleName, operation, detail);
        this.ipAddress = ipAddress;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
}
