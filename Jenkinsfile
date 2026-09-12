// Jenkins 声明式流水线:拉代码 -> 探测被测环境 -> 跑接口回归 -> 发布 Allure / JUnit 报告
// 前置:Jenkins 安装 Allure Jenkins 插件,并在 全局工具配置 中添加 Allure Commandline
// 被测系统(dlyk-server/MySQL/Redis)运行在宿主机,容器内通过 host.docker.internal 访问
pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '30'))
        timeout(time: 30, unit: 'MINUTES')
    }

    triggers {
        // 每晚 20 点定时回归,按需调整
        cron('H 20 * * *')
    }

    parameters {
        string(name: 'BASE_URL', defaultValue: 'http://host.docker.internal:8089', description: '被测系统地址')
    }

    environment {
        // ConfigManager 支持环境变量覆盖 config.yaml(优先级:环境变量 > yaml)
        // 容器内访问宿主机的 dlyk/MySQL/Redis 必须走 host.docker.internal
        BASE_URL = 'http://host.docker.internal:8089'
        DB_URL = 'jdbc:mysql://host.docker.internal:3306/dlyk?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8'
        REDIS_HOST = 'host.docker.internal'
    }

    stages {
        stage('检出代码') {
            steps {
                checkout scm
            }
        }

        stage('环境检查') {
            steps {
                echo "被测系统: ${params.BASE_URL}"
                // 先探测被测系统是否存活,挂了直接终止,避免拿一堆连接超时污染报告
                sh "curl -s -o /dev/null -w 'dlyk HTTP status: %{http_code}' ${params.BASE_URL}/api/login"
            }
        }

        stage('测试执行') {
            steps {
                sh 'mvn clean test -B -Dfile.encoding=UTF-8'
            }
            post {
                always {
                    // Allure 报告数据目录(surefire 版 allure 依赖产出)
                    allure includeProperties: false,
                           jdk: '',
                           results: [[path: 'target/allure-results']]
                }
            }
        }

        stage('结果归档') {
            steps {
                // JUnit 报告 + 产物归档,失败时也能在 Jenkins UI 直接看现场
                junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                archiveArtifacts artifacts: 'target/surefire-reports/**',
                                 allowEmptyArchive: true, onlyIfSuccessful: false
            }
        }
    }

    post {
        success {
            echo "BUILD SUCCESS #${env.BUILD_NUMBER}: 全部用例通过"
        }
        failure {
            echo "BUILD FAILED #${env.BUILD_NUMBER}: 存在失败用例或环境异常,请查看 Allure / JUnit 报告"
        }
    }
}
