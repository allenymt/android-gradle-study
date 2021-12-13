package com.yl.gradle.study;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

import java.io.IOException;
import java.io.InputStream;

public class ASMCode {

    public static ClassWriter run(InputStream is) throws IOException {
        ClassReader classReader = new ClassReader(is);

        // 入参有两个，ClassWriter.COMPUTE_MAXS 和 COMPUTE_FRAMES
        // 简单说 他们的区别是COMPUTE_MAXS的方式会帮助我们重新计算局部变量和操作数的size ， 慢10%
        // COMPUTE_FRAMES既会计算栈size,也会计算StackMapFrame 慢20%
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        // 定义类访问者
        ClassVisitor classVisitor = new TraceClassAdapter(Opcodes.ASM7, classWriter);

        /**
         * ClassReader.SKIP_DEBUG：表示不遍历调试内容，即跳过源文件，源码调试扩展，局部变量表，局部变量类型表和行号表属性，即以下方法既不会被解析也不会被访问（ClassVisitor.visitSource，MethodVisitor.visitLocalVariable，MethodVisitor.visitLineNumber）。使用此标识后，类文件调试信息会被去除，请警记。
         * ClassReader.SKIP_CODE：设置该标识，则代码属性将不会被转换和访问，例如方法体代码不会进行解析和访问。
         * ClassReader.SKIP_FRAMES：设置该标识，表示跳过栈图（StackMap）和栈图表（StackMapTable）属性，即MethodVisitor.visitFrame方法不会被转换和访问。当设置了ClassWriter.COMPUTE_FRAMES时，设置该标识会很有用，因为他避免了访问帧内容（这些内容会被忽略和重新计算，无需访问）。
         * ClassReader.EXPAND_FRAMES：该标识用于设置扩展栈帧图。默认栈图以它们原始格式（V1_6以下使用扩展格式，其他使用压缩格式）被访问。如果设置该标识，栈图则始终以扩展格式进行访问（此标识在ClassReader和ClassWriter中增加了解压/压缩步骤，会大幅度降低性能）。
         * 一般来说都选最全的，即使性能问题也是编译期
         */
        classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);
        return classWriter;
    }

    // 每个类都会在这里被访问到
    public static class TraceClassAdapter extends ClassVisitor {

        // 记录当前类名
        private String className;

        TraceClassAdapter(int i, ClassVisitor classVisitor) {
            super(i, classVisitor);
        }


        // 访问类的头部

        /**
         *
         * @param version 类版本
         * @param access 修饰符
         * @param name 类名
         * @param signature 泛型信息
         * @param superName 父类
         * @param interfaces 继承的接口
         */
        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            this.className = name;
        }

        // source代表什么?
        // 答： 输出打印看了下，竟然只是xxx.java 一个文件名
        @Override
        public void visitSource(String source, String debug) {
            super.visitSource(source, debug);
            Log.e("TraceClassAdapter", "visitSource source is  %s", source);
        }

        // 访问变量，这个好理解
        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            return super.visitField(access, name, descriptor, signature, value);
        }

        // 访问内部类
        @Override
        public void visitInnerClass(final String s, final String s1, final String s2, final int i) {
            super.visitInnerClass(s, s1, s2, i);
        }

        // 访问方法，由于是统计方法耗时，因此我们只需要对访问方法做操作就行
        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                                         String signature, String[] exceptions) {
            MethodVisitor methodVisitor = cv.visitMethod(access, name, desc, signature, exceptions);
            return new TraceMethodAdapter(api, methodVisitor, access, name, desc, this.className);
        }

        // 当前类访问结束
        @Override
        public void visitEnd() {
            super.visitEnd();
        }
    }

    // 方法的访问顺序如下

    /**
     * (visitParameter)*
     * [visitAnnotationDefault]
     * (visitAnnotation | visitAnnotableParameterCount | visitParameterAnnotation | visitTypeAnnotation | visitAttribute)*
     * [
     *     visitCode // 第二阶段开始 调用一次。
     *     (
     *         visitFrame |
     *         visitXxxInsn | 这里有一堆的insn，调用多次， 有注释说是在构建方法的方法体,怎么理解呢？
     *         visitLabel |
     *         visitInsnAnnotation |
     *         visitTryCatchBlock |
     *         visitTryCatchAnnotation |
     *         visitLocalVariable |
     *         visitLocalVariableAnnotation |
     *         visitLineNumber
     *     )*
     *     visitMaxs 第二阶段结束 调用一次。
     * ]
     * visitEnd 调用一次。
     *
     * 第一组，在visitCode()方法之前的方法。这一组的方法，主要负责parameter、annotation和attributes等内容；在当前课程当中，我们暂时不去考虑这些内容，可以忽略这一组方法。
     * 第二组，在visitCode()方法和visitMaxs()方法之间的方法。这一组的方法，主要负责当前方法的“方法体”内的opcode内容。其中，visitCode()方法，标志着方法体的开始，而visitMaxs()方法，标志着方法体的结束。
     * 第三组，是visitEnd()方法。这个visitEnd()方法，是最后一个进行调用的方0
     */
    // 访问方法
    public static class TraceMethodAdapter extends AdviceAdapter {

        // 当前的方法名
        private final String methodName;
        // 类名
        private final String className;

        // 以下两个是业务使用变量
        private boolean find = false;
        private int timeLocalIndex = 0;

        protected TraceMethodAdapter(int api, MethodVisitor mv, int access, String name, String desc, String className) {
            super(api, mv, access, name, desc);
            this.className = className;
            this.methodName = name;
        }

        // 当前方法访问的开始
        @Override
        public void visitCode() {
            super.visitCode();
        }

        // Label是什么？
        @Override
        public void visitLabel(Label label) {
            super.visitLabel(label);
        }

        // 不知道什么意思
        @Override
        public void visitInsn(int opcode) {
            super.visitInsn(opcode);
        }

        // 不知道什么意思
        @Override
        public void visitVarInsn(int opcode, int var) {
            super.visitVarInsn(opcode, var);
        }

        // 不知道什么意思
        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            super.visitFieldInsn(opcode, owner, name, descriptor);
        }

        // 不知道什么意思
        @Override
        public void visitIntInsn(int opcode, int operand) {
            super.visitIntInsn(opcode, operand);
        }

        // 不知道什么意思
        @Override
        public void visitLdcInsn(Object value) {
            super.visitLdcInsn(value);
        }

        // 不知道什么意思
        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            super.visitMultiANewArrayInsn(descriptor, numDimensions);
        }

        // 不知道什么意思
        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
        }

        // 不知道什么意思
        @Override
        public void visitJumpInsn(int opcode, Label label) {
            super.visitJumpInsn(opcode, label);
        }

        // 不知道什么意思
        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            super.visitLookupSwitchInsn(dflt, keys, labels);
        }

        // 不知道什么意思
        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            super.visitTableSwitchInsn(min, max, dflt, labels);
        }

        // 访问try-catch block
        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            super.visitTryCatchBlock(start, end, handler, type);
        }


        // 以下两个方法是替换了线程为自定义的线程
        // 字节码里对应到visitTypeInsn 一般都是 _new指令 ，s就是对应的type，举个例子：_new 'com/jeremyliao/gradle/CustomThread'
        @Override
        public void visitTypeInsn(int opcode, String s) {
            if (opcode == Opcodes.NEW && "java/lang/Thread".equals(s)) {
                find = true;
                mv.visitTypeInsn(Opcodes.NEW, "com/jeremyliao/gradle/CustomThread");
                return;
            }
            super.visitTypeInsn(opcode, s);

        }

        /**
         * 发生在访问方法的时候，需要把owner也就是方法属于的类替换掉
         * @param opcode 操作码
         * @param owner 方法属于哪个类
         * @param name 方法名
         * @param desc 描述
         * @param itf owner 是否为一个接口
         */
        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            //需要排除CustomThread自己
            if ("java/lang/Thread".equals(owner) && !className.equals("com/jeremyliao/gradle/CustomThread") && opcode == Opcodes.INVOKESPECIAL && find) {
                find = false;
                mv.visitMethodInsn(opcode, "com/jeremyliao/gradle/CustomThread", name, desc, itf);
                Log.e("asmcode", "className:%s, method:%s, name:%s", className, methodName, name);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);

//
//            if (owner.equals("android/telephony/TelephonyManager") && name.equals("getDeviceId") && desc.equals("()Ljava/lang/String;")) {
//                Log.e("asmcode", "get imei className:%s, method:%s, name:%s", className, methodName, name);
//            }
        }

        // 方法进入和退出这里，做了方法统计的耗时
        // 方法进入
        @Override
        protected void onMethodEnter() {
            //method enter
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false); // 调用类的静态方法
            timeLocalIndex = newLocal(Type.LONG_TYPE); //new 一个局部变量
            mv.visitVarInsn(LSTORE, timeLocalIndex);// 把第一步的结果(在栈顶)，放到局部变量中，timeLocalIndex就是在局部变量表中的位置
        }

        // 方法退出
        @Override
        protected void onMethodExit(int opcode) {
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false); // 同第一步
            mv.visitVarInsn(LLOAD, timeLocalIndex); // 加载第一个大步存储的值
            mv.visitInsn(LSUB);//此处的值在栈顶,做减法
            mv.visitVarInsn(LSTORE, timeLocalIndex);//因为后面要用到这个值所以先将其保存到本地变量表中


            int stringBuilderIndex = newLocal(Type.getType("Ljava/lang/StringBuilder")); // new一个局部变量
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder"); //new 一个实例
            mv.visitInsn(Opcodes.DUP);// 复制栈顶实例
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false); //执行实例的初始化
            mv.visitVarInsn(Opcodes.ASTORE, stringBuilderIndex);//需要将栈顶的 stringbuilder 保存起来否则后面找不到了
            mv.visitVarInsn(Opcodes.ALOAD, stringBuilderIndex); // 加载局部 变量
            mv.visitLdcInsn(className + "." + methodName + " time:"); //常量值，此时这个常量在栈顶
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false); //执行方法
            mv.visitInsn(Opcodes.POP); // 出栈，情况栈
            mv.visitVarInsn(Opcodes.ALOAD, stringBuilderIndex); // 入栈sb对象，也就是append方法的第一个参数
            mv.visitVarInsn(Opcodes.LLOAD, timeLocalIndex); // 入栈时间的差值
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(J)Ljava/lang/StringBuilder;", false); // 执行方法
            mv.visitInsn(Opcodes.POP); //保持栈的空状态
            mv.visitLdcInsn("Geek"); //加载常量
            mv.visitVarInsn(Opcodes.ALOAD, stringBuilderIndex); // 入参sb
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false); //sb的toString
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/util/Log", "d", "(Ljava/lang/String;Ljava/lang/String;)I", false);//第一个参数就是常量Geek,第二个参数是sb的ToString(),注意： Log.d 方法是有返回值的，需要 pop 出去
            mv.visitInsn(Opcodes.POP);//插入字节码后要保证栈的清洁，不影响原来的逻辑，否则就会产生异常，也会对其他框架处理字节码造成影响
        }
    }
}
