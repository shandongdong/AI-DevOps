package com.ruoyi.aidevops.service.calc;

import java.util.List;
import com.ruoyi.aidevops.domain.calc.CalcHistoryEntity;

/**
 * 计算器历史记录 服务层
 *
 * @author shandongdong
 */
public interface ICalcHistoryService
{
    /**
     * 四则运算并落库历史记录
     *
     * @param operator      运算符（+ - * /）
     * @param firstNumber   第一个数
     * @param secondNumber  第二个数
     * @return 含运算结果的历史记录实体（含 calcId）
     */
    public CalcHistoryEntity compute(String operator, Double firstNumber, Double secondNumber);

    /**
     * 查询计算历史记录列表（按当前用户隔离）
     *
     * @param calcHistory 查询条件
     * @return 计算历史记录集合
     */
    public List<CalcHistoryEntity> selectCalcHistoryList(CalcHistoryEntity calcHistory);

    /**
     * 批量软删除计算历史记录
     *
     * @param calcIds 需要删除的记录ID数组
     * @return 结果
     */
    public int deleteCalcHistoryByIds(Long[] calcIds);
}
