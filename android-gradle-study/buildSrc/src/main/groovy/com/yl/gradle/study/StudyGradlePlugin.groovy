package com.yl.gradle.study

import com.android.build.gradle.AppExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

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
        Task testStudyTask = target.tasks.create("testStudyTask",{
            doLast {
                println "Hello plugin doLast ...  findByName extensions is " + target.extensions.findByName("studyInfo").properties
                println "Hello plugin doLast ...  findByType extensions is " + target.extensions.findByType(StudyPluginInfoExtension.class).testExtensionA
            }
        })

        // 创建我们自定义的task，其实就是把这个task加入到task列表中
        Task StudyGradleTask = target.tasks.create("StudyGradleTask",StudyGradleTask.class)

        // 创建依赖关系，执行StudyGradleTask前先执行testStudyTask
        StudyGradleTask.dependsOn(testStudyTask)


        // 注册我们自定义的 Transform
        def appExtension = target.extensions.findByType(AppExtension.class)
        appExtension.registerTransform(new StudyTransform());

    }
}


