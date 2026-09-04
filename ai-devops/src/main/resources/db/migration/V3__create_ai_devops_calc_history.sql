-- Active: 1776157699775@@192.168.30.41@3306@ai-devops
use `ai-devops`;
-- 计算器历史记录表（ai-devops 业务表，V3 起编号）
-- 字段注释之间不能有空行，否则 SQL 执行可能出错
drop table if exists ai_devops_calc_history;
create table ai_devops_calc_history (
  calc_id        bigint(20)   not null auto_increment    comment '记录ID',
  first_number   double(16,4)                            comment '第一个数',
  second_number  double(16,4)                            comment '第二个数',
  operator       char(1)                                 comment '运算符(+ - * /)',
  result         double(16,4)                            comment '运算结果',
  status         char(1)       default '0'               comment '状态（0正常 1停用）',
  del_flag       char(1)       default '0'               comment '删除标志（0存在 2删除）',
  create_by      varchar(64)   default ''                comment '创建者',
  create_time    datetime                                comment '创建时间',
  update_by      varchar(64)   default ''                comment '更新者',
  update_time    datetime                                comment '更新时间',
  remark         varchar(500) default null              comment '备注',
  primary key (calc_id)
) engine=innodb auto_increment=1 comment='计算器历史记录表';

-- 菜单与权限（sys_menu，菜单数据驱动，constitution 原则 III）
-- 计算器作为一级目录挂根目录（parent_id=0，order_num=5 排在系统工具之后）
-- M=目录/C=菜单/F=按钮; visible 0=显示; status 0=正常
-- sys_menu 20 列顺序（对齐 V1 官方格式）：
--   menu_id, menu_name, parent_id, order_num, path, component, query, route_name,
--   is_frame, is_cache, menu_type, visible, status, perms, icon,
--   create_by, create_time, update_by, update_time, remark
-- 注意：path 不含模块前缀（前端拼成 /calc）；C 菜单 component 指向 views 下组件路径（如 ai-devops/calc/index）；
--       perms 填权限标识、icon 填 SVG 图标名（取 src/assets/icons/svg/ 已有图标，如 number/edit/list）

-- 先删旧菜单再插（避免重复执行报唯一键冲突）
delete from sys_menu where menu_id in (2020, 2021, 2022, 2023, 2024, 2025);

-- 计算器目录（M，一级目录挂根目录）
insert into sys_menu values('2020', '计算器', '0', '5', 'calc', null, '', '', 1, 0, 'M', '0', '0', '', 'number', 'admin', sysdate(), '', null, '计算器目录');

-- 计算器页面菜单（C）
insert into sys_menu values('2021', '计算器', '2020', '1', 'index', 'ai-devops/calc/index', '', '', 1, 0, 'C', '0', '0', 'aidevops:calc:compute', 'edit', 'admin', sysdate(), '', null, '计算器页面');

-- 计算按钮（F）
insert into sys_menu values('2022', '计算', '2021', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'aidevops:calc:compute', '#', 'admin', sysdate(), '', null, '');

-- 历史记录页面菜单（C）
insert into sys_menu values('2023', '历史记录', '2020', '2', 'history', 'ai-devops/calc/history', '', '', 1, 0, 'C', '0', '0', 'aidevops:calc:list', 'list', 'admin', sysdate(), '', null, '历史记录页面');

-- 历史查询按钮（F）
insert into sys_menu values('2024', '查询', '2023', '1', '', '', '', '', 1, 0, 'F', '0', '0', 'aidevops:calc:list', '#', 'admin', sysdate(), '', null, '');

-- 历史删除按钮（F）
insert into sys_menu values('2025', '删除', '2023', '2', '', '', '', '', 1, 0, 'F', '0', '0', 'aidevops:calc:remove', '#', 'admin', sysdate(), '', null, '');
