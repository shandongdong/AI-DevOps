package com.ruoyi.aidevops.controller.calc;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.aidevops.domain.calc.CalcComputeRequest;
import com.ruoyi.aidevops.domain.calc.CalcHistoryEntity;
import com.ruoyi.aidevops.service.calc.ICalcHistoryService;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;

/**
 * 计算器
 *
 * @author shandongdong
 */
@RestController
@RequestMapping("/aidevops/calc")
public class CalcController extends BaseController
{
    @Autowired
    private ICalcHistoryService calcHistoryService;

    // 取消注释执行 mvn test 会触发 ArchUnit 约束检查，可验证 ArchUnit 约束（原则 IV 分层单向依赖）
    // @Autowired private CalcHistoryMapper calcHistoryMapper;

    /**
     * 四则运算并落库历史（POST + JSON body，RESTful：运算会创建历史记录，非幂等）
     * 除数为 0 由 Service 抛 ServiceException，全局异常处理器转 AjaxResult.error（AC-1.3）
     */
    @Log(title = "计算器", businessType = BusinessType.INSERT)
    @PreAuthorize("@ss.hasPermi('aidevops:calc:compute')")
    @PostMapping("/compute")
    public AjaxResult compute(@RequestBody CalcComputeRequest req)
    {
        CalcHistoryEntity entity = calcHistoryService.compute(req.operator(), req.first(), req.second());
        return AjaxResult.success(entity);
    }

    /**
     * 查询计算历史列表（分页，按当前用户隔离）
     */
    @PreAuthorize("@ss.hasPermi('aidevops:calc:list')")
    @GetMapping("/list")
    public TableDataInfo list(CalcHistoryEntity calcHistory)
    {
        startPage();
        List<CalcHistoryEntity> list = calcHistoryService.selectCalcHistoryList(calcHistory);
        return getDataTable(list);
    }

    /**
     * 批量软删除计算历史（AC-2.2）
     */
    @Log(title = "计算器历史", businessType = BusinessType.DELETE)
    @PreAuthorize("@ss.hasPermi('aidevops:calc:remove')")
    @DeleteMapping("/{calcIds}")
    public AjaxResult remove(@PathVariable Long[] calcIds)
    {
        return toAjax(calcHistoryService.deleteCalcHistoryByIds(calcIds));
    }
}
