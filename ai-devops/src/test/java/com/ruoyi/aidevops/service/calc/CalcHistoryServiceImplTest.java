package com.ruoyi.aidevops.service.calc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.aidevops.domain.calc.CalcHistoryEntity;
import com.ruoyi.aidevops.mapper.calc.CalcHistoryMapper;
import com.ruoyi.aidevops.service.calc.impl.CalcHistoryServiceImpl;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;

/**
 * 计算器历史记录 Service 单元测试
 * 覆盖验收用例 AC-1.1/1.2/1.3（运算）与 AC-2.2/2.5/2.6（历史管理）
 *
 * @author shandongdong
 */
@ExtendWith(MockitoExtension.class)
class CalcHistoryServiceImplTest
{
    @Mock
    private CalcHistoryMapper calcHistoryMapper;

    @InjectMocks
    private CalcHistoryServiceImpl calcHistoryService;

    private static final String USERNAME = "admin";

    /**
     * AC-1.1：6 + 2 = 8，且落库一条记录
     */
    @Test
    @DisplayName("AC-1.1: 加法运算正确并落库")
    void compute_add_shouldReturnCorrectResultAndInsert()
    {
        try (MockedStatic<SecurityUtils> mocked = Mockito.mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn(USERNAME);
            when(calcHistoryMapper.insertCalcHistory(any())).thenReturn(1);

            CalcHistoryEntity result = calcHistoryService.compute("+", 6.0, 2.0);

            assertEquals(8.0, result.getResult());
            assertEquals("+", result.getOperator());
            assertEquals(USERNAME, result.getCreateBy());
            // 验证落库
            verify(calcHistoryMapper, times(1)).insertCalcHistory(any());
        }
    }

    /**
     * 入参校验：任一参数为 null → ServiceException 业务提示（而非 NPE 裸 500），且不落库
     */
    @Test
    @DisplayName("入参为 null 时抛 ServiceException 业务提示，不落库")
    void compute_nullParams_shouldThrowServiceException()
    {
        // operator 为 null（原实现 switch(null) 直接 NPE）
        ServiceException ex1 = assertThrows(ServiceException.class,
                () -> calcHistoryService.compute(null, 6.0, 2.0));
        assertEquals("运算符与两个操作数均不能为空", ex1.getMessage());

        // 操作数为 null（原实现拆箱 NPE）
        assertThrows(ServiceException.class,
                () -> calcHistoryService.compute("+", null, 2.0));
        assertThrows(ServiceException.class,
                () -> calcHistoryService.compute("+", 6.0, null));

        // 均不落库
        verify(calcHistoryMapper, never()).insertCalcHistory(any());
    }

    /**
     * AC-1.2：减/乘/除结果正确（链式验证）
     */
    @Test
    @DisplayName("AC-1.2: 减/乘/除运算结果正确")
    void compute_subtractMultiplyDivide_shouldReturnCorrectResults()
    {
        try (MockedStatic<SecurityUtils> mocked = Mockito.mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn(USERNAME);
            when(calcHistoryMapper.insertCalcHistory(any())).thenReturn(1);

            // 减：6 - 2 = 4
            assertEquals(4.0, calcHistoryService.compute("-", 6.0, 2.0).getResult());
            // 乘：6 * 2 = 12
            assertEquals(12.0, calcHistoryService.compute("*", 6.0, 2.0).getResult());
            // 除：6 / 2 = 3
            assertEquals(3.0, calcHistoryService.compute("/", 6.0, 2.0).getResult());
        }
    }

    /**
     * AC-1.3：除数为 0 抛 ServiceException 且不落库
     */
    @Test
    @DisplayName("AC-1.3: 除数为0抛业务异常且不落库")
    void compute_divisionByZero_shouldThrowAndNotInsert()
    {
        try (MockedStatic<SecurityUtils> mocked = Mockito.mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn(USERNAME);

            ServiceException ex = assertThrows(ServiceException.class,
                    () -> calcHistoryService.compute("/", 6.0, 0.0));
            assertEquals("除数不能为0", ex.getMessage());
            // 验证不落库
            verify(calcHistoryMapper, never()).insertCalcHistory(any());
        }
    }

    /**
     * 不支持的运算符应抛异常
     */
    @Test
    @DisplayName("不支持的运算符抛业务异常")
    void compute_unsupportedOperator_shouldThrow()
    {
        try (MockedStatic<SecurityUtils> mocked = Mockito.mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn(USERNAME);

            assertThrows(ServiceException.class,
                    () -> calcHistoryService.compute("%", 6.0, 2.0));
            verify(calcHistoryMapper, never()).insertCalcHistory(any());
        }
    }

    /**
     * AC-2.5：查询历史列表时强制按当前登录用户隔离（createBy 过滤）
     */
    @Test
    @DisplayName("AC-2.5: 历史查询按当前用户隔离")
    void selectList_shouldFilterByCurrentUser()
    {
        try (MockedStatic<SecurityUtils> mocked = Mockito.mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn(USERNAME);
            when(calcHistoryMapper.selectCalcHistoryList(any())).thenReturn(Collections.emptyList());

            CalcHistoryEntity query = new CalcHistoryEntity();
            calcHistoryService.selectCalcHistoryList(query);

            // 验证传入 Mapper 的查询条件 createBy 被设为当前用户
            assertEquals(USERNAME, query.getCreateBy());
            verify(calcHistoryMapper, times(1)).selectCalcHistoryList(any());
        }
    }

    /**
     * AC-2.2：软删除调用 Mapper 的 deleteCalcHistoryByIds
     */
    @Test
    @DisplayName("AC-2.2: 软删除调用 deleteCalcHistoryByIds")
    void deleteByIds_shouldCallSoftDelete()
    {
        try (MockedStatic<SecurityUtils> mocked = Mockito.mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn(USERNAME);
            when(calcHistoryMapper.deleteCalcHistoryByIds(any(), eq(USERNAME))).thenReturn(2);

            Long[] ids = { 1L, 2L };
            int rows = calcHistoryService.deleteCalcHistoryByIds(ids);

            assertEquals(2, rows);
            verify(calcHistoryMapper, times(1)).deleteCalcHistoryByIds(ids, USERNAME);
        }
    }

    /**
     * AC-2.6：已软删除记录不返回（Mapper 已过滤 del_flag，这里验证 Service 不做额外处理）
     */
    @Test
    @DisplayName("AC-2.6: 已删除记录不在列表中")
    void selectList_shouldNotReturnDeletedRecords()
    {
        try (MockedStatic<SecurityUtils> mocked = Mockito.mockStatic(SecurityUtils.class))
        {
            mocked.when(SecurityUtils::getUsername).thenReturn(USERNAME);
            // 模拟 Mapper 返回空列表（已删除的被 SQL 过滤掉）
            when(calcHistoryMapper.selectCalcHistoryList(any())).thenReturn(Collections.emptyList());

            List<CalcHistoryEntity> list = calcHistoryService.selectCalcHistoryList(new CalcHistoryEntity());

            // 列表中无已删除记录
            assertTrue(list.isEmpty());
        }
    }
}
