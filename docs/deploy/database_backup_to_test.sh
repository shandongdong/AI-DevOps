#!/bin/bash
# MySQL 数据库自动备份脚本（测试环境）
# 版本: 1.1
# 适用环境: MySQL 8.0+, Ubuntu 22.04 Server
# 功能: 自动备份所有数据库，保留指定数量的备份文件，记录错误日志
#
# 安全说明：密码通过环境变量传入，不写明文默认值。
# 执行前必须 export MYSQL_PASSWORD=xxx 或在 crontab 环境配置，否则脚本中止。

# ==================== 配置项（可根据需要修改）====================
# 备份文件保存路径（测试环境默认：/root/backups/mysql_backups/test）
BACKUP_DIR="${BACKUP_DIR:-/root/backups/mysql_backups/test}"

# 备份文件保留数量（默认：30个）
KEEP_FILES="${KEEP_FILES:-30}"

# MySQL 数据库连接信息（密码强制环境变量，不设默认值）
MYSQL_USER="${MYSQL_USER:-root}"
if [ -z "${MYSQL_PASSWORD+x}" ]; then
    echo "[$(date +'%Y-%m-%d %H%M%S')] 错误: 未设置 MYSQL_PASSWORD 环境变量，出于安全考虑脚本不内嵌密码" >&2
    echo "请执行: export MYSQL_PASSWORD=你的密码" >&2
    exit 1
fi

# 备份文件命名格式
BACKUP_FILE_PREFIX="all_databases"
BACKUP_FILE_EXT=".sql"

# ==================== 脚本开始执行 ====================

# 获取当前时间戳（格式：YYYY-MM-DD_HHMMSS，时分秒不用冒号分隔，避免转义）
TIMESTAMP=$(date +%Y-%m-%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/${BACKUP_FILE_PREFIX}_${TIMESTAMP}${BACKUP_FILE_EXT}"
ERROR_LOG="${BACKUP_DIR}/${BACKUP_FILE_PREFIX}_${TIMESTAMP}.err"

# 创建备份目录（如果不存在）
mkdir -p "$BACKUP_DIR" || {
    echo "[$(date +'%Y-%m-%d %H%M%S')] 错误: 无法创建备份目录 $BACKUP_DIR" >&2
    exit 1
}

# 检查 mysqldump 命令是否存在
if ! command -v mysqldump &> /dev/null; then
    echo "[$(date +'%Y-%m-%d %H%M%S')] 错误: 未找到 mysqldump 命令，请确保 MySQL 客户端已安装" | tee -a "$ERROR_LOG"
    exit 1
fi

# 检查备份目录是否可写
if [ ! -w "$BACKUP_DIR" ]; then
    echo "[$(date +'%Y-%m-%d %H%M%S')] 错误: 备份目录 $BACKUP_DIR 不可写" | tee -a "$ERROR_LOG"
    exit 1
fi

# 开始备份
echo "[$(date +'%Y-%m-%d %H%M%S')] 开始备份数据库..."
echo "[$(date +'%Y-%m-%d %H%M%S')] 备份文件: $BACKUP_FILE"

# 执行备份命令
mysqldump -u "$MYSQL_USER" -p"$MYSQL_PASSWORD" --all-databases > "$BACKUP_FILE" 2>> "$ERROR_LOG"

# 检查备份是否成功
BACKUP_EXIT_CODE=$?
if [ $BACKUP_EXIT_CODE -eq 0 ]; then
    # 检查备份文件是否生成且大小大于0
    if [ -f "$BACKUP_FILE" ] && [ -s "$BACKUP_FILE" ]; then
        BACKUP_SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
        echo "[$(date +'%Y-%m-%d %H%M%S')] 备份成功完成！"
        echo "[$(date +'%Y-%m-%d %H%M%S')] 备份文件大小: $BACKUP_SIZE"

        # 删除错误日志文件（如果备份成功，错误日志为空或只包含警告）
        if [ -f "$ERROR_LOG" ]; then
            if [ ! -s "$ERROR_LOG" ] || ! grep -qi "error" "$ERROR_LOG"; then
                rm -f "$ERROR_LOG"
            fi
        fi
    else
        echo "[$(date +'%Y-%m-%d %H%M%S')] 错误: 备份文件生成失败或文件为空" | tee -a "$ERROR_LOG"
        exit 1
    fi
else
    echo "[$(date +'%Y-%m-%d %H%M%S')] 错误: 备份失败，退出代码: $BACKUP_EXIT_CODE" | tee -a "$ERROR_LOG"
    echo "[$(date +'%Y-%m-%d %H%M%S')] 请查看错误日志: $ERROR_LOG" | tee -a "$ERROR_LOG"
    exit 1
fi

# 清理旧备份文件（保留最新的 KEEP_FILES 个文件）
echo "[$(date +'%Y-%m-%d %H%M%S')] 开始清理旧备份文件（保留最新 $KEEP_FILES 个）..."

OLD_FILES=$(ls -t "${BACKUP_DIR}/${BACKUP_FILE_PREFIX}_"*"${BACKUP_FILE_EXT}" 2>/dev/null | tail -n +$((KEEP_FILES + 1)))

if [ -n "$OLD_FILES" ]; then
    DELETED_COUNT=0
    for file in $OLD_FILES; do
        if [ -f "$file" ]; then
            rm -f "$file"
            DELETED_COUNT=$((DELETED_COUNT + 1))
            echo "[$(date +'%Y-%m-%d %H%M%S')] 已删除旧备份: $(basename "$file")"
        fi
    done
    echo "[$(date +'%Y-%m-%d %H%M%S')] 清理完成，共删除 $DELETED_COUNT 个旧备份文件"
else
    echo "[$(date +'%Y-%m-%d %H%M%S')] 无需清理，备份文件数量未超过保留数量"
fi

# 清理旧的错误日志文件（保留最新的 10 个）
OLD_ERROR_LOGS=$(ls -t "${BACKUP_DIR}/${BACKUP_FILE_PREFIX}_"*.err 2>/dev/null | tail -n +11)
if [ -n "$OLD_ERROR_LOGS" ]; then
    for log in $OLD_ERROR_LOGS; do
        rm -f "$log"
    done
fi

echo "[$(date +'%Y-%m-%d %H%M%S')] 备份任务完成！"
exit 0
