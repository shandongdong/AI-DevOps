package com.ruoyi.aidevops.domain.calc;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.ColumnType;
import com.ruoyi.common.core.domain.BaseEntity;

/**
 * 计算器历史记录 ai_devops_calc_history
 *
 * @author shandongdong
 */
public class CalcHistoryEntity extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 记录ID */
    @Excel(name = "记录序号", cellType = ColumnType.NUMERIC)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long calcId;

    /** 第一个数 */
    @Excel(name = "第一个数")
    private Double firstNumber;

    /** 第二个数 */
    @Excel(name = "第二个数")
    private Double secondNumber;

    /** 运算符(+ - * /) */
    @Excel(name = "运算符")
    private String operator;

    /** 运算结果 */
    @Excel(name = "运算结果")
    private Double result;

    /** 状态（0正常 1停用） */
    private String status;

    /** 删除标志（0存在 2删除） */
    private String delFlag;

    public Long getCalcId()
    {
        return calcId;
    }

    public void setCalcId(Long calcId)
    {
        this.calcId = calcId;
    }

    public Double getFirstNumber()
    {
        return firstNumber;
    }

    public void setFirstNumber(Double firstNumber)
    {
        this.firstNumber = firstNumber;
    }

    public Double getSecondNumber()
    {
        return secondNumber;
    }

    public void setSecondNumber(Double secondNumber)
    {
        this.secondNumber = secondNumber;
    }

    public String getOperator()
    {
        return operator;
    }

    public void setOperator(String operator)
    {
        this.operator = operator;
    }

    public Double getResult()
    {
        return result;
    }

    public void setResult(Double result)
    {
        this.result = result;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getDelFlag()
    {
        return delFlag;
    }

    public void setDelFlag(String delFlag)
    {
        this.delFlag = delFlag;
    }
}
