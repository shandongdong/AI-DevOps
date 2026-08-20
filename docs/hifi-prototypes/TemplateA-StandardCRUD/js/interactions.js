/**
 * 高保真交互原型 - 标准CRUD管理页 - 共享交互逻辑
 * 业务示例：用户管理（通用模版）
 */

/* ============================================================
 * 字典 / 选项数据
 * ============================================================ */
const genderOptions = [
  { label: '男', value: 'M' },
  { label: '女', value: 'F' }
];

const statusOptions = [
  { label: '正常', value: '0' },
  { label: '停用', value: '1' }
];

const deptOptions = [
  { label: '技术部', value: 'TECH' },
  { label: '产品部', value: 'PROD' },
  { label: '运营部', value: 'OPS' },
  { label: '市场部', value: 'MKT' },
  { label: '财务部', value: 'FIN' },
  { label: '人事部', value: 'HR' }
];

const roleOptions = [
  { label: '管理员', value: 'admin' },
  { label: '普通用户', value: 'user' },
  { label: '审核员', value: 'auditor' },
  { label: '运营专员', value: 'operator' },
  { label: '访客', value: 'guest' }
];

/* ============================================================
 * 30 条用户模拟数据（循环生成 + 多样性）
 * ============================================================ */
const _firstNames = ['张', '李', '王', '刘', '陈', '杨', '黄', '赵', '吴', '周', '徐', '孙', '马', '朱', '胡'];
const _lastNames = ['伟', '芳', '娜', '敏', '静', '丽', '强', '磊', '军', '洋', '勇', '艳', '杰', '涛', '明'];
const _domains = ['example.com', 'company.cn', 'corp.io', 'mail.com'];
const _roleCombos = [
  ['admin'],
  ['user'],
  ['auditor', 'user'],
  ['operator'],
  ['user', 'guest'],
  ['admin', 'auditor']
];

function _pad(n, len) {
  var s = String(n);
  while (s.length < len) s = '0' + s;
  return s;
}

function _randPhone(seed) {
  var prefix = ['138', '139', '186', '188', '152', '155', '177'][seed % 7];
  var tail = _pad((seed * 7919 + 1234) % 100000000, 8);
  return prefix + tail;
}

function _maskPhone(phone) {
  if (!phone || phone.length < 11) return phone;
  return phone.substring(0, 3) + '****' + phone.substring(7);
}

const mockTableData = (function () {
  var list = [];
  for (var i = 0; i < 30; i++) {
    var dept = deptOptions[i % deptOptions.length];
    var gender = genderOptions[i % 2];
    var status = i % 7 === 0 ? statusOptions[1] : statusOptions[0]; // 部分停用
    var firstName = _firstNames[i % _firstNames.length];
    var lastName = _lastNames[(i * 3) % _lastNames.length];
    var name = firstName + lastName;
    var phone = _randPhone(i + 1);
    var email = ('user' + (i + 1)) + '@' + _domains[i % _domains.length];
    var roles = _roleCombos[i % _roleCombos.length];
    var roleLabels = roles.map(function (r) {
      var hit = roleOptions.find(function (o) { return o.value === r; });
      return hit ? hit.label : r;
    });
    var birthYear = 1985 + (i % 15);
    var birthMonth = _pad(((i * 5) % 12) + 1, 2);
    var birthDay = _pad(((i * 7) % 28) + 1, 2);
    var entryYear = 2018 + (i % 7);
    var entryMonth = _pad(((i * 3) % 12) + 1, 2);
    var entryDay = _pad(((i * 11) % 28) + 1, 2);
    var createMonth = _pad(((i * 2) % 6) + 1, 2);
    var createDay = _pad(((i * 13) % 28) + 1, 2);
    var createHour = _pad((i * 3) % 24, 2);
    var createMin = _pad((i * 7) % 60, 2);

    list.push({
      id: 1001 + i,
      userCode: dept.value + '-' + _pad(i + 1, 4),
      name: name,
      phone: phone,
      phoneMasked: _maskPhone(phone),
      email: email,
      gender: gender.value,
      genderLabel: gender.label,
      deptId: dept.value,
      deptLabel: dept.label,
      roles: roles,
      roleLabels: roleLabels,
      status: status.value,
      statusLabel: status.label,
      statusType: status.value === '0' ? 'success' : 'info',
      enabled: status.value === '0',
      birthDate: birthYear + '-' + birthMonth + '-' + birthDay,
      entryDate: entryYear + '-' + entryMonth + '-' + entryDay,
      createTime: '2026-' + createMonth + '-' + createDay + ' ' + createHour + ':' + createMin + ':00'
    });
  }
  return list;
})();

/* ============================================================
 * 操作历史（详情页）
 * 共 35 条：34 条修改 + 1 条创建（最末位），按时间倒序排列
 * ============================================================ */
const operationHistory = (function () {
  var operators = ['张三', '李四', '王五', '赵六', '管理员', '孙七', '周八', '吴九'];
  var changes = [
    { field: '姓名', oldValue: '张伟', newValue: '张磊' },
    { field: '性别', oldValue: '女', newValue: '男' },
    { field: '部门', oldValue: '技术部', newValue: '产品部' },
    { field: '手机号', oldValue: '138****1234', newValue: '139****5678' },
    { field: '角色', oldValue: '普通用户', newValue: '审核员' },
    { field: '状态', oldValue: '停用', newValue: '正常' },
    { field: '邮箱', oldValue: 'zhangwei@example.com', newValue: 'zhangwei@company.cn' },
    { field: '性别', oldValue: '男', newValue: '女' },
    { field: '部门', oldValue: '产品部', newValue: '运营部' },
    { field: '手机号', oldValue: '186****8888', newValue: '188****9999' },
    { field: '角色', oldValue: '审核员', newValue: '管理员' },
    { field: '状态', oldValue: '正常', newValue: '停用' },
    { field: '邮箱', oldValue: 'old@mail.com', newValue: 'new@corp.io' },
    { field: '出生日期', oldValue: '1990-05-12', newValue: '1991-08-20' },
    { field: '入职日期', oldValue: '2020-03-01', newValue: '2020-06-15' },
    { field: '是否启用', oldValue: '停用', newValue: '启用' },
    { field: '部门', oldValue: '运营部', newValue: '市场部' },
    { field: '角色', oldValue: '运营专员', newValue: '普通用户' },
    { field: '是否启用', oldValue: '启用', newValue: '停用' },
    { field: '姓名', oldValue: '李芳', newValue: '李娜' },
    { field: '邮箱', oldValue: 'user12@example.com', newValue: 'user12@company.cn' },
    { field: '部门', oldValue: '市场部', newValue: '财务部' },
    { field: '手机号', oldValue: '152****3333', newValue: '155****6666' },
    { field: '状态', oldValue: '停用', newValue: '正常' }
  ];

  var pad2 = function (n) { return n < 10 ? '0' + n : '' + n; };
  var fmt = function (d) {
    return d.getFullYear() + '-' + pad2(d.getMonth() + 1) + '-' + pad2(d.getDate())
      + ' ' + pad2(d.getHours()) + ':' + pad2(d.getMinutes()) + ':' + pad2(d.getSeconds());
  };

  var list = [];
  // 起点：2026-06-08 18:32:18，向前递减生成 34 条修改记录
  var cursor = new Date(2026, 5, 8, 18, 32, 18).getTime();
  for (var i = 0; i < 34; i++) {
    var change = changes[i % changes.length];
    var op = operators[(i * 3 + 1) % operators.length];
    list.push({
      time: fmt(new Date(cursor)),
      operator: op,
      type: 'update',
      field: change.field,
      oldValue: change.oldValue,
      newValue: change.newValue
    });
    // 每条之间间隔：12 ~ 47 小时不等，外加分钟扰动，确保时间严格递减且跨度合理
    var stepHours = 12 + (i * 7) % 36;
    var stepMinutes = (i * 13) % 53;
    cursor -= (stepHours * 60 + stepMinutes) * 60 * 1000;
  }
  // 末位：创建记录
  list.push({
    time: '2026-01-01 09:00:00',
    operator: '管理员',
    type: 'create'
  });
  return list;
})();

/* ============================================================
 * 表单字段定义 - 基本信息
 * type: input | select | multiselect | date | switch | readonly
 * ============================================================ */
const basicFields = [
  {
    label: '用户编号',
    prop: 'userCode',
    type: 'readonly',
    required: false,
    placeholder: '系统自动生成',
    autoTag: '系统自动生成',
    help: '编号 = 部门前缀 + 4 位序号，由系统自动生成，不可编辑。'
  },
  {
    label: '姓名',
    prop: 'name',
    type: 'input',
    required: true,
    placeholder: '请输入真实姓名',
    maxlength: 20
  },
  {
    label: '手机号',
    prop: 'phone',
    type: 'input',
    required: true,
    placeholder: '请输入 11 位手机号',
    maxlength: 11,
    help: '仅支持中国大陆手机号，将作为登录账号使用。'
  },
  {
    label: '邮箱',
    prop: 'email',
    type: 'input',
    required: false,
    placeholder: '请输入邮箱地址',
    maxlength: 50
  },
  {
    label: '性别',
    prop: 'gender',
    type: 'select',
    required: true,
    options: genderOptions
  },
  {
    label: '部门',
    prop: 'deptId',
    type: 'select',
    required: true,
    options: deptOptions,
    help: '选择部门后，将基于部门前缀重新生成用户编号。'
  },
  {
    label: '角色',
    prop: 'roles',
    type: 'multiselect',
    required: true,
    options: roleOptions,
    dependsOn: 'deptId',
    help: '一个用户可分配多个角色，权限取并集。需先选择部门后才能选择角色。'
  },
  {
    label: '状态',
    prop: 'status',
    type: 'select',
    required: true,
    options: statusOptions
  },
  {
    label: '出生日期',
    prop: 'birthDate',
    type: 'date',
    required: false,
    placeholder: '选择出生日期'
  },
  {
    label: '入职日期',
    prop: 'entryDate',
    type: 'date',
    required: true,
    placeholder: '选择入职日期'
  },
  {
    label: '是否启用',
    prop: 'enabled',
    type: 'switch',
    required: false,
    help: '关闭后该用户将无法登录系统。'
  },
  {
    label: '创建时间',
    prop: 'createTime',
    type: 'readonly',
    required: false,
    placeholder: '系统自动填充',
    autoTag: '系统自动生成'
  }
];

/* ============================================================
 * 导入结果模拟数据
 * ============================================================ */
const importSuccessList = [
  { index: 1, name: '王五', deptLabel: '技术部', actionType: '新增' },
  { index: 2, name: '张三', deptLabel: '产品部', actionType: '更新' },
  { index: 3, name: '陈七', deptLabel: '运营部', actionType: '新增' }
];

const importFailedList = [
  { index: 1, name: '', deptLabel: '技术部', errorMessage: '姓名不能为空' },
  { index: 2, name: '李四', deptLabel: '未知部门', errorMessage: '部门不存在于业务字典' }
];

/* ============================================================
 * 通用方法
 * ============================================================ */
function copyToClipboard(text, vm) {
  if (navigator.clipboard) {
    navigator.clipboard.writeText(text).then(function () {
      vm.$message.success('已复制：' + text);
    }).catch(function () {
      vm.$message.warning('复制失败，请手动选取');
    });
  } else {
    var textarea = document.createElement('textarea');
    textarea.value = text;
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
    vm.$message.success('已复制：' + text);
  }
}

/**
 * 模拟跳转到帮助页面（点击 ? 图标）
 */
function openHelpPage(topic) {
  var url = 'about:blank#help-' + encodeURIComponent(topic || 'general');
  window.open(url, '_blank');
}
