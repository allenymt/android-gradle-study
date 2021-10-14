package com.yl.gradle.study

import org.gradle.api.Plugin
import org.gradle.api.Project

class StudyGradlePlugin implements Plugin<Project> {

    @Override
    void apply(Project target) {
        println "Hello plugin..." + target.name
        target.extensions.create("StudyPluginInfo", StudyPluginInfoExtension.class)

        // 配置阶段extensions是没有注入的，在task执行阶段才会执行
        println "Hello plugin...  findByName extensions is " + target.extensions.findByName("StudyPluginInfo").properties
        println "Hello plugin...  findByType extensions is " + target.extensions.findByType(StudyPluginInfoExtension.class).testExtensionA

        //
        target.tasks.create("testStudyTask",{
            doLast {
                println "Hello plugin doLast ...  findByName extensions is " + target.extensions.findByName("StudyPluginInfo").properties
                println "Hello plugin doLast ...  findByType extensions is " + target.extensions.findByType(StudyPluginInfoExtension.class).testExtensionA
            }
        })
    }
}