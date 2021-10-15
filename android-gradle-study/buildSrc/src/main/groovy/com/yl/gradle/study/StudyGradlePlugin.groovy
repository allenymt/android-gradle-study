package com.yl.gradle.study

import org.gradle.api.Plugin
import org.gradle.api.Project

class StudyGradlePlugin implements Plugin<Project> {

    @Override
    void apply(Project target) {
        println "Hello plugin..." + target.name
        // 这个name就是在gralde文件里声明的key值
        target.extensions.create("studyInfo", StudyPluginInfoExtension.class)

        // 配置阶段extensions是没有注入的，在task执行阶段才会执行，so why
        println "Hello plugin...  findByName extensions is " + target.extensions.findByName("studyInfo").properties
        println "Hello plugin...  findByType extensions is " + target.extensions.findByType(StudyPluginInfoExtension.class).testExtensionA

        // 创建testStudyTask，读取extensions里的参数
        target.tasks.create("testStudyTask",{
            doLast {
                println "Hello plugin doLast ...  findByName extensions is " + target.extensions.findByName("studyInfo").properties
                println "Hello plugin doLast ...  findByType extensions is " + target.extensions.findByType(StudyPluginInfoExtension.class).testExtensionA
            }
        })

        target.tasks.create("StudyGradleTask",StudyGradleTask.class)

    }
}


