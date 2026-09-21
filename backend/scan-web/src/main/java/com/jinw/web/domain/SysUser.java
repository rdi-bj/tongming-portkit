package com.jinw.web.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jinw.web.base.BaseEntity;
import lombok.Data;

import java.util.Date;

/**
 * <p>
 * 用户信息表
 * </p>
 *
 * @author lilw
 * @since 2021-08-13
 */
@TableName("C_T_SYS_USER")
@Data
public class SysUser extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 用户姓名拼音
     */
    private String pyName;

    /**
     * 所属单位
     */
    private String parentUnit;

    /**
     * 所属部门id
     */
    private String deptId;

    /**
     * 生日
     */
    private String birthDay;

    /**
     * 节点ID
     */
    private String orgId;

    /**
     * 用户账号
     */
    private String loginName;

    /**
     * 用户中文名
     */
    private String userName;

    /**
     * 显示顺序
     */
    private Integer orderNum;

    /**
     * 用户类型（00系统用户 01 单位用户  02 普通用户）
     */
    private String userType;

    /**
     * 用户邮箱
     */
    private String email;

    /**
     * 手机号码
     */
    private String phone;

    /**
     * 短号
     */
    private String shortPhone;

    /**
     * 办公室电话
     */
    private String officePhone;

    /**
     * 身份证号码
     */
    private String cardNumber;

    /**
     * 用户性别（0男 1女 2未知）
     */
    private String userGender;

    /**
     * 政治面貌
     */
    private String politicStatus;

    /**
     * 职务
     */
    private String postName;

    /**
     * 头像地址
     */
    private String avatar;

    /**
     * 密码
     */
    private String password;

    /**
     * 帐号状态（0正常  1 锁定  2 停用）
     */
    private String status;


    /**
     * 最后登录IP
     */
    private String loginIp;

    /**
     * 最后登录时间
     */
    private Date loginDate;

    /**
     * 最后修改密码时间
     */
    private Date passwordChangeDate;

    /**
     * 备注
     */
    private String remark;

    /**
     * 钉钉ID
     */
//    private String dingTalkId;

    /**
     * 公司(单位)名称
     */
//    private String companyName;

    /**
     * 部门名称
     */
    private String deptName;


}
