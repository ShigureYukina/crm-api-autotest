// Jenkins 声明式流水线:定时拉代码 -> 跑接口回归 -> 发布 Allure 报告
// 前置:Jenkins 安装 Allure Jenkins 插件,并在 全局工具配置 中添加 Allure Commandline
pipeline {
    agent any

    triggers {
        // 每晚 20 点定时回归,按需调整
        cron('H 20 * * *')
    }

    stages {
        stage('拉取代码') {
            steps {
                checkout scm
            }
        }
        stage('执行接口回归') {
            steps {
                // Linux agent 用 sh;Windows agent 改为 bat 'mvn clean test -Dfile.encoding=UTF-8'
                sh 'mvn clean test -Dfile.encoding=UTF-8'
            }
        }
    }

    post {
        always {
            // 发布 Allure 报告(结果目录 allure-results)
            allure includeProperties: false, jdk: '', results: [[path: 'allure-results']]
        }
    }
}
