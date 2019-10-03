package ae.hitb.proctf.drone_racing.programming.jvm

import ae.hitb.proctf.drone_racing.programming.Compiler
import ae.hitb.proctf.drone_racing.programming.exhaustive
import ae.hitb.proctf.drone_racing.programming.stack.*
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes.*
import org.objectweb.asm.Type.getDescriptor
import ae.hitb.proctf.drone_racing.programming.language.*
import java.io.BufferedReader


class StackToJvmCompiler : Compiler<StackProgram, ByteArray> {
    private val brDescriptor = getDescriptor(BufferedReader::class.java)

    override fun compile(programName: String, source: StackProgram): ByteArray {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        cw.visit(V1_8, ACC_PUBLIC + ACC_SUPER, programName, null, "java/lang/Object", emptyArray())

        cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null).apply {
            visitVarInsn(ALOAD, 0)
            visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            visitInsn(RETURN)
            visitMaxs(-1, -1)
            visitEnd()
        }

        /*
        cw.visitMethod(ACC_PUBLIC + ACC_STATIC, "<clinit>", "()V", null, null).apply {
            visitTypeInsn(NEW, "java/io/BufferedReader")
            visitInsn(DUP)
            visitTypeInsn(NEW, "java/io/InputStreamReader")
            visitInsn(DUP)
            visitFieldInsn(GETSTATIC, "java/lang/System", "in", getDescriptor(InputStream::class.java))
            visitMethodInsn(INVOKESPECIAL, "java/io/InputStreamReader", "<init>", "(Ljava/io/InputStream;)V", false)
            visitMethodInsn(INVOKESPECIAL, "java/io/BufferedReader", "<init>", "(Ljava/io/Reader;)V", false)
            visitFieldInsn(PUTSTATIC, "Program", "input", brDescriptor)
            visitInsn(RETURN)
            visitMaxs(-1, -1)
            visitEnd()
        }
        */

        source.functions.forEach { (declaration, code) -> compileFunction(declaration, code, cw, declaration == source.entryPoint, source.literalPool) }

        return cw.toByteArray()
    }

    private fun getJavaType(functionType: FunctionType) : String {
        return when (functionType) {
            FunctionType.INTEGER -> "I"
            FunctionType.STRING -> "Ljava/lang/String;"
            FunctionType.VOID -> "V"
            else -> ""
        }
    }

    private fun findFunctionVariableType(function: FunctionDeclaration, v: Variable): FunctionType {
        val index = function.parameterNames.indexOf(v)
        if (index < 0)
            return FunctionType.INTEGER
        return function.parameterTypes[index]
    }

    private fun compileFunction(
        function: FunctionDeclaration,
        source: List<StackStatement>,
        cw: ClassWriter,
        isMain: Boolean,
        literalPool: List<CharArray>
    ) {
        val signature = "(" + function.parameterTypes.joinToString { getJavaType(it) } + ")" + getJavaType(function.returnType)

        cw.visitMethod(ACC_PUBLIC + ACC_STATIC, function.name, signature, null, null).apply {
            val beginLabel = Label().apply { info = "begin" }
            val endLabel = Label().apply { info = "end" }
            visitLabel(beginLabel)

            var variablesMap = function.parameterNames.withIndex().associate { (index, it) -> it to index }.toMutableMap()

            val functionVariables = (collectVariables(source) + function.parameterNames).distinct()
            val functionLocalVariables = functionVariables - function.parameterNames
            var variableIndex = function.parameterNames.size
            functionLocalVariables.forEach {
                variablesMap[it] = variableIndex++
            }
            variablesMap.forEach { (v, index) -> visitLocalVariable(v.name, getJavaType(findFunctionVariableType(function, v)), null, beginLabel, endLabel, index) }

            val labels = (source + NOP).map { Label().apply { info = it; } }

            for ((index, s) in source.withIndex()) {
                visitLabel(labels[index])

                when (s) {
                    Nop -> visitInsn(NOP)

                    is Push -> visitLdcInsn(s.constant.value)
                    is Ld -> visitVarInsn(ILOAD, variablesMap[s.v]!!)
                    is St -> visitVarInsn(ISTORE, variablesMap[s.v]!!)
                    is Unop -> when (s.kind) {
                        Not -> {
                            val labelIfNz = Label()
                            val labelAfter = Label()
                            visitJumpInsn(IFNE, labelIfNz)
                            visitInsn(ICONST_0)
                            visitJumpInsn(GOTO, labelAfter)
                            visitLabel(labelIfNz)
                            visitInsn(ICONST_1)
                            visitLabel(labelAfter)
                        }
                    }
                    is Binop -> when (s.kind) {
                        Plus -> visitInsn(IADD)
                        Minus -> visitInsn(ISUB)
                        Times -> visitInsn(IMUL)
                        Div -> visitInsn(IDIV)
                        Rem -> visitInsn(IREM)
                        And -> visitInsn(IAND)
                        Or -> visitInsn(IOR)
                        Eq, Neq, Gt, Lt, Leq, Geq -> {
                            val labelOtherwise = Label()
                            val labelAfter = Label()
                            visitJumpInsn(checkOtherwiseOp[s.kind]!!, labelOtherwise)
                            visitInsn(ICONST_1)
                            visitJumpInsn(GOTO, labelAfter)
                            visitLabel(labelOtherwise)
                            visitInsn(ICONST_0)
                            visitLabel(labelAfter)
                        }
                    }.exhaustive
                    is Jmp -> visitJumpInsn(GOTO, labels[s.nextInstruction])
                    is Jz -> {
                        visitInsn(ICONST_0)
                        visitJumpInsn(IF_ICMPEQ, labels[s.nextInstruction])
                    }
                    is Call -> when (s.function) {
                        is Intrinsic -> when (s.function) {
                            Intrinsic.READ -> {
                                visitFieldInsn(GETSTATIC, "Program", "input", brDescriptor)
                                visitMethodInsn(INVOKEVIRTUAL, "java/io/BufferedReader", "readLine", "()Ljava/lang/String;", false)
                                visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "parseInt", "(Ljava/lang/String;)I", false)
                            }
                            Intrinsic.WRITE -> {
                                visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;")
                                visitInsn(SWAP)
                                visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false)
                            }
                            Intrinsic.WRITESTRING -> {
                                visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;")
                                visitInsn(SWAP)
                                visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false)
                            }
                            Intrinsic.STRMAKE -> TODO()
                            Intrinsic.STRCMP -> {
                                visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "compareTo","(Ljava/lang/String;)I", false);
                            }
                            Intrinsic.STRGET -> TODO()
                            Intrinsic.STRDUP -> {
                                // NOP
                            }
                            Intrinsic.STRSET -> TODO()
                            Intrinsic.STRCAT -> TODO()
                            Intrinsic.STRSUB -> TODO()
                            Intrinsic.STRLEN -> visitMethodInsn(INVOKEVIRTUAL, "java/lang/String", "length", "()I", false)
                            Intrinsic.ARRMAKE -> TODO()
                            Intrinsic.ARRMAKEBOX -> TODO()
                            Intrinsic.ARRGET -> TODO()
                            Intrinsic.ARRSET -> TODO()
                            Intrinsic.ARRLEN -> TODO()
                        }
                        else -> {
                            val descriptor = "(" + s.function.parameterTypes.joinToString { getJavaType(it) } + ")" + getJavaType(s.function.returnType)
                            visitMethodInsn(INVOKESTATIC, "Program", s.function.name, descriptor, false)
                            // TODO Нужно? Ради хакабельности?
//                            visitLdcInsn(0)
                        }
                    }
                    Ret0 -> {
                        visitInsn(RETURN)
                    }
                    Ret1 -> {
                        visitInsn(IRETURN)
//                        visitInsn(POP)
                    }
                    Pop -> visitInsn(POP)
                    is PushPooled -> {
                        visitLdcInsn(String(literalPool[s.id]))
                    }
                    TransEx -> visitInsn(NOP)
                }.exhaustive
            }

            visitLabel(labels.last())
//            visitInsn(RETURN)
            visitMaxs(0, 0)
            visitEnd()
        }
    }

    private val checkOtherwiseOp = mapOf(
        Eq to IF_ICMPNE,
        Neq to IF_ICMPEQ,
        Gt to IF_ICMPLE,
        Lt to IF_ICMPGE,
        Geq to IF_ICMPLT,
        Leq to IF_ICMPGT
    )
}