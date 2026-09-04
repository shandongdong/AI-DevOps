import request from '@/utils/request'
import type { CalcHistoryQueryParams, CalcHistory, AjaxResult, TableDataInfo } from '@/types'

// 四则运算并落库（operator: + - * /）—— POST + JSON body，运算会创建历史记录，非幂等
export function compute(operator: string, first: number, second: number): Promise<AjaxResult<CalcHistory>> {
  return request({
    url: '/aidevops/calc/compute',
    method: 'post',
    data: { operator, first, second }
  })
}

// 查询计算历史列表（分页，按当前用户隔离）
export function listHistory(query: CalcHistoryQueryParams): Promise<TableDataInfo<CalcHistory[]>> {
  return request({
    url: '/aidevops/calc/list',
    method: 'get',
    params: query
  })
}

// 批量软删除计算历史
export function delHistory(calcIds: number | number[]): Promise<AjaxResult> {
  return request({
    url: '/aidevops/calc/' + calcIds,
    method: 'delete'
  })
}
