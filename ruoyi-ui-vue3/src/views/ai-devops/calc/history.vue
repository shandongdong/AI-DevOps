<template>
  <div class="app-container">
    <!-- 查询条件 -->
    <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
      <el-form-item label="运算符" prop="operator">
        <el-select v-model="queryParams.operator" placeholder="运算符" clearable style="width: 160px">
          <el-option label="加（+）" value="+" />
          <el-option label="减（-）" value="-" />
          <el-option label="乘（×）" value="*" />
          <el-option label="除（÷）" value="/" />
        </el-select>
      </el-form-item>
      <el-form-item label="运算时间">
        <el-date-picker v-model="dateRange" value-format="YYYY-MM-DD" type="daterange"
          range-separator="-" start-placeholder="开始日期" end-placeholder="结束日期" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 操作按钮 -->
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete()">删除</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button icon="Refresh" @click="getList">刷新</el-button>
      </el-col>
      <right-toolbar v-model:showSearch="showSearch" @queryTable="getList"></right-toolbar>
    </el-row>

    <!-- 列表 -->
    <el-table v-loading="loading" :data="calcHistoryData" @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="55" align="center" />
      <el-table-column label="记录ID" align="center" prop="calcId" width="90" />
      <el-table-column label="第一个数" align="center" prop="firstNumber" />
      <el-table-column label="运算符" align="center" prop="operator" width="80">
        <template #default="scope">
          {{ formatOperator(scope.row.operator) }}
        </template>
      </el-table-column>
      <el-table-column label="第二个数" align="center" prop="secondNumber" />
      <el-table-column label="结果" align="center" prop="result">
        <template #default="scope">
          <span style="color: #67c23a; font-weight: 600;">{{ scope.row.result }}</span>
        </template>
      </el-table-column>
      <el-table-column label="运算时间" align="center" prop="createTime" width="180" />
      <el-table-column label="操作" align="center" width="120">
        <template #default="scope">
          <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <pagination
      v-show="total > 0"
      :total="total"
      v-model:page="queryParams.pageNum"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </div>
</template>

<script setup lang="ts" name="CalcHistory">
import { listHistory, delHistory } from "@/api/ai-devops/calc"
import type { CalcHistory, CalcHistoryQueryParams } from '@/types'

const { proxy } = getCurrentInstance()

const calcHistoryData = ref<CalcHistory[]>([])
const loading = ref<boolean>(true)
const showSearch = ref<boolean>(true)
const total = ref<number>(0)
const ids = ref<number[]>([])
const multiple = ref<boolean>(true)
const dateRange = ref<string[]>([])

const queryParams = reactive<CalcHistoryQueryParams>({
  pageNum: 1,
  pageSize: 10,
  operator: undefined
})

/** 运算符显示符号 */
function formatOperator(op?: string): string {
  const map: Record<string, string> = { "+": "+", "-": "-", "*": "×", "/": "÷" }
  return op ? (map[op] || op) : ""
}

/** 查询列表 */
function getList() {
  loading.value = true
  listHistory(proxy.addDateRange(queryParams, dateRange.value)).then(res => {
    calcHistoryData.value = res.rows
    total.value = res.total
  }).finally(() => {
    loading.value = false
  })
}

/** 搜索 */
function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

/** 重置 */
function resetQuery() {
  dateRange.value = []
  proxy.resetForm("queryRef")
  handleQuery()
}

/** 多选 */
function handleSelectionChange(selection: CalcHistory[]) {
  ids.value = selection.map(item => item.calcId!)
  multiple.value = !selection.length
}

/** 软删除（AC-2.2） */
function handleDelete(row?: CalcHistory) {
  const calcIds = row ? [row.calcId] : ids.value
  proxy.$modal.confirm('确认删除选中的 ' + calcIds.length + ' 条计算记录？').then(() => {
    return delHistory(calcIds)
  }).then(() => {
    getList()
    proxy.$modal.msgSuccess("删除成功")
  }).catch(() => {})
}

onMounted(() => {
  getList()
})
</script>
