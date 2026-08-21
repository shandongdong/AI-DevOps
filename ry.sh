#!/bin/bash
# ./ry.sh start [test|prod] 启动 stop 停止 restart [test|prod] 重启 status 状态
# 示例：
#   ./ry.sh start        # 启动测试环境（默认）
#   ./ry.sh start test   # 启动测试环境
#   ./ry.sh start prod   # 启动生产环境
#   ./ry.sh restart prod # 重启生产环境
AppName=ruoyi-admin.jar

# 获取脚本所在目录（jar文件应该和脚本在同一目录）
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
APP_HOME=$SCRIPT_DIR

# 保存脚本参数（函数内部会覆盖 $1, $2 等）
SCRIPT_ARG1="$1"
SCRIPT_ARG2="$2"

# 默认环境为 test（测试环境）
ENV=${SCRIPT_ARG2:-test}

# 根据环境设置 Spring Profile
if [ "$ENV" = "prod" ]; then
    SPRING_PROFILES_ACTIVE="prod,druid-prod"
    echo -e "\033[0;32m 使用生产环境配置 \033[0m"
else
    SPRING_PROFILES_ACTIVE="test,druid-test"
    echo -e "\033[0;32m 使用测试环境配置 \033[0m"
fi

# JVM参数（兼容 Java 17）
# 注意：
# 1. JDK 9+ 已移除 -XX:+PrintGCDateStamps 和 -XX:+PrintGCDetails，使用统一日志框架
# 2. JDK 9+ 已移除 -XX:+UseParallelOldGC，-XX:+UseParallelGC 已包含并行老年代收集器
# 日志路径使用绝对路径，相对于 jar 文件所在目录
JVM_OPTS="-Dname=$AppName -Duser.timezone=Asia/Shanghai -Xms512m -Xmx1024m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc*:file=$APP_HOME/logs/gc-%t.log:time,tags,level:filecount=5,filesize=10M -XX:NewRatio=1 -XX:SurvivorRatio=30 -XX:+UseParallelGC -Dspring.profiles.active=$SPRING_PROFILES_ACTIVE"
LOG_PATH=$APP_HOME/logs/console.log

if [ "$1" = "" ];
then
    echo -e "\033[0;31m 未输入操作名 \033[0m  \033[0;34m {start|stop|restart|status} [test|prod] \033[0m"
    exit 1
fi

if [ "$AppName" = "" ];
then
    echo -e "\033[0;31m 未输入应用名 \033[0m"
    exit 1
fi

function start()
{
    PID=$(ps -ef |grep java|grep $AppName|grep -v grep|awk '{print $2}')

	if [ x"$PID" != x"" ]; then
	    echo "$AppName is running... (PID: $PID)"
	else
		# 切换到 jar 文件所在目录
		cd "$APP_HOME" || {
			echo "错误: 无法切换到目录 $APP_HOME"
			exit 1
		}

		# 确保日志目录存在（在 jar 文件所在目录下）
		mkdir -p "$APP_HOME/logs"

		# 检查 jar 文件是否存在
		if [ ! -f "$APP_HOME/$AppName" ]; then
			echo "错误: 找不到 jar 文件 $APP_HOME/$AppName"
			exit 1
		fi

		# 打印最终执行的命令（方便调试）
		echo "=========================================="
		echo "工作目录: $APP_HOME"
		echo "执行启动命令："
		echo "java $JVM_OPTS -jar $AppName"
		echo "=========================================="

		# 启动服务，将输出重定向到日志文件
		nohup java $JVM_OPTS -jar "$APP_HOME/$AppName" > "$LOG_PATH" 2>&1 &

		# 等待一下，检查进程是否启动成功
		sleep 2
		NEW_PID=$(ps -ef |grep java|grep $AppName|grep -v grep|awk '{print $2}')
		if [ x"$NEW_PID" != x"" ]; then
			echo "Start $AppName success... (PID: $NEW_PID)"
			echo "启动日志: $LOG_PATH"
			echo "运行日志: $APP_HOME/logs/sys-info.log"
		else
			echo "Start $AppName failed! 请检查日志: $LOG_PATH"
			if [ -f "$LOG_PATH" ]; then
				echo "最近的错误日志："
				tail -20 "$LOG_PATH"
			fi
		fi
	fi
}

function stop()
{
    echo "Stop $AppName"

	PID=""
	query(){
		PID=$(ps -ef |grep java|grep $AppName|grep -v grep|awk '{print $2}')
	}

	query
	if [ x"$PID" != x"" ]; then
		# 先发送 TERM 信号，优雅关闭
		kill -TERM $PID
		echo "$AppName (pid:$PID) exiting..."
		while [ x"$PID" != x"" ]
		do
			sleep 1
			query
		done
		echo "$AppName exited."
	else
		echo "$AppName already stopped."
	fi
}

function restart()
{
    stop
    sleep 2
    # 重启时使用传入的环境参数，如果没有则使用默认的 test
    # 使用保存的脚本参数，因为函数内部的 $2 会被函数参数覆盖
    local env_param="${SCRIPT_ARG2:-test}"
    if [ "$env_param" = "prod" ]; then
        SPRING_PROFILES_ACTIVE="prod,druid-prod"
        echo -e "\033[0;32m 使用生产环境配置 \033[0m"
    else
        SPRING_PROFILES_ACTIVE="test,druid-test"
        echo -e "\033[0;32m 使用测试环境配置 \033[0m"
    fi
    # JVM参数（兼容 Java 17）
    JVM_OPTS="-Dname=$AppName -Duser.timezone=Asia/Shanghai -Xms512m -Xmx1024m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -Xlog:gc*:file=$APP_HOME/logs/gc-%t.log:time,tags,level:filecount=5,filesize=10M -XX:NewRatio=1 -XX:SurvivorRatio=30 -XX:+UseParallelGC -Dspring.profiles.active=$SPRING_PROFILES_ACTIVE"
    start
}

function status()
{
    PID=$(ps -ef |grep java|grep $AppName|grep -v grep|awk '{print $2}')
    if [ x"$PID" != x"" ]; then
        echo "$AppName is running... (PID: $PID)"
        echo "启动日志: $LOG_PATH"
        echo "运行日志: $APP_HOME/logs/sys-info.log"
        echo "错误日志: $APP_HOME/logs/sys-error.log"
        if [ -f "$APP_HOME/logs/sys-info.log" ]; then
            echo "最近的日志（最后10行）："
            tail -10 "$APP_HOME/logs/sys-info.log"
        fi
    else
        echo "$AppName is not running..."
    fi
}

case $1 in
    start)
    start;;
    stop)
    stop;;
    restart)
    restart;;
    status)
    status;;
    *)

esac
