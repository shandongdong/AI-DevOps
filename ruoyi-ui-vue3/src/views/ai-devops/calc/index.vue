<template>
  <div class="app-container calc-container">
    <el-card class="calc-card" shadow="hover">
      <template #header>
        <div class="calc-header">
          <span>计算器</span>
          <el-link type="primary" :href="'/calc/history'" @click.prevent="goHistory">历史记录</el-link>
        </div>
      </template>

      <!-- 显示区 -->
      <div class="calc-display">
        <div class="calc-previous">{{ previous || ' ' }}</div>
        <div class="calc-current">{{ current || '0' }}</div>
      </div>

      <!-- 按键区 -->
      <div class="calc-keys">
        <el-button class="calc-btn fn" @click="clear">C</el-button>
        <el-button class="calc-btn fn" @click="del">DEL</el-button>
        <el-button class="calc-btn fn" @click="clearCurrent">CE</el-button>
        <el-button class="calc-btn op" @click="chooseOp('/')">÷</el-button>

        <el-button class="calc-btn" @click="append('7')">7</el-button>
        <el-button class="calc-btn" @click="append('8')">8</el-button>
        <el-button class="calc-btn" @click="append('9')">9</el-button>
        <el-button class="calc-btn op" @click="chooseOp('*')">×</el-button>

        <el-button class="calc-btn" @click="append('4')">4</el-button>
        <el-button class="calc-btn" @click="append('5')">5</el-button>
        <el-button class="calc-btn" @click="append('6')">6</el-button>
        <el-button class="calc-btn op" @click="chooseOp('-')">-</el-button>

        <el-button class="calc-btn" @click="append('1')">1</el-button>
        <el-button class="calc-btn" @click="append('2')">2</el-button>
        <el-button class="calc-btn" @click="append('3')">3</el-button>
        <el-button class="calc-btn op" @click="chooseOp('+')">+</el-button>

        <el-button class="calc-btn zero" @click="append('0')">0</el-button>
        <el-button class="calc-btn" @click="appendDot">.</el-button>
        <el-button class="calc-btn op equal" :loading="loading" @click="equal">=</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts" name="Calc">
import { compute } from "@/api/ai-devops/calc"
import { useRouter } from "vue-router"

const router = useRouter()
const { proxy } = getCurrentInstance()

// 当前输入
const current = ref<string>("")
// 上一次输入（含运算符的待算式）
const previous = ref<string>("")
// 运算符（+ - * /）
const operator = ref<string>("")
// 计算中
const loading = ref<boolean>(false)

/** 追加数字 */
function append(num: string) {
  // 防止多个前导 0
  if (current.value === "0" && num !== ".") {
    current.value = num
  } else {
    current.value += num
  }
}

/** 追加小数点（仅一个） */
function appendDot() {
  if (current.value.indexOf(".") === -1) {
    current.value = (current.value || "0") + "."
  }
}

/** 清除全部 */
function clear() {
  current.value = ""
  previous.value = ""
  operator.value = ""
}

/** 清除当前输入 */
function clearCurrent() {
  current.value = ""
}

/** 退格删除 */
function del() {
  current.value = current.value.slice(0, -1)
}

/** 选择运算符 */
function chooseOp(op: string) {
  if (current.value === "" && previous.value === "") return
  // 若已有 previous 且 current 有值，先算一步（链式运算）
  if (previous.value !== "" && current.value !== "" && operator.value !== "") {
    doCompute(operator.value, parseFloat(previous.value), parseFloat(current.value), true)
    return
  }
  operator.value = op
  previous.value = current.value
  current.value = ""
  refreshDisplay()
}

/** 等号：触发最终计算 */
function equal() {
  if (current.value === "" || previous.value === "" || operator.value === "") return
  doCompute(operator.value, parseFloat(previous.value), parseFloat(current.value), false)
}

/** 调后端计算接口 */
function doCompute(op: string, first: number, second: number, chain: boolean) {
  // 防抖：计算中禁用
  if (loading.value) return
  loading.value = true
  compute(op, first, second).then(res => {
    const result = res.data?.result
    current.value = String(result)
    previous.value = ""
    operator.value = ""
  }).catch(() => {
    // ServiceException 已由 request 拦截器弹窗提示
    current.value = ""
    previous.value = ""
    operator.value = ""
  }).finally(() => {
    loading.value = false
  })
}

/** 刷新待算式显示 */
function refreshDisplay() {
  if (previous.value && operator.value) {
    const symbolMap: Record<string, string> = { "+": "+", "-": "-", "*": "×", "/": "÷" }
    previous.value = previous.value + " " + (symbolMap[operator.value] || operator.value)
  }
}

/** 跳转历史记录页 */
function goHistory() {
  router.push("/calc/history")
}
</script>

<style scoped lang="scss">
.calc-container {
  display: flex;
  justify-content: center;
  align-items: flex-start;
}

.calc-card {
  width: 420px;
}

.calc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.calc-display {
  background-color: #333;
  color: #fff;
  border-radius: 8px;
  padding: 16px 20px;
  margin-bottom: 16px;
  text-align: right;
  min-height: 90px;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
}

.calc-previous {
  font-size: 16px;
  color: #bbb;
  min-height: 22px;
}

.calc-current {
  font-size: 36px;
  font-weight: 600;
  word-break: break-all;
}

.calc-keys {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
}

.calc-btn {
  height: 60px;
  font-size: 20px;
  margin: 0;
  border-radius: 8px;
}

.calc-btn.zero {
  grid-column: span 1;
}

.calc-btn.equal {
  grid-column: span 2;
}

.calc-btn.op {
  background-color: #f0a020;
  color: #fff;
  border-color: #f0a020;
}

.calc-btn.op:hover {
  background-color: #f5b94a;
  border-color: #f5b94a;
}

.calc-btn.fn {
  background-color: #409eff;
  color: #fff;
  border-color: #409eff;
}

.calc-btn.fn:hover {
  background-color: #66b1ff;
  border-color: #66b1ff;
}
</style>
