package com.yl.gradle.study;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.AdviceAdapter;

import java.io.IOException;
import java.io.InputStream;

public class ASMCode {

    public static ClassWriter run(InputStream is) throws IOException {
        ClassReader classReader = new ClassReader(is);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor classVisitor = new TraceClassAdapter(Opcodes.ASM7, classWriter);
        classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);
        return classWriter;
    }

    public static ClassWriter run(byte[] is) throws IOException {
        ClassReader classReader = new ClassReader(is);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor classVisitor = new TraceClassAdapter(Opcodes.ASM5, classWriter);
        classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);
        return classWriter;
    }

    public static class TraceClassAdapter extends ClassVisitor {

        private String className;

        TraceClassAdapter(int i, ClassVisitor classVisitor) {
            super(i, classVisitor);
        }


        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            super.visit(version, access, name, signature, superName, interfaces);
            this.className = name;

        }

        @Override
        public void visitInnerClass(final String s, final String s1, final String s2, final int i) {
            super.visitInnerClass(s, s1, s2, i);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                                         String signature, String[] exceptions) {

            MethodVisitor methodVisitor = cv.visitMethod(access, name, desc, signature, exceptions);
            return new TraceMethodAdapter(api, methodVisitor, access, name, desc, this.className);
        }


        @Override
        public void visitEnd() {
            super.visitEnd();
        }
    }

    public static class TraceMethodAdapter extends AdviceAdapter {

        private final String methodName;
        private final String className;
        private boolean find = false;


        protected TraceMethodAdapter(int api, MethodVisitor mv, int access, String name, String desc, String className) {
            super(api, mv, access, name, desc);
            this.className = className;
            this.methodName = name;
        }

        @Override
        public void visitTypeInsn(int opcode, String s) {
            if (opcode == Opcodes.NEW && "java/lang/Thread".equals(s)) {
                find = true;
                mv.visitTypeInsn(Opcodes.NEW, "com/sample/asm/CustomThread");
                return;
            }
            super.visitTypeInsn(opcode, s);

        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
            //需要排查CustomThread自己
            if ("java/lang/Thread".equals(owner) && !className.equals("com/sample/asm/CustomThread") && opcode == Opcodes.INVOKESPECIAL && find) {
                find = false;
                mv.visitMethodInsn(opcode, "com/sample/asm/CustomThread", name, desc, itf);
                Log.e("asmcode", "className:%s, method:%s, name:%s", className, methodName, name);
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, itf);

//
//            if (owner.equals("android/telephony/TelephonyManager") && name.equals("getDeviceId") && desc.equals("()Ljava/lang/String;")) {
//                Log.e("asmcode", "get imei className:%s, method:%s, name:%s", className, methodName, name);
//            }
        }

        private int timeLocalIndex = 0;

        @Override
        protected void onMethodEnter() {
            //method enter
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/System", "currentTimeMillis", "()J", false); // 调用类的静态方法
            timeLocalIndex = newLocal(Type.LONG_TYPE); //new 一个局部变量
            mv.visitVarInsn(LSTORE, timeLocalIndex);// 把第一步的结果(在栈顶)，放到局部变量中，timeLocalIndex就是在局部变量表中的位置
        }

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
