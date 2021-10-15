package com.yl.gradle.study

import groovy.xml.MarkupBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * 更新版本信息的 Task
 */
class StudyGradleTask extends DefaultTask {

    StudyGradleTask() {
        // 配置 Task group，即 Task 组，并为其添加上了对应的描述信息。
        group = 'study_test'
        description = 'just for study'
    }

    // action的执行顺序很有意思 ，好像是按首字母倒序输出，流入现在这个情况及时D C B
    @TaskAction
    void doDction2() {
        println("StudyGradleTask doAction2")
    }

    // 2、在 gradle 执行阶段执行，task的本质就是一系列的action
    @TaskAction
    void doBction() {
        println("StudyGradleTask doAction")
        updateVersionInfo()
    }


    @TaskAction
    void doCction1() {
        println("StudyGradleTask doAction1")
    }

    private void updateVersionInfo() {
        // 3、从 studyInfo Extension 属性中获取相应信息
        def testExtensionA = project.extensions.studyInfo.testExtensionA;
        def testExtensionB = project.extensions.studyInfo.testExtensionB;
        def testExtensionC = project.extensions.studyInfo.testExtensionC;
        def fileName = project.extensions.studyInfo.fileName;
        def file = project.file(fileName)

        // 文件有可能不存在的，所以需要判断
        if (!file.exists()){
            file.createNewFile()
        }

        // 4、将实体对象写入到 xml 文件中
        def sw = new StringWriter()
        def xmlBuilder = new MarkupBuilder(sw)
        if (file.text != null && file.text.size() <= 0) {
            //没有内容 ， 对应的就是xml格式
            xmlBuilder.studyInfo { // 这里的studyInfo就是标签
                study {
                    testA(testExtensionA)
                    testB(testExtensionB)
                    testC(testExtensionC)
                }
            }
            //直接写入
            file.withWriter { writer -> writer.append(sw.toString())
            }
        } else {
            //已有其它版本内容
            xmlBuilder.studyInfo {
                testA(testExtensionA)
                testB(testExtensionB)
                testC(testExtensionC)
            }
            //插入到最后一行前面
            def lines = file.readLines()
            def lengths = lines.size() - 1
            file.withWriter { writer ->
                lines.eachWithIndex { line, index ->
                    if (index != lengths) {
                        writer.append(line + '\r\n')
                    } else if (index == lengths) {
                        writer.append('\r\r\n' + sw.toString() + '\r\n')
                        writer.append(lines.get(tlengths))
                    }
                }
            }
        }
    }
}



