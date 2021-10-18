import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Opcodes

// TODO  do something
class MyCustomClassVisitor extends ClassVisitor {

    private String className;

    MyCustomClassVisitor(ClassVisitor classVisitor, String className) {
        super(Opcodes.ASM4, classVisitor)
        this.className = className

    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.className = name;
        println("yulun-MyCustomClassVisitor-className")
    }

    @Override
    public void visitInnerClass(final String s, final String s1, final String s2, final int i) {
        super.visitInnerClass(s, s1, s2, i);
    }

//    @Override
//    public MethodVisitor visitMethod(int access, String name, String desc,
//                                     String signature, String[] exceptions) {
//
//        MethodVisitor methodVisitor = cv.visitMethod(access, name, desc, signature, exceptions);
//        return new TraceMethodAdapter(api, methodVisitor, access, name, desc, this.className);
//    }


    @Override
    public void visitEnd() {
        super.visitEnd();
    }
}
