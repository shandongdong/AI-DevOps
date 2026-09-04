package com.ruoyi.aidevops.mapper.calc;

import java.util.List;
import com.ruoyi.aidevops.domain.calc.CalcHistoryEntity;

/**
 * 计算器历史记录 数据层
 *
 * @author shandongdong
 */
public interface CalcHistoryMapper
{
    /**
     * 新增计算历史记录
     *
     * @param calcHistory 计算历史记录
     * @return 结果
     */
    public int insertCalcHistory(CalcHistoryEntity calcHistory);

    /**
     * 查询计算历史记录列表（按当前用户隔离，过滤已软删除）
     *
     * @param calcHistory 查询条件（含 createBy 数据隔离、operator/createTime 过滤）
     * @return 计算历史记录集合
     */
    public List<CalcHistoryEntity> selectCalcHistoryList(CalcHistoryEntity calcHistory);

    /**
     * 批量软删除计算历史记录（置 del_flag=2，不物理删除）
     *
     * @param calcIds 需要删除的记录ID数组
     * @param updateBy 更新者
     * @return 结果
     */
    public int deleteCalcHistoryByIds(Long[] calcIds, String updateBy);
}
