package dev.jason.gboardpatches.patches.gboard.features.telemetry

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11n
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21c
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction21t
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction30t
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction35c
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableMethodReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableTypeReference
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbi
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeAbiCatalog
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallEmitter
import dev.jason.gboardpatches.patches.gboard.shared.runtimeabi.RuntimeCallId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GboardTelemetryControlFlowTest {
    @Test
    fun `completed task guard verifies stock branch destination`() {
        val policy = RuntimeCallId.TELEMETRY_RUNTIME_SHOULD_BLOCK_CLEARCUT
        val instructions = completedTaskGuard(policy)

        assertTrue(
            instructions.hasConditionalCompletedSuccessTaskPrefix(
                RuntimeAbiCatalog.abi(policy).reference,
            ),
        )

        val malformed = instructions.withIfEqzTarget(branchIndex = 2, targetIndex = 3)
        assertFalse(
            malformed.hasConditionalCompletedSuccessTaskPrefix(
                RuntimeAbiCatalog.abi(policy).reference,
            ),
        )
    }

    @Test
    fun `return void guard verifies stock branch destination`() {
        val policy = RuntimeCallId.TELEMETRY_RUNTIME_SHOULD_BLOCK_GOOGLE_PLAY_SERVICES
        val instructions = returnVoidGuard(policy)

        assertTrue(
            instructions.hasConditionalReturnVoidPrefix(
                RuntimeAbiCatalog.abi(policy).reference,
            ),
        )
        assertFalse(
            instructions.withIfEqzTarget(2, 3).hasConditionalReturnVoidPrefix(
                RuntimeAbiCatalog.abi(policy).reference,
            ),
        )
    }

    @Test
    fun `forced boolean guard verifies stock branch destination`() {
        val policy = RuntimeCallId.TELEMETRY_RUNTIME_SHOULD_BLOCK_CRONET
        val instructions = forcedBooleanGuard(policy, forcedValue = 0)

        assertTrue(
            instructions.hasConditionalForcedBooleanPrefix(
                RuntimeAbiCatalog.abi(policy).reference,
                forcedValue = 0,
            ),
        )
        assertFalse(
            instructions.withIfEqzTarget(2, 4).hasConditionalForcedBooleanPrefix(
                RuntimeAbiCatalog.abi(policy).reference,
                forcedValue = 0,
            ),
        )
    }

    @Test
    fun `daily ping guard verifies stock branch destination`() {
        val policy = RuntimeCallId.TELEMETRY_RUNTIME_SHOULD_BLOCK_DAILY_PING
        val instructions = dailyPingGuard(policy)

        assertTrue(
            instructions.hasConditionalDailyPingPrefix(
                RuntimeAbiCatalog.abi(policy).reference,
            ),
        )
        assertFalse(
            instructions.withIfEqzTarget(2, 7).hasConditionalDailyPingPrefix(
                RuntimeAbiCatalog.abi(policy).reference,
            ),
        )
    }

    @Test
    fun `tenor guard verifies register share and continuation destinations`() {
        val policy = RuntimeCallId.TELEMETRY_RUNTIME_SHOULD_BLOCK_TENOR
        val policyReference = RuntimeAbiCatalog.abi(policy).reference
        val instructions = tenorGuard(policy)

        assertTrue(
            instructions.hasConditionalTenorPrefixAt(
                start = 0,
                scratchRegister = 2,
                policyReference = policyReference,
                setupIndex = 4,
                continuationIndex = 6,
            ),
        )

        val wrongRegisterShare = instructions.withIfEqzTarget(2, 5)
        assertFalse(
            wrongRegisterShare.hasConditionalTenorPrefixAt(
                start = 0,
                scratchRegister = 2,
                policyReference = policyReference,
                setupIndex = 4,
                continuationIndex = 6,
            ),
        )

        val wrongContinuation = instructions.withGoto32Target(3, 5)
        assertFalse(
            wrongContinuation.hasConditionalTenorPrefixAt(
                start = 0,
                scratchRegister = 2,
                policyReference = policyReference,
                setupIndex = 4,
                continuationIndex = 6,
            ),
        )
    }

    @Test
    fun `runtime policy calls emit the expected argument registers`() {
        assertTrue(
            RuntimeCallEmitter.invoke(
                RuntimeCallId.TELEMETRY_RUNTIME_SHOULD_BLOCK_CLEARCUT,
                "",
            ).startsWith("invoke-static {}, "),
        )
        assertTrue(
            RuntimeCallEmitter.invoke(
                RuntimeCallId.TELEMETRY_RUNTIME_SHOULD_BLOCK_GOOGLE_PLAY_SERVICES,
                "",
            ).startsWith("invoke-static {}, "),
        )
        assertTrue(
            RuntimeCallEmitter.invoke(
                RuntimeCallId.TELEMETRY_RUNTIME_SHOULD_BLOCK_DAILY_PING,
                "p0",
            ).startsWith("invoke-static {p0}, "),
        )
        assertTrue(
            RuntimeCallEmitter.invoke(
                RuntimeCallId.TELEMETRY_RUNTIME_SHOULD_BLOCK_PRIMES,
                "p1",
            ).startsWith("invoke-static {p1}, "),
        )
        assertTrue(
            RuntimeCallEmitter.invoke(
                RuntimeCallId.TELEMETRY_RUNTIME_SHOULD_BLOCK_TENOR,
                "",
            ).startsWith("invoke-static {}, "),
        )
        assertTrue(
            RuntimeCallEmitter.invoke(
                RuntimeCallId.TELEMETRY_RUNTIME_SHOULD_BLOCK_CRONET,
                "p0",
            ).startsWith("invoke-static {p0}, "),
        )
    }

    private fun completedTaskGuard(policy: RuntimeCallId): List<Instruction> =
        listOf(
            invoke(policy),
            ImmutableInstruction11x(Opcode.MOVE_RESULT, 0),
            ImmutableInstruction21t(Opcode.IF_EQZ, 0, 0),
            ImmutableInstruction11n(Opcode.CONST_4, 0, 0),
            invoke(
                RuntimeAbi.decode("Llcw;->aZ(Ljava/lang/Object;)Llsz;"),
                0,
            ),
            ImmutableInstruction11x(Opcode.MOVE_RESULT_OBJECT, 0),
            ImmutableInstruction11x(Opcode.RETURN_OBJECT, 0),
            ImmutableInstruction10x(Opcode.NOP),
        ).withIfEqzTarget(branchIndex = 2, targetIndex = 7)

    private fun returnVoidGuard(policy: RuntimeCallId): List<Instruction> =
        listOf(
            invoke(policy),
            ImmutableInstruction11x(Opcode.MOVE_RESULT, 0),
            ImmutableInstruction21t(Opcode.IF_EQZ, 0, 0),
            ImmutableInstruction10x(Opcode.RETURN_VOID),
            ImmutableInstruction10x(Opcode.NOP),
        ).withIfEqzTarget(branchIndex = 2, targetIndex = 4)

    private fun forcedBooleanGuard(
        policy: RuntimeCallId,
        forcedValue: Int,
    ): List<Instruction> =
        listOf(
            invoke(policy),
            ImmutableInstruction11x(Opcode.MOVE_RESULT, 0),
            ImmutableInstruction21t(Opcode.IF_EQZ, 0, 0),
            ImmutableInstruction11n(Opcode.CONST_4, 0, forcedValue),
            ImmutableInstruction11x(Opcode.RETURN, 0),
            ImmutableInstruction10x(Opcode.NOP),
        ).withIfEqzTarget(branchIndex = 2, targetIndex = 5)

    private fun dailyPingGuard(policy: RuntimeCallId): List<Instruction> =
        listOf(
            invoke(policy),
            ImmutableInstruction11x(Opcode.MOVE_RESULT, 0),
            ImmutableInstruction21t(Opcode.IF_EQZ, 0, 0),
            ImmutableInstruction21c(
                Opcode.NEW_INSTANCE,
                0,
                ImmutableTypeReference("Lcsr;"),
            ),
            invoke(RuntimeAbi.decode("Lcsr;-><init>()V"), 0),
            ImmutableInstruction21c(
                Opcode.NEW_INSTANCE,
                1,
                ImmutableTypeReference("Lwyy;"),
            ),
            invoke(RuntimeAbi.decode("Lwyy;-><init>(Ljava/lang/Object;)V"), 1, 0),
            ImmutableInstruction11x(Opcode.RETURN_OBJECT, 1),
            ImmutableInstruction10x(Opcode.NOP),
        ).withIfEqzTarget(branchIndex = 2, targetIndex = 8)

    private fun tenorGuard(policy: RuntimeCallId): List<Instruction> {
        val instructions = listOf<Instruction>(
            invoke(policy),
            ImmutableInstruction11x(Opcode.MOVE_RESULT, 2),
            ImmutableInstruction21t(Opcode.IF_EQZ, 2, 0),
            ImmutableInstruction30t(Opcode.GOTO_32, 0),
            ImmutableInstruction10x(Opcode.NOP),
            ImmutableInstruction10x(Opcode.NOP),
            ImmutableInstruction10x(Opcode.NOP),
        )
        return instructions
            .withIfEqzTarget(branchIndex = 2, targetIndex = 4)
            .withGoto32Target(branchIndex = 3, targetIndex = 6)
    }

    private fun invoke(call: RuntimeCallId, vararg registers: Int): Instruction =
        invoke(RuntimeAbiCatalog.abi(call), *registers)

    private fun invoke(abi: RuntimeAbi, vararg registers: Int): Instruction {
        require(registers.size <= 5)
        val padded = registers.toList() + List(5 - registers.size) { 0 }
        return ImmutableInstruction35c(
            if (abi.name == "<init>") Opcode.INVOKE_DIRECT else Opcode.INVOKE_STATIC,
            registers.size,
            padded[0],
            padded[1],
            padded[2],
            padded[3],
            padded[4],
            ImmutableMethodReference(abi.owner, abi.name, abi.parameters, abi.returnType),
        )
    }

    private fun List<Instruction>.withIfEqzTarget(
        branchIndex: Int,
        targetIndex: Int,
    ): List<Instruction> {
        val result = toMutableList()
        val register = (result[branchIndex] as ImmutableInstruction21t).registerA
        result[branchIndex] = ImmutableInstruction21t(
            Opcode.IF_EQZ,
            register,
            codeOffset(branchIndex, targetIndex),
        )
        return result
    }

    private fun List<Instruction>.withGoto32Target(
        branchIndex: Int,
        targetIndex: Int,
    ): List<Instruction> {
        val result = toMutableList()
        result[branchIndex] = ImmutableInstruction30t(
            Opcode.GOTO_32,
            codeOffset(branchIndex, targetIndex),
        )
        return result
    }

    private fun List<Instruction>.codeOffset(
        branchIndex: Int,
        targetIndex: Int,
    ): Int {
        val branchAddress = take(branchIndex).sumOf { instruction -> instruction.codeUnits }
        val targetAddress = take(targetIndex).sumOf { instruction -> instruction.codeUnits }
        return targetAddress - branchAddress
    }
}
