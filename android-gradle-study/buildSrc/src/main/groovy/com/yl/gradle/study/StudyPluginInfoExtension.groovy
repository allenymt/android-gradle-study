package com.yl.gradle.study

/**
 * @author yulun* @sinice 2021-10-13 17:58
 */
class StudyPluginInfoExtension {
    String testExtensionA
    double testExtensionB
    int testExtensionC
    String fileName

    String getTestExtensionA() {
        return testExtensionA
    }

    void setTestExtensionA(String testExtensionA) {
        this.testExtensionA = testExtensionA
    }

    double getTestExtensionB() {
        return testExtensionB
    }

    void setTestExtensionB(double testExtensionB) {
        this.testExtensionB = testExtensionB
    }

    int getTestExtensionC() {
        return testExtensionC
    }

    void setTestExtensionC(int testExtensionC) {
        this.testExtensionC = testExtensionC
    }
}
