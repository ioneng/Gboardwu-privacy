package dev.jason.gboardpatches.patches.gboard.features.telemetry

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import dev.jason.gboardpatches.patches.gboard.shared.VerifiedTransformationPlan
import dev.jason.gboardpatches.patches.gboard.shared.VerifiedTransformationState
import dev.jason.gboardpatches.patches.gboard.shared.applyVerified
import dev.jason.gboardpatches.patches.gboard.shared.isFieldReference
import dev.jason.gboardpatches.patches.gboard.shared.isInvoke
import dev.jason.gboardpatches.patches.gboard.shared.isLiteralWrite
import dev.jason.gboardpatches.patches.gboard.shared.isMethodReference
import dev.jason.gboardpatches.patches.gboard.shared.isOpcode
import dev.jason.gboardpatches.patches.gboard.shared.isReference
import dev.jason.gboardpatches.patches.gboard.shared.isRegisterOperation
import dev.jason.gboardpatches.patches.gboard.shared.mutableClass
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD

/**
 * Blocks dedicated telemetry/reporting sidecars in the exact Gboard 18.0.3 target while
 * preserving the functional API/network calls that those sidecars observe.
 *
 * Intentionally retained: UsageReporting consent plumbing, audit consent records, AppDoctor, auth,
 * OCR execution, voice/Agentic Dictation requests, model/module downloads, remote config,
 * Tenor search/download, and other feature-required network traffic.
 */
internal val gboardTelemetryBytecodePatch = bytecodePatch(
    description = "封鎖 Gboard、ML Kit、Primes、Google Play Services 與 Tenor 的獨立遙測回報。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    execute {
        patchCompletedTaskNoOp(CLEARCUT_SUBMIT_TARGET, ::validateClearcutSubmitStockBody)
        patchForcedTrueReturn(CLEARCUT_LOGGER_GATE_TARGET, ::validateClearcutLoggerGateStockBody)
        patchCompletedTaskNoOp(CLIENT_TELEMETRY_TARGET, ::validateClientTelemetryStockBody)
        patchReturnVoidNoOp(CLIENT_THROTTLING_TARGET, ::validateClientThrottlingStockBody)
        patchReturnVoidNoOp(CLIENT_NOTIFICATION_TARGET, ::validateClientNotificationStockBody)
        patchDailyPing()
        patchReturnVoidNoOp(PRIMES_STARTUP_TARGET, ::validatePrimesStartupStockBody)
        patchReturnVoidNoOp(PRIMES_NATIVE_CRASH_TARGET, ::validatePrimesNativeCrashStockBody)
        patchReturnVoidNoOp(PRIMES_LIFEBOAT_TARGET, ::validatePrimesLifeboatStockBody)
        patchTenorRegisterShare()
    }
}

context(context: BytecodePatchContext)
private fun patchCompletedTaskNoOp(
    target: MethodTarget,
    validateStockBody: (List<Instruction>) -> Unit,
) = with(context) {
    exactMethod(target).applyVerified(
        VerifiedTransformationPlan(
            targetName = target.descriptor,
            classify = { method -> method.classifyCompletedTaskNoOp(target, validateStockBody) },
            mutate = { method ->
                method.addInstructions(0, COMPLETED_SUCCESS_TASK_PREFIX.trimIndent())
                method
            },
        ),
    )
}

context(context: BytecodePatchContext)
private fun patchReturnVoidNoOp(
    target: MethodTarget,
    validateStockBody: (List<Instruction>) -> Unit,
) = with(context) {
    exactMethod(target).applyVerified(
        VerifiedTransformationPlan(
            targetName = target.descriptor,
            classify = { method -> method.classifyReturnVoidNoOp(target, validateStockBody) },
            mutate = { method ->
                method.addInstructions(0, "return-void")
                method
            },
        ),
    )
}

context(context: BytecodePatchContext)
private fun patchForcedTrueReturn(
    target: MethodTarget,
    validateStockBody: (List<Instruction>) -> Unit,
) = with(context) {
    exactMethod(target).applyVerified(
        VerifiedTransformationPlan(
            targetName = target.descriptor,
            classify = { method -> method.classifyForcedTrueReturn(target, validateStockBody) },
            mutate = { method ->
                method.addInstructions(
                    0,
                    """
                        const/4 v0, 0x1
                        return v0
                    """.trimIndent(),
                )
                method
            },
        ),
    )
}

context(context: BytecodePatchContext)
private fun patchDailyPing() = with(context) {
    exactMethod(DAILY_PING_TARGET).applyVerified(
        VerifiedTransformationPlan(
            targetName = DAILY_PING_TARGET.descriptor,
            classify = MutableMethod::classifyDailyPing,
            mutate = { method ->
                method.addInstructions(0, DAILY_PING_SUCCESS_PREFIX.trimIndent())
                method
            },
        ),
    )
}

context(context: BytecodePatchContext)
private fun patchTenorRegisterShare() = with(context) {
    exactMethod(TENOR_REGISTER_SHARE_TARGET).applyVerified(
        VerifiedTransformationPlan(
            targetName = TENOR_REGISTER_SHARE_TARGET.descriptor,
            classify = MutableMethod::classifyTenorRegisterShare,
            mutate = { method ->
                val instructions = method.instructions()
                val shareStart = instructions.indexOfUniqueField(TENOR_SHARE_MODE_FIELD)
                val continuation = instructions.indexOfFirstAfter(
                    shareStart,
                    TENOR_CONTINUATION_FIELD,
                )
                check(continuation > shareStart) {
                    "Tenor continuation must follow register-share block"
                }
                method.addInstructionsWithLabels(
                    shareStart,
                    "goto/32 :tenor_after_register_share",
                    ExternalLabel(
                        "tenor_after_register_share",
                        method.getInstruction(continuation),
                    ),
                )
                method
            },
        ),
    )
}

context(context: BytecodePatchContext)
private fun exactMethod(target: MethodTarget): MutableMethod = with(context) {
    val matches = mutableClass(target.classDescriptor).methods.filter { method ->
        method.matches(target)
    }
    check(matches.size == 1) {
        "Expected exactly one ${target.descriptor} target; found ${matches.size}"
    }
    matches.single()
}

private fun MutableMethod.classifyCompletedTaskNoOp(
    target: MethodTarget,
    validateStockBody: (List<Instruction>) -> Unit,
): VerifiedTransformationState {
    validateTargetMetadata(target)
    val instructions = instructions()
    return when {
        instructions.hasCompletedSuccessTaskPrefix() -> {
            validateStockBody(instructions.drop(COMPLETED_SUCCESS_TASK_PREFIX_INSTRUCTION_COUNT))
            VerifiedTransformationState.PATCHED
        }
        instructions.any { it.isMethodReference(COMPLETED_SUCCESS_TASK_METHOD) } ->
            VerifiedTransformationState.MALFORMED
        else -> {
            validateStockBody(instructions)
            VerifiedTransformationState.STOCK
        }
    }
}

private fun MutableMethod.classifyReturnVoidNoOp(
    target: MethodTarget,
    validateStockBody: (List<Instruction>) -> Unit,
): VerifiedTransformationState {
    validateTargetMetadata(target)
    val instructions = instructions()
    return if (instructions.firstOrNull()?.isOpcode("RETURN_VOID") == true) {
        validateStockBody(instructions.drop(1))
        VerifiedTransformationState.PATCHED
    } else {
        validateStockBody(instructions)
        VerifiedTransformationState.STOCK
    }
}

private fun MutableMethod.classifyForcedTrueReturn(
    target: MethodTarget,
    validateStockBody: (List<Instruction>) -> Unit,
): VerifiedTransformationState {
    validateTargetMetadata(target)
    val instructions = instructions()
    val patchedPrefix = instructions.size >= 2 &&
        instructions[0].isOpcode("CONST_4") &&
        instructions[0].isLiteralWrite(0, 1) &&
        instructions[1].isRegisterOperation("RETURN", 0)
    return if (patchedPrefix) {
        validateStockBody(instructions.drop(2))
        VerifiedTransformationState.PATCHED
    } else {
        validateStockBody(instructions)
        VerifiedTransformationState.STOCK
    }
}

private fun MutableMethod.classifyDailyPing(): VerifiedTransformationState {
    validateTargetMetadata(DAILY_PING_TARGET)
    val instructions = instructions()
    return if (instructions.hasDailyPingSuccessPrefix()) {
        validateDailyPingStockBody(instructions.drop(DAILY_PING_SUCCESS_PREFIX_INSTRUCTION_COUNT))
        VerifiedTransformationState.PATCHED
    } else {
        validateDailyPingStockBody(instructions)
        VerifiedTransformationState.STOCK
    }
}

private fun MutableMethod.classifyTenorRegisterShare(): VerifiedTransformationState {
    validateTargetMetadata(TENOR_REGISTER_SHARE_TARGET)
    val instructions = instructions()
    validateTenorSentinels(instructions)
    val shareStart = instructions.indexOfUniqueField(TENOR_SHARE_MODE_FIELD)
    val continuation = instructions.indexOfFirstAfter(shareStart, TENOR_CONTINUATION_FIELD)
    check(continuation > shareStart)
    val preceding = instructions.getOrNull(shareStart - 1)
    return when {
        preceding?.isOpcode("GOTO_32") == true -> VerifiedTransformationState.PATCHED
        preceding?.isOpcode("CONST_16") == true -> VerifiedTransformationState.STOCK
        else -> VerifiedTransformationState.MALFORMED
    }
}

private fun MutableMethod.validateTargetMetadata(target: MethodTarget) {
    check(matches(target)) {
        "Refusing non-target telemetry method $definingClass->$name"
    }
    val implementation = implementation ?: error("No implementation for ${target.descriptor}")
    check(implementation.registerCount == target.registerCount) {
        "Unexpected register count in ${target.descriptor}: ${implementation.registerCount}"
    }
    check(implementation.tryBlocks.size == target.tryBlockCount) {
        "Unexpected try-block count in ${target.descriptor}: ${implementation.tryBlocks.size}"
    }
}

private fun MutableMethod.matches(target: MethodTarget): Boolean =
    definingClass == target.classDescriptor &&
        name == target.methodName &&
        parameterTypes == target.parameterTypes &&
        returnType == target.returnType &&
        accessFlags == target.accessFlags

private fun MutableMethod.instructions(): List<Instruction> =
    implementation?.instructions ?: error("No instructions in $definingClass->$name")

private fun List<Instruction>.hasCompletedSuccessTaskPrefix(): Boolean =
    size >= COMPLETED_SUCCESS_TASK_PREFIX_INSTRUCTION_COUNT &&
        this[0].isOpcode("CONST_4") &&
        this[0].isLiteralWrite(0, 0) &&
        this[1].isInvoke("INVOKE_STATIC", COMPLETED_SUCCESS_TASK_METHOD, 0) &&
        this[2].isRegisterOperation("MOVE_RESULT_OBJECT", 0) &&
        this[3].isRegisterOperation("RETURN_OBJECT", 0)

private fun List<Instruction>.hasDailyPingSuccessPrefix(): Boolean =
    size >= DAILY_PING_SUCCESS_PREFIX_INSTRUCTION_COUNT &&
        this[0].isOpcode("NEW_INSTANCE") &&
        (this[0] as? OneRegisterInstruction)?.registerA == 0 &&
        this[0].isReference("Lcsr;") &&
        this[1].isInvoke("INVOKE_DIRECT", "Lcsr;-><init>()V", 0) &&
        this[2].isOpcode("NEW_INSTANCE") &&
        (this[2] as? OneRegisterInstruction)?.registerA == 1 &&
        this[2].isReference("Lwyy;") &&
        this[3].isInvoke("INVOKE_DIRECT", "Lwyy;-><init>(Ljava/lang/Object;)V", 1, 0) &&
        this[4].isRegisterOperation("RETURN_OBJECT", 1)

private fun validateClearcutSubmitStockBody(instructions: List<Instruction>) {
    requireReference(instructions, "AbstractLogEventBuilder")
    requireReference(
        instructions,
        "resolveComplianceData should not be invoked more than once per log.",
    )
    requireMethod(instructions, "Lkth;->d(Lktn;)V")
    requireMethod(instructions, "Llsz;->b(Ljava/util/concurrent/Executor;Llsl;)Llsz;")
    check(instructions.lastOrNull()?.isRegisterOperation("RETURN_OBJECT", 10) == true) {
        "$CLEARCUT_SUBMIT_DESCRIPTOR must retain its stock final return"
    }
}

private fun validateClearcutLoggerGateStockBody(instructions: List<Instruction>) {
    requireReference(instructions, "shouldNotCreateLogger")
    requireReference(instructions, "shouldCreateLogger(): isGMSCoreSafeToConnect=false")
    requireReference(instructions, "shouldCreateLogger(): disabled for tests")
    requireMethod(instructions, "Locu;->a()Z")
    requireMethod(instructions, "Lrox;->p()Z")
    check(instructions.lastOrNull()?.isRegisterOperation("RETURN", 0) == true) {
        "$CLEARCUT_LOGGER_GATE_DESCRIPTOR must retain its final false return"
    }
}

private fun validateClientTelemetryStockBody(instructions: List<Instruction>) {
    requireReference(instructions, "Lkza;")
    requireField(instructions, "Lkvb;->a:Lkve;")
    requireMethod(instructions, "Llbs;-><init>(Llbd;)V")
    requireMethod(instructions, "Lkwq;->f(ILkzb;)Llsz;")
    check(instructions.lastOrNull()?.isRegisterOperation("RETURN_OBJECT", 4) == true)
}

private fun validateClientThrottlingStockBody(instructions: List<Instruction>) {
    requireReference(instructions, "Lkza;")
    requireField(instructions, "Lkvb;->c:Lkve;")
    requireMethod(instructions, "Llbp;-><init>(Ljava/lang/Object;I)V")
    requireMethod(instructions, "Lkwq;->f(ILkzb;)Llsz;")
    check(instructions.lastOrNull()?.isOpcode("RETURN_VOID") == true)
}

private fun validateClientNotificationStockBody(instructions: List<Instruction>) {
    requireReference(instructions, "Lkza;")
    requireField(instructions, "Lkvb;->b:Lkve;")
    requireMethod(instructions, "Llbp;-><init>(Ljava/lang/Object;I)V")
    requireMethod(instructions, "Lkwq;->f(ILkzb;)Llsz;")
    check(instructions.lastOrNull()?.isOpcode("RETURN_VOID") == true)
}

private fun validateDailyPingStockBody(instructions: List<Instruction>) {
    requireReference(instructions, "daily_ping_work")
    requireReference(instructions, "Completed work: WORK_ID = %s")
    requireReference(instructions, "Lcsr;")
    requireReference(instructions, "Lwyy;")
    requireMethod(instructions, "Lcsr;-><init>()V")
    requireMethod(instructions, "Lwyy;-><init>(Ljava/lang/Object;)V")
    check(instructions.lastOrNull()?.isRegisterOperation("RETURN_OBJECT", 0) == true)
}

private fun validatePrimesStartupStockBody(instructions: List<Instruction>) {
    requireReference(instructions, "PrimesModule.onCreate")
    requireMethod(instructions, "Landroid/os/Trace;->beginSection(Ljava/lang/String;)V")
    requireMethod(instructions, "Landroid/os/Trace;->endSection()V")
    requireField(instructions, "Lqjg;->b:Llhl;")
    requireMethod(instructions, "Lqjf;->az()Lubr;")
    check(instructions.count { it.isOpcode("RETURN_VOID") } == 1)
}

private fun validatePrimesNativeCrashStockBody(instructions: List<Instruction>) {
    requireReference(instructions, "Primes-nativecrash-sidecar")
    requireMethod(
        instructions,
        "Luas;-><init>(Lcom/google/android/libraries/performance/primes/metrics/crash/NativeCrashHandlerImpl;Luaj;)V",
    )
    requireMethod(instructions, "Ljava/lang/Thread;->setDaemon(Z)V")
    requireMethod(instructions, "Ljava/lang/Thread;->start()V")
    check(instructions.firstOrNull()?.isOpcode("MONITOR_ENTER") == true)
}

private fun validatePrimesLifeboatStockBody(instructions: List<Instruction>) {
    requireReference(instructions, "PrimesLifeboatReceiver")
    requireReference(instructions, "MetricSnapshot")
    requireReference(instructions, "Transmitters")
    requireMethod(instructions, "Ljava/lang/Class;->forName(Ljava/lang/String;)Ljava/lang/Class;")
    requireMethod(
        instructions,
        "Lcom/google/android/libraries/performance/primes/transmitter/LifeboatReceiver;->goAsync()Landroid/content/BroadcastReceiver\$PendingResult;",
    )
}

private fun validateTenorSentinels(instructions: List<Instruction>) {
    requireField(instructions, TENOR_SHARE_MODE_FIELD)
    requireField(instructions, TENOR_REGISTER_SHARE_URL_FIELD)
    requireReference(instructions, "gboard")
    requireMethod(instructions, TENOR_REGISTER_SHARE_CALL)
    requireReference(instructions, "Failed to register Tenor share")
    check(instructions.count { it.isFieldReference(TENOR_CONTINUATION_FIELD) } >= 2) {
        "Expected repeated $TENOR_CONTINUATION_FIELD markers in Tenor processor"
    }
}

private fun List<Instruction>.indexOfUniqueField(descriptor: String): Int {
    val matches = indices.filter { index -> this[index].isFieldReference(descriptor) }
    check(matches.size == 1) { "Expected exactly one $descriptor marker; found ${matches.size}" }
    return matches.single()
}

private fun List<Instruction>.indexOfFirstAfter(start: Int, fieldDescriptor: String): Int =
    ((start + 1)..lastIndex).firstOrNull { index ->
        this[index].isFieldReference(fieldDescriptor)
    } ?: error("Missing $fieldDescriptor continuation after index $start")

private fun requireReference(instructions: List<Instruction>, descriptor: String) {
    check(instructions.any { it.isReference(descriptor) }) {
        "Missing exact reference $descriptor"
    }
}

private fun requireField(instructions: List<Instruction>, descriptor: String) {
    check(instructions.any { it.isFieldReference(descriptor) }) {
        "Missing exact field $descriptor"
    }
}

private fun requireMethod(instructions: List<Instruction>, descriptor: String) {
    check(instructions.any { it.isMethodReference(descriptor) }) {
        "Missing exact method $descriptor"
    }
}

private data class MethodTarget(
    val classDescriptor: String,
    val methodName: String,
    val parameterTypes: List<String>,
    val returnType: String,
    val accessFlags: Int,
    val registerCount: Int,
    val tryBlockCount: Int,
) {
    val descriptor: String =
        "$classDescriptor->$methodName(${parameterTypes.joinToString("")})$returnType"
}

private const val PUBLIC_FINAL = 0x11
private const val PUBLIC_STATIC = 0x09
private const val PUBLIC_FINAL_DECLARED_SYNCHRONIZED = 0x20011

private const val COMPLETED_SUCCESS_TASK_METHOD =
    "Llcw;->aZ(Ljava/lang/Object;)Llsz;"
private const val COMPLETED_SUCCESS_TASK_PREFIX = """
    const/4 v0, 0x0
    invoke-static {v0}, Llcw;->aZ(Ljava/lang/Object;)Llsz;
    move-result-object v0
    return-object v0
"""
private const val COMPLETED_SUCCESS_TASK_PREFIX_INSTRUCTION_COUNT = 4

private const val DAILY_PING_SUCCESS_PREFIX = """
    new-instance v0, Lcsr;
    invoke-direct {v0}, Lcsr;-><init>()V
    new-instance v1, Lwyy;
    invoke-direct {v1, v0}, Lwyy;-><init>(Ljava/lang/Object;)V
    return-object v1
"""
private const val DAILY_PING_SUCCESS_PREFIX_INSTRUCTION_COUNT = 5

private const val CLEARCUT_SUBMIT_DESCRIPTOR = "Llvf;->l(Lkth;)Llsz;"
private val CLEARCUT_SUBMIT_TARGET = MethodTarget(
    classDescriptor = "Llvf;",
    methodName = "l",
    parameterTypes = listOf("Lkth;"),
    returnType = "Llsz;",
    accessFlags = PUBLIC_FINAL,
    registerCount = 12,
    tryBlockCount = 1,
)

private const val CLEARCUT_LOGGER_GATE_DESCRIPTOR = "Lprn;->b()Z"
private val CLEARCUT_LOGGER_GATE_TARGET = MethodTarget(
    classDescriptor = "Lprn;",
    methodName = "b",
    parameterTypes = emptyList(),
    returnType = "Z",
    accessFlags = PUBLIC_STATIC,
    registerCount = 6,
    tryBlockCount = 0,
)

private val CLIENT_TELEMETRY_TARGET = MethodTarget(
    classDescriptor = "Llbu;",
    methodName = "a",
    parameterTypes = listOf("Llbd;"),
    returnType = "Llsz;",
    accessFlags = PUBLIC_FINAL,
    registerCount = 6,
    tryBlockCount = 0,
)

private val CLIENT_THROTTLING_TARGET = MethodTarget(
    classDescriptor = "Llbr;",
    methodName = "a",
    parameterTypes = listOf("Lkzr;"),
    returnType = "V",
    accessFlags = PUBLIC_FINAL,
    registerCount = 6,
    tryBlockCount = 0,
)

private val CLIENT_NOTIFICATION_TARGET = MethodTarget(
    classDescriptor = "Llbo;",
    methodName = "a",
    parameterTypes = listOf("Lkzn;"),
    returnType = "V",
    accessFlags = PUBLIC_FINAL,
    registerCount = 7,
    tryBlockCount = 0,
)

private val DAILY_PING_TARGET = MethodTarget(
    classDescriptor = "Lcom/google/android/libraries/inputmethod/dailyping/DailyPingWorker;",
    methodName = "c",
    parameterTypes = emptyList(),
    returnType = "Lwzc;",
    accessFlags = PUBLIC_FINAL,
    registerCount = 5,
    tryBlockCount = 0,
)

private val PRIMES_STARTUP_TARGET = MethodTarget(
    classDescriptor = "Lqjg;",
    methodName = "fG",
    parameterTypes = listOf("Landroid/content/Context;", "Lptt;"),
    returnType = "V",
    accessFlags = PUBLIC_FINAL,
    registerCount = 5,
    tryBlockCount = 1,
)

private val PRIMES_NATIVE_CRASH_TARGET = MethodTarget(
    classDescriptor = "Lcom/google/android/libraries/performance/primes/metrics/crash/NativeCrashHandlerImpl;",
    methodName = "a",
    parameterTypes = listOf("Luaj;"),
    returnType = "V",
    accessFlags = PUBLIC_FINAL_DECLARED_SYNCHRONIZED,
    registerCount = 5,
    tryBlockCount = 3,
)

private val PRIMES_LIFEBOAT_TARGET = MethodTarget(
    classDescriptor = "Lcom/google/android/libraries/performance/primes/transmitter/LifeboatReceiver;",
    methodName = "onReceive",
    parameterTypes = listOf("Landroid/content/Context;", "Landroid/content/Intent;"),
    returnType = "V",
    accessFlags = PUBLIC_FINAL,
    registerCount = 12,
    tryBlockCount = 2,
)

private val TENOR_REGISTER_SHARE_TARGET = MethodTarget(
    classDescriptor = "Lgqq;",
    methodName = "E",
    parameterTypes = listOf("Lwnj;", "Lgkc;"),
    returnType = "V",
    accessFlags = PUBLIC_FINAL,
    registerCount = 26,
    tryBlockCount = 0,
)
private const val TENOR_SHARE_MODE_FIELD = "Lojl;->s:Lwod;"
private const val TENOR_REGISTER_SHARE_URL_FIELD = "Lqvz;->j:Lnxp;"
private const val TENOR_REGISTER_SHARE_CALL = "Lqws;->a(Lqwk;)Loch;"
private const val TENOR_CONTINUATION_FIELD = "Lwnj;->c:I"
