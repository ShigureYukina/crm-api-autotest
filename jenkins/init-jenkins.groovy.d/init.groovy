import jenkins.model.*
import hudson.tools.*

def inst = Jenkins.getInstance()

// ---------- Allure Commandline 全局工具 ----------
def allureDesc = new hudson.plugins.allure.tools.AllureCommandlineInstallation("Allure-Default", "/opt/allure", false)
inst.getDescriptor("hudson.plugins.allure.tools.AllureCommandlineInstallation").setInstallations(allureDesc)

// ---------- Maven 全局工具(容器内自带的 mvn 即可,不额外配置) ----------

// ---------- 关闭首次启动向导 ----------
inst.setSecurityRealm(null)
inst.save()

println "[init] Allure commandline installed: Allure-Default -> /opt/allure"
