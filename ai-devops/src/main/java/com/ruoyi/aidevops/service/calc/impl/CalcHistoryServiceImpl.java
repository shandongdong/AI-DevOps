package com.ruoyi.aidevops.service.calc.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.aidevops.domain.calc.CalcHistoryEntity;
import com.ruoyi.aidevops.mapper.calc.CalcHistoryMapper;
import com.ruoyi.aidevops.service.calc.ICalcHistoryService;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;

/**
 * 计算器历史记录 服务实现
 *
 * @author shandongdong
 */
@Service
public class CalcHistoryServiceImpl implements ICalcHistoryService
{
    @Autowired
    private CalcHistoryMapper calcHistoryMapper;

    /**
     * 四则运算并落库
     * 除数为 0 抛 ServiceException 不落库（AC-1.3），合法运算写入历史表
     *
     * @param operator      运算符
     * @param firstNumber   第一个数
     * @param secondNumber  第二个数
     * @return 含运算结果的历史记录实体
     */
    @Override
    @Transactional
    public CalcHistoryEntity compute(String operator, Double firstNumber, Double secondNumber)
    {
        double result;
        switch (operator)
        {
            case "+":
                result = firstNumber + secondNumber;
                break;
            case "-":
                result = firstNumber - secondNumber;
                break;
            case "*":
                result = firstNumber * secondNumber;
                break;
            case "/":
                // 除数为 0 拦截：返回业务错误，不落库（AC-1.3）
                if (secondNumber == 0)
                {
                    throw new ServiceException("除数不能为0");
                }
                result = firstNumber / secondNumber;
                break;
            default:
                throw new ServiceException("不支持的运算符：" + operator);
        }

        // 合法运算落库
        CalcHistoryEntity entity = new CalcHistoryEntity();
        entity.setFirstNumber(firstNumber);
        entity.setSecondNumber(secondNumber);
        entity.setOperator(operator);
        entity.setResult(result);
        entity.setStatus("0");
        entity.setCreateBy(SecurityUtils.getUsername());
        calcHistoryMapper.insertCalcHistory(entity);
        return entity;
    }

    /**
     * 查询历史列表（按当前用户隔离，AC-2.5）
     */
    @Override
    public List<CalcHistoryEntity> selectCalcHistoryList(CalcHistoryEntity calcHistory)
    {
        // 强制按当前登录用户过滤，确保数据隔离
        calcHistory.setCreateBy(SecurityUtils.getUsername());
        return calcHistoryMapper.selectCalcHistoryList(calcHistory);
    }

    /**
     * 软删除（置 del_flag=2，AC-2.2）
     */
    @Override
    public int deleteCalcHistoryByIds(Long[] calcIds)
    {
        return calcHistoryMapper.deleteCalcHistoryByIds(calcIds, SecurityUtils.getUsername());
    }
}
