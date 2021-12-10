package com.yl.gradle.study

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import groovy.io.FileType
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.gradle.util.GFileUtils
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

/**
 * @author yulun* @sinice 2021-10-13 17:58
 */
class StudyTransform extends Transform {

    /**
     * 名称
     * @return
     */
    @Override
    String getName() {
        return "StudyTransform"
    }

    /**
     * 需要处理的数据类型，目前 ContentType 有六种枚举类型，通常我们使用比较频繁的有前两种：
     *      1、CONTENT_CLASS：表示需要处理 java 的 class 文件。
     *      2、CONTENT_JARS：表示需要处理 java 的 class 与 资源文件。
     *      3、CONTENT_RESOURCES：表示需要处理 java 的资源文件。
     *      4、CONTENT_NATIVE_LIBS：表示需要处理 native 库的代码。
     *      5、CONTENT_DEX：表示需要处理 DEX 文件。 这真的有用吗、、
     *      6、CONTENT_DEX_WITH_RESOURCES：表示需要处理 DEX 与 java 的资源文件。
     *
     * @return
     */
    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    /**
     * 表示 Transform 要操作的内容范围，目前 Scope 有五种基本类型：
     *      1、PROJECT                   只有项目内容
     *      2、SUB_PROJECTS              只有子项目
     *      3、EXTERNAL_LIBRARIES        只有外部库
     *      4、TESTED_CODE               由当前变体（包括依赖项）所测试的代码
     *      5、PROVIDED_ONLY             只提供本地或远程依赖项
     *      SCOPE_FULL_PROJECT 是一个 Scope 集合，包含 Scope.PROJECT, Scope.SUB_PROJECTS, Scope.EXTERNAL_LIBRARIES 这三项，即当前 Transform 的作用域包括当前项目、子项目以及外部的依赖库
     *
     * @return
     */

    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    // 是否增量
    @Override
    boolean isIncremental() {
        return true
    }

    // 具体执行的方法
    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation)
        println '--------------- MyTransform visit start --------------- '
        def startTime = System.currentTimeMillis()
        def inputs = transformInvocation.inputs
        def outputProvider = transformInvocation.outputProvider

        // 当前transform是否支持增量
        if (!transformInvocation.isIncremental()) {
            outputProvider.deleteAll()
        }

        // Transform 的 inputs 有两种类型，一种是目录，一种是 jar 包，要分开遍历
        inputs.each { TransformInput input ->
            // 遍历 directoryInputs（本地 project 编译成的多个 class ⽂件存放的目录）
            input.directoryInputs.each { DirectoryInput directoryInput ->
                try {
                    handleDirectory(directoryInput, transformInvocation)
                } catch (Exception e) {
                    e.printStackTrace()
                }
            }

            // 遍历 jarInputs（各个依赖所编译成的 jar 文件）
            input.jarInputs.each { JarInput jarInput ->
                try {
                    handleJar(jarInput, transformInvocation)
                } catch (Exception e) {
                    e.printStackTrace()
                }
            }
        }

        def cost = (System.currentTimeMillis() - startTime) / 1000
        println '--------------- MyTransform visit end --------------- '
        println "MyTransform cost ： $cost s"
    }

    // 本地依赖的lib也是当做jar处理
    static void handleJar(JarInput jarInput, TransformInvocation transformInvocation) {
        if (!jarInput.file.getAbsolutePath().endsWith(".jar")) {
            return
        }
        println("StudyTransform handleJar1 Begin ${jarInput.file.absolutePath}")
        def dest = transformInvocation.outputProvider.getContentLocation(jarInput.name,
                jarInput.contentTypes, jarInput.scopes, Format.JAR)
        if (transformInvocation.incremental) {
            switch (jarInput.status) {
                case Status.ADDED:
                case Status.CHANGED:
                    println("StudyTransform incremental true Status ${jarInput.status}  file is ${jarInput.file.absolutePath}")
                    asmProcessJar(jarInput.file)
                    // 处理input字节码
                    FileUtils.copyFile(jarInput.file, dest)
                    break
                case Status.REMOVED:
                    println("StudyTransform incremental true Status REMOVED  file is ${jarInput.file.absolutePath}")
                    GFileUtils.deleteFileQuietly(dest)
                    break
            }
        } else {
            println("StudyTransform incremental false handleJar file is ${jarInput.file.absolutePath}")
            asmProcessJar(jarInput.file)
            // 处理input字节码处理input字节码
            FileUtils.copyFile(jarInput.file, dest)
        }
    }

    static void asmProcessJar(File file) {
        if (file == null || !file.exists()) {
            return
        }
        println "asmProcessJar start file is ${file.absolutePath}"
        File tmpFile = new File(file.getParent() + File.separator + "${file.name}_classes_temp.jar")
        // 避免上次的缓存被重复插入
        if (tmpFile.exists()) {
            tmpFile.delete()
        }
        JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(tmpFile))

        def jarFile = new JarFile(file)
        Enumeration enumeration = jarFile.entries()
        while (enumeration.hasMoreElements()) {
            JarEntry jarEntry = (JarEntry) enumeration.nextElement()
            String entryName = jarEntry.getName()
            ZipEntry zipEntry = new ZipEntry(entryName)
            InputStream inputStream = jarFile.getInputStream(jarEntry)
            if (checkClassFile(entryName)) {
                // 使用 ASM 对 class 文件进行操控
                println '----------- deal with "jar" class file <' + entryName + '> -----------'
                jarOutputStream.putNextEntry(zipEntry)
                jarOutputStream.write(ASMCode.run(inputStream).toByteArray())
            } else {
                println '----------- undeal with "jar" class file <' + entryName + '> -----------'
                jarOutputStream.putNextEntry(zipEntry)
                jarOutputStream.write(IOUtils.toByteArray(inputStream))
            }
            jarOutputStream.closeEntry()
        }
        jarOutputStream.close()
        jarFile.close()

        if (file.exists()) {
            file.delete()
        }
        println "----------- ${tmpFile.absolutePath} rename to ${file.absolutePath} -----------"
        tmpFile.renameTo(file)
    }

    /**
     * 疑问：为什么即使不做操作也要做一次copy呢，猜测是每个transform是独立的，而且他的output也是固定的，对于
     * 每个transform，output里必须有内容，才能将处理的数据一级级的传递下去，对于下一级来说的，上一级的output就是下一级的input,一直传递下去，直到结束
     * transform的模板写法
     * @param directoryInput 输入流封装
     * @param transformInvocation
     */
    static void handleDirectory(DirectoryInput directoryInput, TransformInvocation transformInvocation) {
        // 当前项目里是 inputDir /Users/yulun/android-gradle-study/android-gradle-study/app/build/intermediates/javac/debug/classes
        // 注意这里是个directory
        def inputDir = directoryInput.getFile()

        //输出的路径，某次编译是  /Users/yulun/android-gradle-study/android-gradle-study/app/build/intermediates/transforms/StudyTransform/debug/42
        def outputDir = transformInvocation.outputProvider.getContentLocation(directoryInput.name, directoryInput.contentTypes, directoryInput.scopes, Format.DIRECTORY)
        println("[StudyTransform] handleDirectory1: inputDir ${inputDir.absolutePath}")
        println("[StudyTransform] handleDirectory1: outputDir ${outputDir.absolutePath}")
        println("[StudyTransform] handleDirectory1: incremental ${transformInvocation.incremental}")
        // 执行注入逻辑
        // 当前是否为增量
        if (transformInvocation.incremental) {
            // 进入增量逻辑，对已修改的文件做遍历
            directoryInput.changedFiles.each {
                // 针对当前file，构建输出的文件名
                def outputFile = new File(outputDir, com.android.utils.FileUtils.relativePossiblyNonExistingPath(it.key, inputDir))
                println("[StudyTransform] incremental true outputFile file is : ${outputFile}")
                switch (it.value) {
                // 新增或者修改状态，都需要修改
                    case Status.ADDED:
                    case Status.CHANGED:
                        println("[StudyTransform] Status ${it.value} file is : ${it.key.absolutePath}")
                        // 先删除之前的目录，如果存在的话
                        GFileUtils.deleteQuietly(outputFile)
                        //在这里做字节码修改操作，对input做操作
                        if (checkClassFile(it.key.name)) {
                            println '----------- deal with "class" file <' + name + '> -----------'
                            // 下面是套路代码
                            def classReader = new ClassReader(it.key.bytes)
                            def classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
                            // ASMCode里就是我们实际操作修改字节码的地方
                            def classVisitor = new ASMCode.TraceClassAdapter(org.objectweb.asm.Opcodes.ASM5, classWriter)
                            classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
                            // 保存修改后的字节码
                            byte[] codeBytes = classWriter.toByteArray()
                            // 构建输出流，这里是当前目录的原文件；也可以新建个临时文件，写完后再覆盖
                            FileOutputStream fileOutputStream = new FileOutputStream(
                                    it.key.parentFile.absolutePath + File.separator + it.key.name
                            )
                            fileOutputStream.write(codeBytes)
                            fileOutputStream.close()
                        }
                        //在对input操作完后，将input的目录文件复制到output目录，这一步必须的，因为这个输入输出是相对于transform来说的，也就是下一个transform的输入
                        FileUtils.copyFile(it.key, outputFile)
                        break
                // 文件被删除了
                    case Status.REMOVED:
                        // 那就直接删除
                        println("[StudyTransform] Status remove file is : ${it.key.absolutePath}")
                        GFileUtils.deleteQuietly(outputFile)
                        break
                }
            }
        } else {
            // 首次编译，全量状态
            println("[StudyTransform] incremental false and  file is : ${inputDir.absolutePath}")
            // 还是先删除，这里多一次操作不会损失什么，但有保证
            GFileUtils.deleteQuietly(outputDir)
            //在这里做字节码修改操作，对input做操作，注意inputDir是个路径，要循环遍历它下面所有的class文件
            inputDir.traverse(type: FileType.FILES, nameFilter: ~/.*\.class/) {
                if (checkClassFile(it.name)) {
                    println '----------- deal with "class" file <' + it.name + '> -----------'
                    def classReader = new ClassReader(it.bytes)
                    def classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
                    def classVisitor = new ASMCode.TraceClassAdapter(org.objectweb.asm.Opcodes.ASM5, classWriter)
                    classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
                    byte[] codeBytes = classWriter.toByteArray()
                    try {
                        // 这里还是写入原文件
                        FileOutputStream fileOutputStream = new FileOutputStream(
                                it.parentFile.absolutePath + File.separator + it.name
                        )
                        fileOutputStream.write(codeBytes)
                        fileOutputStream.close()
                    } catch (Exception e) {
                        e.printStackTrace()
                    }
                }
            }
            // 将input的目录复制到output指定目录，这里也是必须要copy的
            FileUtils.copyDirectory(inputDir, outputDir)
        }
    }
    /**
     * 检查 class 文件是否需要处理
     *
     * @param fileName
     * @return class 文件是否需要处理
     */
    static boolean checkClassFile(String entryName) {
        if (!entryName.endsWith(".class")
                || entryName.contains("\$")
                || entryName.endsWith("R.class")
                || entryName.endsWith("BuildConfig.class")
                || entryName.contains("android/support/")
                || entryName.contains("android/arch/")
                || entryName.contains("android/app/")
                || entryName.contains("androidx")) {
            print("checkClassFile className is $entryName false")
            return false
        }
        print("checkClassFile className is $entryName true")
        return true
    }
}
