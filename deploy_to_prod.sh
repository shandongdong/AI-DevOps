#!/bin/bash

###############################################################################
# 生产环境一键自动部署脚本（Nginx + 前端单机，后端双机滚动部署）
#
# 架构说明：
#   - 192.168.130.32  Nginx 服务器，部署前端静态资源
#   - 192.168.130.36  后端服务 1（主）
#   - 192.168.130.34  后端服务 2（backup，负载均衡备用）
#
# 功能：自动完成生产环境完整部署
#   1. 上传 nginx.conf、ruoyi-prod.conf 到 Nginx 服务器并重载
#   2. 构建并部署前端到 192.168.130.32
#   3. 构建后端，滚动部署到 192.168.130.36、192.168.130.34，并打印两台服务器部署结果
#
# 使用方法：
#   bash deploy_to_prod.sh
#
# 前置条件：
#   1. 本地已安装 Node.js、Yarn、Maven
#   2. 已配置 SSH 免密登录到 192.168.130.32、192.168.130.36、192.168.130.34
#   3. 192.168.130.32 已安装 Nginx
#   4. 192.168.130.36、192.168.130.34 已安装 Java 17
###############################################################################

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# 服务器配置
NGINX_HOST="root@192.168.130.32"       # Nginx + 前端
BACKEND_HOSTS=("root@192.168.130.36" "root@192.168.130.34")

# 路径与文件
REMOTE_NGINX_MAIN_CONF="/etc/nginx/nginx.conf"           # 主配置（替换原 nginx.conf）
REMOTE_NGINX_CONF_DIR="/etc/nginx/conf.d"
REMOTE_WEB_DIR="/usr/share/nginx/html/ruoyi-ui"
REMOTE_APP_DIR="/opt/ruoyi"
APP_NAME="ruoyi-admin.jar"
SCRIPT_NAME="ry.sh"

# 本地 Nginx 配置路径
LOCAL_NGINX_MAIN="docs/deploy/nginx/nginx.conf"
LOCAL_NGINX_RUOYI="docs/deploy/nginx/ruoyi-prod.conf"

# 前端目录名（本项目用 ruoyi-ui-vue3，Vite 工程）
FRONTEND_DIR="ruoyi-ui-vue3"

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"

LOG_DIR="$PROJECT_ROOT/logs"
mkdir -p "$LOG_DIR"
LOG_FILE="$LOG_DIR/deploy_prod_$(date +%Y%m%d_%H%M%S).log"
ERROR_COUNT=0

###############################################################################
# 工具函数
###############################################################################

print_info()    { echo -e "${BLUE}[INFO]${NC} $1" | tee -a "$LOG_FILE"; }
print_success() { echo -e "${GREEN}[SUCCESS]${NC} $1" | tee -a "$LOG_FILE"; }
print_warning() { echo -e "${YELLOW}[WARNING]${NC} $1" | tee -a "$LOG_FILE"; }
print_error()   { echo -e "${RED}[ERROR]${NC} $1" | tee -a "$LOG_FILE"; ((ERROR_COUNT++)); }

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

# 检查前置条件（工具齐全 + SSH 连接三台机器）
check_prerequisites() {
    print_info "检查前置条件..."
    local missing_tools=()
    command -v node &> /dev/null || missing_tools+=("Node.js")
    command -v yarn &> /dev/null || missing_tools+=("Yarn")
    command -v mvn  &> /dev/null || missing_tools+=("Maven")

    if [ ${#missing_tools[@]} -gt 0 ]; then
        print_error "缺少必要工具: ${missing_tools[*]}"
        return 1
    fi

    for h in "$NGINX_HOST" "${BACKEND_HOSTS[@]}"; do
        if ! ssh -o ConnectTimeout=5 -o BatchMode=yes "$h" "echo OK" &> /dev/null; then
            print_error "无法 SSH 连接 $h，请检查免密登录"
            return 1
        fi
    done
    print_success "前置条件检查通过"
    return 0
}

###############################################################################
# 部署步骤
###############################################################################

# 步骤1: 部署 Nginx 配置到 192.168.130.32（主配置 + ruoyi-prod.conf）
deploy_nginx() {
    print_info "=========================================="
    print_info "步骤 1/4: 部署 Nginx 配置到 $NGINX_HOST"
    print_info "=========================================="

    if [ ! -f "$LOCAL_NGINX_MAIN" ]; then
        print_error "主配置不存在: $LOCAL_NGINX_MAIN"
        return 1
    fi
    if [ ! -f "$LOCAL_NGINX_RUOYI" ]; then
        print_error "站点配置不存在: $LOCAL_NGINX_RUOYI"
        return 1
    fi

    if ! execute_command "scp $LOCAL_NGINX_MAIN $NGINX_HOST:$REMOTE_NGINX_MAIN_CONF" "上传 nginx.conf 并替换原文件"; then
        return 1
    fi
    if ! execute_command "scp $LOCAL_NGINX_RUOYI $NGINX_HOST:$REMOTE_NGINX_CONF_DIR/ruoyi-prod.conf" "上传 ruoyi-prod.conf 并替换原文件"; then
        return 1
    fi
    if ! execute_command "ssh $NGINX_HOST 'nginx -t'" "测试 Nginx 配置"; then
        print_error "Nginx 配置测试失败"
        return 1
    fi
    execute_command "ssh $NGINX_HOST 'nginx -s reload'" "重新加载 Nginx"
}

# 步骤2: 构建并部署前端到 192.168.130.32
deploy_frontend() {
    print_info "=========================================="
    print_info "步骤 2/4: 构建并部署前端到 $NGINX_HOST"
    print_info "=========================================="

    if [ ! -d "$FRONTEND_DIR" ]; then
        print_error "前端目录不存在: $FRONTEND_DIR"
        return 1
    fi

    cd "$FRONTEND_DIR" || { print_error "无法进入 $FRONTEND_DIR"; return 1; }

    if [ ! -d "node_modules" ]; then
        print_warning "正在安装前端依赖（yarn）..."
        if ! execute_command "yarn --registry=https://registry.npmmirror.com" "安装前端依赖"; then
            cd "$PROJECT_ROOT"
            return 1
        fi
    fi

    if ! execute_command "yarn build:prod" "构建前端（生产环境，Vite）"; then
        cd "$PROJECT_ROOT"
        return 1
    fi

    if [ ! -d "dist" ] || [ -z "$(ls -A dist 2>/dev/null)" ]; then
        print_error "前端构建失败，dist 为空"
        cd "$PROJECT_ROOT"
        return 1
    fi

    if ! execute_command "scp -r dist/* $NGINX_HOST:$REMOTE_WEB_DIR/" "上传前端文件到 $NGINX_HOST"; then
        cd "$PROJECT_ROOT"
        return 1
    fi
    execute_command "ssh $NGINX_HOST 'chmod -R 755 $REMOTE_WEB_DIR/'" "设置前端目录权限"
    cd "$PROJECT_ROOT"
    print_success "前端部署完成"
}

# 步骤3: 构建后端（仅构建一次）
build_backend() {
    print_info "=========================================="
    print_info "步骤 3/4: 构建后端"
    print_info "=========================================="

    if ! execute_command "mvn clean package -DskipTests" "Maven 构建后端"; then
        return 1
    fi

    local jar_file="ruoyi-admin/target/$APP_NAME"
    if [ ! -f "$jar_file" ]; then
        print_error "构建产物不存在: $jar_file"
        return 1
    fi
    print_success "后端构建完成"
    return 0
}

# 步骤4: 滚动部署后端到两台机器，并分别打印部署结果
deploy_backend_rolling() {
    print_info "=========================================="
    print_info "步骤 4/4: 滚动部署后端到 192.168.130.36、192.168.130.34"
    print_info "=========================================="

    local jar_file="ruoyi-admin/target/$APP_NAME"
    if [ ! -f "$jar_file" ] || [ ! -f "$SCRIPT_NAME" ]; then
        print_error "缺少 $jar_file 或 $SCRIPT_NAME"
        return 1
    fi

    local idx=0
    local server1_ok=0
    local server2_ok=0

    for host in "${BACKEND_HOSTS[@]}"; do
        idx=$((idx + 1))
        print_info "---------- 后端服务器 $idx: $host ----------"

        execute_command "ssh $host 'mkdir -p $REMOTE_APP_DIR'" "创建应用目录"
        if ! execute_command "scp $jar_file $host:$REMOTE_APP_DIR/" "上传 jar"; then
            print_error "[$host] 上传 jar 失败"
            [ $idx -eq 1 ] && server1_ok=0 || server2_ok=0
            continue
        fi
        if ! execute_command "scp $SCRIPT_NAME $host:$REMOTE_APP_DIR/" "上传 ry.sh"; then
            print_error "[$host] 上传 ry.sh 失败"
            [ $idx -eq 1 ] && server1_ok=0 || server2_ok=0
            continue
        fi
        execute_command "ssh $host 'chmod +x $REMOTE_APP_DIR/$APP_NAME $REMOTE_APP_DIR/$SCRIPT_NAME'" "设置执行权限"

        print_info "[$host] 停止旧服务..."
        ssh "$host" "bash $REMOTE_APP_DIR/$SCRIPT_NAME stop" >> "$LOG_FILE" 2>&1 || true
        sleep 2

        print_info "[$host] 启动生产环境服务..."
        local output
        output=$(ssh "$host" "bash $REMOTE_APP_DIR/$SCRIPT_NAME restart prod" 2>&1)
        echo "$output" | tee -a "$LOG_FILE"

        local this_ok=0
        if echo "$output" | grep -q "Start $APP_NAME success"; then
            print_success "[$host] 服务启动成功"
            this_ok=1
        else
            print_error "[$host] 服务启动失败，请查看该机日志: ssh $host 'tail -50 $REMOTE_APP_DIR/logs/sys-info.log'"
        fi
        [ $idx -eq 1 ] && server1_ok=$this_ok || server2_ok=$this_ok
    done

    # 明确打印两台服务器部署结果
    echo "" | tee -a "$LOG_FILE"
    print_info "---------- 两台后端服务器部署结果 ----------"
    if [ $server1_ok -eq 1 ]; then
        print_success "192.168.130.36: 部署并启动成功"
    else
        print_error "192.168.130.36: 部署或启动失败，请根据上方日志排查"
    fi
    if [ $server2_ok -eq 1 ]; then
        print_success "192.168.130.34: 部署并启动成功"
    else
        print_error "192.168.130.34: 部署或启动失败，请根据上方日志排查"
    fi
    print_info "---------- 结束 ----------"
    echo "" | tee -a "$LOG_FILE"

    if [ $server1_ok -eq 0 ] && [ $server2_ok -eq 0 ]; then
        print_error "两台后端服务器均启动异常"
        return 1
    fi
    return 0
}

show_summary() {
    print_info "=========================================="
    print_info "部署摘要"
    print_info "=========================================="

    if [ $ERROR_COUNT -eq 0 ]; then
        print_success "部署流程执行完成"
        echo ""
        print_info "访问地址:"
        echo "  前端（Nginx）: http://192.168.130.32"
        echo "  后端由 Nginx 负载均衡: http://192.168.130.32/prod-api/ （主 36，备 34）"
        echo ""
        print_info "服务器与常用命令:"
        echo "  Nginx+前端: ssh $NGINX_HOST"
        echo "  后端 1:     ssh ${BACKEND_HOSTS[0]} 'bash $REMOTE_APP_DIR/$SCRIPT_NAME status'"
        echo "  后端 2:     ssh ${BACKEND_HOSTS[1]} 'bash $REMOTE_APP_DIR/$SCRIPT_NAME status'"
        echo "  查看日志:   ssh <host> 'tail -f $REMOTE_APP_DIR/logs/sys-info.log'"
        echo "  重启生产:   ssh <host> 'bash $REMOTE_APP_DIR/$SCRIPT_NAME restart prod'"
    else
        print_error "部署过程中发生 $ERROR_COUNT 个错误，详见日志: $LOG_FILE"
        echo ""
        print_info "查看详细日志: cat $LOG_FILE"
    fi
    echo ""
    print_info "部署日志: $LOG_FILE"
}

###############################################################################
# 主流程
###############################################################################

main() {
    echo ""
    print_info "=========================================="
    print_info "AI-DevOps 生产环境一键部署（Nginx+前端@32，后端@36/34）"
    print_info "=========================================="
    echo ""
    print_info "部署时间: $(date '+%Y-%m-%d %H:%M:%S')"
    print_info "日志文件: $LOG_FILE"
    echo ""

    if ! check_prerequisites; then
        print_error "前置条件检查失败，部署终止"
        exit 1
    fi
    echo ""

    failed_step=""
    if ! deploy_nginx; then
        failed_step="Nginx 配置"
    elif ! deploy_frontend; then
        failed_step="前端部署"
    elif ! build_backend; then
        failed_step="后端构建"
    elif ! deploy_backend_rolling; then
        failed_step="后端滚动部署"
    fi

    echo ""
    show_summary

    if [ -n "$failed_step" ]; then
        print_error "部署失败步骤: $failed_step"
        exit 1
    fi
    exit 0
}

main
