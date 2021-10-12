package com.yl.gradle.study

import org.gradle.api.Plugin
import org.gradle.api.Project

class StudyGradlePlugin implements Plugin<Project> {

    @Override
    void apply(Project target) {
        println "Hello plugin..." + target.name
    }
}