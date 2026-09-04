import type { PageDomain, BaseEntity } from "../common";

/** 计算历史分页查询参数 */
export interface CalcHistoryQueryParams extends PageDomain {
  /** 运算符（+ - * /） */
  operator?: string;
  /** 查询起始时间 */
  beginTime?: string;
  /** 查询结束时间 */
  endTime?: string;
}

/** 计算历史记录 */
export interface CalcHistory extends BaseEntity {
  /** 记录ID */
  calcId?: number;
  /** 第一个数 */
  firstNumber?: number;
  /** 第二个数 */
  secondNumber?: number;
  /** 运算符（+ - * /） */
  operator?: string;
  /** 运算结果 */
  result?: number;
  /** 状态（0正常 1停用） */
  status?: '0' | '1';
  /** 删除标志（0存在 2删除） */
  delFlag?: '0' | '2';
}
