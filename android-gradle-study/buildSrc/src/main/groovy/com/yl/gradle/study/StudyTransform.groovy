package com.yl.gradle.study

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import groovy.io.FileType
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
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
     *      5、CONTENT_DEX：表示需要处理 DEX 文件。
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
        // 删除之前的输出
        if (outputProvider != null)
            outputProvider.deleteAll()

        // Transform 的 inputs 有两种类型，一种是目录，一种是 jar 包，要分开遍历
        inputs.each { TransformInput input ->
            // 遍历 directoryInputs（本地 project 编译成的多个 class ⽂件存放的目录）
            input.directoryInputs.each { DirectoryInput directoryInput ->
                try {
                    handleDirectory(directoryInput, outputProvider)
                } catch (Exception e) {
                    e.printStackTrace()
                }
            }

            // 遍历 jarInputs（各个依赖所编译成的 jar 文件）
            input.jarInputs.each { JarInput jarInput ->
                try {
                    handleJar(jarInput, outputProvider)
                } catch (Exception e) {
                    e.printStackTrace()
                }
            }
        }

        def cost = (System.currentTimeMillis() - startTime) / 1000
        println '--------------- MyTransform visit end --------------- '
        println "MyTransform cost ： $cost s"
    }

    /**
     * 处理jar文件
     * @param jarInput
     * @param outputProvider
     */
    static void handleJar(JarInput jarInput, TransformOutputProvider outputProvider) {
        if (jarInput.file.getAbsolutePath().endsWith(".jar")) {
            // 截取文件路径的 md5 值重命名输出文件，避免同名导致覆盖的情况出现
            def jarName = jarInput.name
            if (jarName.contains("androidx"))
                return
            def md5Name = ""
//            try {
//                md5Name = DigestUtils.md5Hex(jarInput.file.getAbsolutePath())
//            } catch (Exception var6) {
//                var6.printStackTrace()
//            }
            if (jarName.endsWith(".jar")) {
                jarName = jarName.substring(0, jarName.length() - 4)
            }
            JarFile jarFile = new JarFile(jarInput.file)
            Enumeration enumeration = jarFile.entries()
            File tmpFile = new File(jarInput.file.getParent() + File.separator + "classes_temp.jar")
            // 避免上次的缓存被重复插入
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
            JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(tmpFile))
            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement()
                String entryName = jarEntry.getName()
                ZipEntry zipEntry = new ZipEntry(entryName)
                InputStream inputStream = jarFile.getInputStream(jarEntry)
                if (checkClassFile(entryName)) {
                    // 使用 ASM 对 class 文件进行操控
                    println '----------- deal with "jar" class file <' + entryName + '> -----------'
                    jarOutputStream.putNextEntry(zipEntry)
                    ClassReader classReader = new ClassReader(IOUtils.toByteArray(inputStream))
                    ClassWriter classWriter = new ClassWriter(classReader, org.objectweb.asm.ClassWriter.COMPUTE_MAXS)
                    ClassVisitor cv = new MyCustomClassVisitor(classWriter, entryName)
                    classReader.accept(cv, ClassReader.EXPAND_FRAMES)
                    byte[] code = classWriter.toByteArray()
                    jarOutputStream.write(code)
                } else {
                    jarOutputStream.putNextEntry(zipEntry)
                    jarOutputStream.write(IOUtils.toByteArray(inputStream))
                }
                jarOutputStream.closeEntry()
            }
            jarOutputStream.close()
            jarFile.close()

            // 生成输出路径 dest：./app/build/intermediates/transforms/xxxTransform/...
            def dest = outputProvider.getContentLocation(jarName + md5Name,
                    jarInput.contentTypes, jarInput.scopes, Format.JAR)
            // 将 input 的目录复制到 output 指定目录
            FileUtils.copyFile(tmpFile, dest)
            tmpFile.delete()
        }
    }

    static void handleDirectory(DirectoryInput directoryInput, TransformOutputProvider outputProvider) {
        // 在增量模式下可以通过 directoryInput.changedFiles 方法获取修改的文件
        if (directoryInput.file.size() == 0)
            return
        if (directoryInput.file.isDirectory()) {
            /**遍历以某一扩展名结尾的文件*/
            directoryInput.file.traverse(type: FileType.FILES, nameFilter: ~/.*\.class/) {
                File classFile ->
                    def name = classFile.name
                    if (checkClassFile(name)) {
                        println '----------- deal with "class" file <' + name + '> -----------'
                        def classReader = new ClassReader(classFile.bytes)
                        def classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
                        def classVisitor = new MyCustomClassVisitor(classWriter, name)
                        classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
                        byte[] codeBytes = classWriter.toByteArray()
                        FileOutputStream fileOutputStream = new FileOutputStream(
                                classFile.parentFile.absolutePath + File.separator + name
                        )
                        fileOutputStream.write(codeBytes)
                        fileOutputStream.close()
                    }
            }
        }

        /// 获取 output 目录 dest：./app/build/intermediates/transforms/hencoderTransform/
        def destFile = outputProvider.getContentLocation(
                directoryInput.name,
                directoryInput.contentTypes,
                directoryInput.scopes,
                Format.DIRECTORY
        )
        // 将 input 的目录复制到 output 指定目录
        FileUtils.copyDirectory(directoryInput.file, destFile)
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
                || entryName.contains("android/app/")) {
            return false
        }
        return true
    }
}
