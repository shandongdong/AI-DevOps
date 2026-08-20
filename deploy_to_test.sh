#!/bin/bash

###############################################################################
# 测试环境一键自动部署脚本
#
# 测试环境为单机部署：前端、后端、Redis、MySQL8、Nginx 均在 192.168.130.41
#
# 功能：自动完成测试环境的完整部署流程
# 包括：Nginx 配置、前端构建部署、后端构建部署、服务启动
#
# 使用方法：
#   bash deploy_to_test.sh
#
# 前置条件：
#   1. 本地已安装 Node.js、Yarn、Maven
#   2. 已配置 SSH 免密登录到 192.168.130.41
#   3. 服务器 192.168.130.41 已安装 Java 17、Nginx、Redis、MySQL8
###############################################################################

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 配置信息
REMOTE_HOST="root@192.168.130.41"
REMOTE_APP_DIR="/opt/ruoyi"
REMOTE_NGINX_DIR="/etc/nginx/conf.d"
REMOTE_WEB_DIR="/usr/share/nginx/html/ruoyi-ui"
APP_NAME="ruoyi-admin.jar"
SCRIPT_NAME="ry.sh"

# 前端目录名（本项目用 ruoyi-ui-vue3，Vite 工程）
FRONTEND_DIR="ruoyi-ui-vue3"

# 项目根目录（绝对路径）
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"

# 日志文件（使用绝对路径，避免 cd 后路径失效）
LOG_DIR="$PROJECT_ROOT/logs"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/deploy_test_$(date +%Y%m%d_%H%M%S).log"

# 错误计数
ERROR_COUNT=0

###############################################################################
# 工具函数
###############################################################################

# 打印信息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1" | tee -a "$LOG_FILE"
}

# 打印成功
print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1" | tee -a "$LOG_FILE"
}

# 打印警告
print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1" | tee -a "$LOG_FILE"
}

# 打印错误
print_error() {
    echo -e "${RED}[ERROR]${NC} $1" | tee -a "$LOG_FILE"
    ((ERROR_COUNT++))
}

# 执行命令并检查结果
execute_command() {
    local cmd="$1"
    local desc="$2"

    print_info "$desc"
    if eval "$cmd" >> "$LOG_FILE" 2>&1; then
        print_success "$desc - 完成"
        return 0
    else
        print_error "$desc - 失败"
        return 1
    fi
}

# 检查前置条件
check_prerequisites() {
    print_info "检查前置条件..."

    local missing_tools=()

    # 检查 Node.js
    if ! command -v node &> /dev/null; then
        missing_tools+=("Node.js")
    fi

    # 检查 Yarn
    if ! command -v yarn &> /dev/null; then
        missing_tools+=("Yarn")
    fi

    # 检查 Maven
    if ! command -v mvn &> /dev/null; then
        missing_tools+=("Maven")
    fi

    # 检查 SSH 连接
    if ! ssh -o ConnectTimeout=5 -o BatchMode=yes "$REMOTE_HOST" "echo 'SSH连接测试'" &> /dev/null; then
        print_error "无法连接到 $REMOTE_HOST，请检查 SSH 配置"
        return 1
    fi

    if [ ${#missing_tools[@]} -gt 0 ]; then
        print_error "缺少必要的工具: ${missing_tools[*]}"
        return 1
    fi

    print_success "前置条件检查通过"
    return 0
}

###############################################################################
# 部署步骤
###############################################################################

# 步骤1: 部署 Nginx 配置
deploy_nginx() {
    print_info "=========================================="
    print_info "步骤 1/4: 部署 Nginx 配置"
    print_info "=========================================="

    local nginx_conf="docs/deploy/nginx/ruoyi-staging.conf"

    if [ ! -f "$nginx_conf" ]; then
        print_error "Nginx 配置文件不存在: $nginx_conf"
        return 1
    fi

    if execute_command "scp $nginx_conf $REMOTE_HOST:$REMOTE_NGINX_DIR/ruoyi-staging.conf" "上传 Nginx 配置文件"; then
        if execute_command "ssh $REMOTE_HOST 'nginx -t'" "测试 Nginx 配置"; then
            execute_command "ssh $REMOTE_HOST 'nginx -s reload'" "重新加载 Nginx 配置"
        else
            print_error "Nginx 配置测试失败，请检查配置文件"
            return 1
        fi
    else
        return 1
    fi
}

# 步骤2: 构建和部署前端
deploy_frontend() {
    print_info "=========================================="
    print_info "步骤 2/4: 构建和部署前端"
    print_info "=========================================="

    if [ ! -d "$FRONTEND_DIR" ]; then
        print_error "前端目录不存在: $FRONTEND_DIR"
        return 1
    fi

    cd "$FRONTEND_DIR" || {
        print_error "无法进入前端目录"
        return 1
    }

    # 检查 node_modules
    if [ ! -d "node_modules" ]; then
        print_warning "node_modules 不存在，正在安装依赖（yarn）..."
        if ! execute_command "yarn --registry=https://registry.npmmirror.com" "安装前端依赖"; then
            cd "$PROJECT_ROOT"
            return 1
        fi
    fi

    # 构建前端（Vite 测试环境构建）
    if ! execute_command "yarn build:stage" "构建前端（测试环境，Vite）"; then
        cd "$PROJECT_ROOT"
        return 1
    fi

    # 检查构建结果
    if [ ! -d "dist" ] || [ -z "$(ls -A dist)" ]; then
        print_error "前端构建失败，dist 目录为空"
        cd "$PROJECT_ROOT"
        return 1
    fi

    # 上传前端文件
    if execute_command "scp -r dist/* $REMOTE_HOST:$REMOTE_WEB_DIR/" "上传前端文件"; then
        execute_command "ssh $REMOTE_HOST 'chmod -R 755 $REMOTE_WEB_DIR/'" "设置前端文件权限"
    else
        cd "$PROJECT_ROOT"
        return 1
    fi

    cd "$PROJECT_ROOT"
    print_success "前端部署完成"
}

# 步骤3: 构建和部署后端
deploy_backend() {
    print_info "=========================================="
    print_info "步骤 3/4: 构建和部署后端"
    print_info "=========================================="

    # 构建后端
    if ! execute_command "mvn clean package -DskipTests" "构建后端项目"; then
        return 1
    fi

    # 检查构建结果
    local jar_file="ruoyi-admin/target/$APP_NAME"
    if [ ! -f "$jar_file" ]; then
        print_error "后端构建失败，jar 文件不存在: $jar_file"
        return 1
    fi

    # 创建远程目录
    execute_command "ssh $REMOTE_HOST 'mkdir -p $REMOTE_APP_DIR'" "创建远程应用目录"

    # 上传 jar 文件
    if ! execute_command "scp $jar_file $REMOTE_HOST:$REMOTE_APP_DIR/" "上传后端 jar 文件"; then
        return 1
    fi

    # 上传启动脚本
    if [ ! -f "$SCRIPT_NAME" ]; then
        print_error "启动脚本不存在: $SCRIPT_NAME"
        return 1
    fi

    if ! execute_command "scp $SCRIPT_NAME $REMOTE_HOST:$REMOTE_APP_DIR/" "上传启动脚本"; then
        return 1
    fi

    # 设置执行权限
    execute_command "ssh $REMOTE_HOST 'chmod +x $REMOTE_APP_DIR/$APP_NAME $REMOTE_APP_DIR/$SCRIPT_NAME'" "设置文件执行权限"

    print_success "后端部署完成"
}

# 步骤4: 启动服务
start_service() {
    print_info "=========================================="
    print_info "步骤 4/4: 启动后端服务"
    print_info "=========================================="

    print_info "停止旧服务（如果存在）..."
    ssh "$REMOTE_HOST" "bash $REMOTE_APP_DIR/$SCRIPT_NAME stop" >> "$LOG_FILE" 2>&1 || true

    sleep 2

    print_info "启动测试环境服务..."
    local output
    output=$(ssh "$REMOTE_HOST" "bash $REMOTE_APP_DIR/$SCRIPT_NAME restart test" 2>&1)
    echo "$output" | tee -a "$LOG_FILE"

    # 检查启动结果
    if echo "$output" | grep -q "Start $APP_NAME success"; then
        print_success "服务启动成功"

        # 等待服务完全启动
        print_info "等待服务启动（5秒）..."
        sleep 5

        # 验证服务状态
        local status_output
        status_output=$(ssh "$REMOTE_HOST" "bash $REMOTE_APP_DIR/$SCRIPT_NAME status" 2>&1)
        echo "$status_output" | tee -a "$LOG_FILE"

        if echo "$status_output" | grep -q "is running"; then
            print_success "服务运行正常"
            return 0
        else
            print_warning "服务状态检查异常，请手动验证"
            return 1
        fi
    else
        print_error "服务启动失败，请检查日志"
        print_info "查看远程日志: ssh $REMOTE_HOST 'tail -50 $REMOTE_APP_DIR/logs/sys-info.log'"
        return 1
    fi
}

# 显示部署摘要
show_summary() {
    print_info "=========================================="
    print_info "部署摘要"
    print_info "=========================================="

    if [ $ERROR_COUNT -eq 0 ]; then
        print_success "部署完成！所有步骤执行成功"
        echo ""
        print_info "访问地址:"
        echo "  前端: http://192.168.130.41"
        echo "  后端: http://192.168.130.41:8080"
        echo "  健康检查: http://192.168.130.41:8080/aidevops/health"
        echo ""
        print_info "常用命令:"
        echo "  查看服务状态: ssh $REMOTE_HOST 'bash $REMOTE_APP_DIR/$SCRIPT_NAME status'"
        echo "  查看服务日志: ssh $REMOTE_HOST 'tail -f $REMOTE_APP_DIR/logs/sys-info.log'"
        echo "  重启服务: ssh $REMOTE_HOST 'bash $REMOTE_APP_DIR/$SCRIPT_NAME restart test'"
        echo "  停止服务: ssh $REMOTE_HOST 'bash $REMOTE_APP_DIR/$SCRIPT_NAME stop'"
    else
        print_error "部署过程中发生 $ERROR_COUNT 个错误，请检查日志: $LOG_FILE"
        echo ""
        print_info "查看详细日志: cat $LOG_FILE"
    fi

    echo ""
    print_info "部署日志已保存到: $LOG_FILE"
}

###############################################################################
# 主函数
###############################################################################

main() {
    echo ""
    print_info "=========================================="
    print_info "AI-DevOps 测试环境一键自动部署"
    print_info "=========================================="
    echo ""
    print_info "部署目标: $REMOTE_HOST"
    print_info "部署时间: $(date '+%Y-%m-%d %H:%M:%S')"
    print_info "日志文件: $LOG_FILE"
    echo ""

    # 检查前置条件
    if ! check_prerequisites; then
        print_error "前置条件检查失败，部署终止"
        exit 1
    fi

    echo ""

    # 执行部署步骤
    local failed_step=""

    if ! deploy_nginx; then
        failed_step="Nginx配置"
    elif ! deploy_frontend; then
        failed_step="前端部署"
    elif ! deploy_backend; then
        failed_step="后端部署"
    elif ! start_service; then
        failed_step="服务启动"
    fi

    echo ""

    # 显示部署摘要
    show_summary

    # 如果有失败的步骤，返回非零退出码
    if [ -n "$failed_step" ]; then
        print_error "部署失败在步骤: $failed_step"
        exit 1
    fi

    exit 0
}

# 执行主函数
main
