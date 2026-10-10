package com.anuj.obsidian

import com.anuj.obsidian.ui.visualizer.AssignmentOperator
import com.anuj.obsidian.ui.visualizer.CompiledStatement
import com.anuj.obsidian.ui.visualizer.DiagnosticSeverity
import com.anuj.obsidian.ui.visualizer.MilkPresetIR
import com.anuj.obsidian.ui.visualizer.MilkdropCompatibilityAnalyzer
import com.anuj.obsidian.ui.visualizer.MilkdropExpressionCompiler
import com.anuj.obsidian.ui.visualizer.MilkdropGlobalMemory
import com.anuj.obsidian.ui.visualizer.MilkdropPresetMemory
import com.anuj.obsidian.ui.visualizer.MilkdropPresetParser
import com.anuj.obsidian.ui.visualizer.MilkdropShaderCompiler
import com.anuj.obsidian.ui.visualizer.PresetCompatibilityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

class MilkdropRuntimeUnitTest {

    @Before
    fun setUp() {
        MilkdropGlobalMemory.reset()
    }

    @Test
    fun testMathematicalExpressionsAndFunctions() {
        val lines = listOf(
            "a = 5 + 3 * 2",
            "b = sin(0) + cos(0)",
            "c = if(above(5, 3), 42, 99)",
            "d = clamp(15, 0, 10)",
            "e = exec2(100, 200)",
            "f = exec3(1, 2, 333)",
            "g = sigmoid(2, 1)"
        )
        val program = MilkdropExpressionCompiler.compileProgram(lines)
        val registers = FloatArray(program.totalSlots)
        MilkdropExpressionCompiler.executeProgram(program, registers)

        val slotA = program.nameToSlotMap["a"] ?: -1
        val slotB = program.nameToSlotMap["b"] ?: -1
        val slotC = program.nameToSlotMap["c"] ?: -1
        val slotD = program.nameToSlotMap["d"] ?: -1
        val slotE = program.nameToSlotMap["e"] ?: -1
        val slotF = program.nameToSlotMap["f"] ?: -1
        val slotG = program.nameToSlotMap["g"] ?: -1

        assertEquals(11.0f, registers[slotA], 0.001f)
        assertEquals(1.0f, registers[slotB], 0.001f)
        assertEquals(42.0f, registers[slotC], 0.001f)
        assertEquals(10.0f, registers[slotD], 0.001f)
        assertEquals(200.0f, registers[slotE], 0.001f)
        assertEquals(333.0f, registers[slotF], 0.001f)
        assertTrue(registers[slotG] > 0.8f && registers[slotG] < 0.9f)
    }

    @Test
    fun testGlobalAndPresetMemoryBuffers() {
        val presetMem = MilkdropPresetMemory()

        val lines = listOf(
            "gmegabuf(0) = 123.45",
            "gmegabuf(1) = 678.9",
            "megabuf(5) = 42",
            "megabuf(5) += 8",
            "read_g0 = gmegabuf(0)",
            "read_m5 = megabuf(5)"
        )
        val program = MilkdropExpressionCompiler.compileProgram(lines)
        val registers = FloatArray(program.totalSlots)
        MilkdropExpressionCompiler.executeProgram(program, registers, presetMem)

        val slotG0 = program.nameToSlotMap["read_g0"] ?: -1
        val slotM5 = program.nameToSlotMap["read_m5"] ?: -1

        assertEquals(123.45f, registers[slotG0], 0.01f)
        assertEquals(50.0f, registers[slotM5], 0.001f)
        assertEquals(123.45f, MilkdropGlobalMemory.read(0), 0.01f)
        assertEquals(50.0f, presetMem.read(5), 0.001f)
    }

    @Test
    fun testLoopConstructExecution() {
        val lines = listOf(
            "sum = 0",
            "counter = 0",
            "loop(10, sum = sum + 2; counter = counter + 1)"
        )
        val program = MilkdropExpressionCompiler.compileProgram(lines)
        val registers = FloatArray(program.totalSlots)
        MilkdropExpressionCompiler.executeProgram(program, registers)

        val slotSum = program.nameToSlotMap["sum"] ?: -1
        val slotCounter = program.nameToSlotMap["counter"] ?: -1

        assertEquals(20.0f, registers[slotSum], 0.001f)
        assertEquals(10.0f, registers[slotCounter], 0.001f)
    }

    @Test
    fun testShaderTranslationIntrinsics() {
        val hlslLines = listOf(
            "shader_body {",
            "  float2 uv_warped = uv + float2(0.01, -0.01);",
            "  float4 base = tex2D(sampler_main, uv_warped);",
            "  float3 blur = GetBlur2(uv);",
            "  float sat = saturate(base.r * 1.5);",
            "  ret = lerp(base, float4(blur, 1.0), 0.3);",
            "}"
        )
        val shaderIR = MilkdropShaderCompiler.compileShaderBlock(hlslLines, isWarp = false, mutableListOf())

        assertTrue(shaderIR.isValid)
        assertTrue(shaderIR.glslSource.contains("texture2D(uSamplerMain,"))
        assertTrue(shaderIR.glslSource.contains("GetBlur2(uv)"))
        assertTrue(shaderIR.glslSource.contains("mix("))
        assertTrue(shaderIR.glslSource.contains("saturate("))
    }

    // =========================================================================
    // 11 PRESET REGRESSION TEST SUITE (Section 25 Compliance)
    // =========================================================================

    @Test
    fun test1_BasicMilkdrop1Preset() {
        val content = """
            [preset00]
            fDecay=0.970000
            fWaveAlpha=0.800000
            fWaveScale=1.200000
            fZoom=1.010000
            fRot=0.002000
            per_frame_1=zoom = 1.01 + bass * 0.02;
            per_pixel_1=rot = 0.002 * (1.0 - rad);
        """.trimIndent()
        val ir = MilkdropPresetParser.parse(content, "MD1_Classic")
        assertNotNull(ir.perFrameProgram)
        assertNotNull(ir.perVertexProgram)
        val report = MilkdropCompatibilityAnalyzer.analyze(ir)
        assertEquals(PresetCompatibilityLevel.SUPPORTED, report.level)
    }

    @Test
    fun test2_QVariablePreset() {
        val content = """
            [preset00]
            q1=1.000000
            q2=2.000000
            per_frame_init_1=q1 = 10; q2 = 20;
            per_frame_1=q1 = q1 + 0.1; q2 = q2 + 0.2;
            per_pixel_1=dx = q1 * 0.001; dy = q2 * 0.001;
        """.trimIndent()
        val ir = MilkdropPresetParser.parse(content, "QVar_Flow")
        assertNotNull(ir.presetInitProgram)
        val registers = FloatArray(256)
        MilkdropExpressionCompiler.executeProgram(ir.presetInitProgram!!, registers)
        val slotQ1 = ir.presetInitProgram!!.nameToSlotMap["q1"] ?: -1
        val slotQ2 = ir.presetInitProgram!!.nameToSlotMap["q2"] ?: -1
        assertEquals(10f, registers[slotQ1], 0.001f)
        assertEquals(20f, registers[slotQ2], 0.001f)
    }

    @Test
    fun test3_PerVertexMeshWarpPreset() {
        val content = """
            [preset00]
            per_pixel_1=zoom = 1.0 + 0.05 * sin(rad * 10.0 + time);
            per_pixel_2=rot = 0.01 * cos(ang * 4.0);
        """.trimIndent()
        val ir = MilkdropPresetParser.parse(content, "MeshWarp")
        assertNotNull(ir.perVertexProgram)
        assertEquals(2, ir.perVertexProgram!!.statements.size)
    }

    @Test
    fun test4_CustomWavePreset() {
        val content = """
            [preset00]
            wavecode_0_enabled=1
            wavecode_0_samples=512
            wavecode_0_bSpectrum=0
            wavecode_0_r=0.200000
            wavecode_0_g=0.800000
            wavecode_0_b=1.000000
            wave_0_per_frame1=r = r + 0.1 * sin(time);
            wave_0_per_point1=x = sample; y = 0.5 + value1 * 0.3;
        """.trimIndent()
        val ir = MilkdropPresetParser.parse(content, "CustomWave_Spectrum")
        val wave0 = ir.customWaves[0]
        assertTrue(wave0.enabled)
        assertEquals(512, wave0.samples)
        assertNotNull(wave0.perFrameProgram)
        assertNotNull(wave0.perPointProgram)
    }

    @Test
    fun test5_CustomShapePreset() {
        val content = """
            [preset00]
            shapecode_0_enabled=1
            shapecode_0_sides=6
            shapecode_0_rad=0.250000
            shapecode_0_r=1.000000
            shapecode_0_g=0.500000
            shapecode_0_b=0.000000
            shape_0_per_frame1=ang = ang + 0.02; rad = 0.25 + 0.05 * bass;
        """.trimIndent()
        val ir = MilkdropPresetParser.parse(content, "CustomShape_Hexagon")
        val shape0 = ir.customShapes[0]
        assertTrue(shape0.enabled)
        assertEquals(6, shape0.sides)
        assertNotNull(shape0.perFrameProgram)
    }

    @Test
    fun test6_FeedbackFractalPreset() {
        val content = """
            [preset00]
            fDecay=0.990000
            fZoom=0.980000
            fRot=0.040000
            fWarp=0.050000
            per_frame_1=decay = 0.98 + 0.01 * treb;
            per_pixel_1=zoom = 0.99 + 0.02 * sin(rad * 6.28);
        """.trimIndent()
        val ir = MilkdropPresetParser.parse(content, "Feedback_Fractal")
        assertEquals(0.99f, ir.staticParams.decay, 0.001f)
        assertNotNull(ir.perFrameProgram)
        assertNotNull(ir.perVertexProgram)
    }

    @Test
    fun test7_ShaderPreset() {
        val content = """
            [preset00]
            PSVERSION=2
            warp_1=shader_body {
            warp_2=  ret = tex2D(sampler_main, uv) * 0.97;
            warp_3=}
            comp_1=shader_body {
            comp_2=  ret = tex2D(sampler_main, uv) + GetBlur1(uv) * 0.5;
            comp_3=}
        """.trimIndent()
        val ir = MilkdropPresetParser.parse(content, "Shader_Preset")
        assertTrue(ir.warpShader!!.isValid)
        assertTrue(ir.compositeShader!!.isValid)
    }

    @Test
    fun test8_PSVersion3Preset() {
        val content = """
            [preset00]
            MILKDROP_PRESET_VERSION=201
            PSVERSION=3
            PSVERSION_WARP=3
            PSVERSION_COMP=3
            warp_1=shader_body {
            warp_2=  float4 n = tex2D(sampler_noise_hq, uv);
            warp_3=  ret = tex2D(sampler_main, uv + n.xy * 0.005) * 0.98;
            warp_4=}
        """.trimIndent()
        val ir = MilkdropPresetParser.parse(content, "PS3_NoiseWarp")
        assertEquals(3, ir.metadata.psVersion)
        assertEquals(3, ir.metadata.psVersionWarp)
        assertTrue(ir.warpShader!!.isValid)
    }

    @Test
    fun test9_GMegabufMemoryPreset() {
        val content = """
            [preset00]
            per_frame_init_1=gmegabuf(100) = 42.0; megabuf(50) = 99.0;
            per_frame_1=q1 = gmegabuf(100); q2 = megabuf(50);
        """.trimIndent()
        val ir = MilkdropPresetParser.parse(content, "GlobalMemory_Test")
        val presetMem = MilkdropPresetMemory()
        val registers = FloatArray(256)
        MilkdropExpressionCompiler.executeProgram(ir.presetInitProgram!!, registers, presetMem)
        MilkdropExpressionCompiler.executeProgram(ir.perFrameProgram!!, registers, presetMem)

        val slotQ1 = ir.perFrameProgram!!.nameToSlotMap["q1"] ?: -1
        val slotQ2 = ir.perFrameProgram!!.nameToSlotMap["q2"] ?: -1
        assertEquals(42.0f, registers[slotQ1], 0.001f)
        assertEquals(99.0f, registers[slotQ2], 0.001f)
    }

    @Test
    fun test10_LoopHeavyPreset() {
        val content = """
            [preset00]
            per_frame_1=sum = 0; loop(50, sum = sum + 1.5; gmegabuf(sum) = sum * 2);
        """.trimIndent()
        val ir = MilkdropPresetParser.parse(content, "Loop_Heavy")
        val registers = FloatArray(256)
        MilkdropExpressionCompiler.executeProgram(ir.perFrameProgram!!, registers)
        val slotSum = ir.perFrameProgram!!.nameToSlotMap["sum"] ?: -1
        assertEquals(75.0f, registers[slotSum], 0.001f)
        assertEquals(150.0f, MilkdropGlobalMemory.read(75.0f), 0.001f)
    }

    @Test
    fun test11_StressTestComplexPreset() {
        val content = """
            [preset00]
            MILKDROP_PRESET_VERSION=201
            PSVERSION=3
            PSVERSION_WARP=3
            PSVERSION_COMP=3
            fDecay=0.985000
            fZoom=1.010000
            fRot=0.000000
            fWarp=0.010000
            wavecode_0_enabled=1
            wavecode_0_samples=256
            wave_0_per_point1=x = sample; y = 0.5 + value1 * 0.2;
            shapecode_0_enabled=1
            shapecode_0_sides=8
            shapecode_0_rad=0.200000
            shape_0_per_frame1=rad = 0.20 + 0.05 * sin(time * 2.0);
            per_frame_init_1=q1 = 0; loop(100, gmegabuf(q1) = q1 * 0.5; q1 = q1 + 1);
            per_frame_1=q2 = gmegabuf(10); rot = q2 * 0.01;
            per_pixel_1=zoom = 1.01 + 0.02 * sin(rad * 8.0);
            warp_1=shader_body {
            warp_2=  float4 noise = tex2D(sampler_noise_lq, uv);
            warp_3=  ret = tex2D(sampler_main, uv) * 0.985 + noise * 0.015;
            warp_4=}
            comp_1=shader_body {
            comp_2=  float3 blur1 = GetBlur1(uv);
            comp_3=  float3 blur2 = GetBlur2(uv);
            comp_4=  ret = tex2D(sampler_main, uv) + float4(blur1 * 0.4 + blur2 * 0.2, 1.0);
            comp_5=}
        """.trimIndent()

        val ir = MilkdropPresetParser.parse(content, "StressTest_AllFeatures")
        assertEquals(201, ir.metadata.milkdropPresetVersion)
        assertEquals(3, ir.metadata.psVersion)

        // Execute Init and Per-Frame
        val presetMem = MilkdropPresetMemory()
        val totalSlots = ir.presetInitProgram?.totalSlots ?: 256
        val registers = FloatArray(totalSlots)
        MilkdropExpressionCompiler.executeProgram(ir.presetInitProgram!!, registers, presetMem)
        MilkdropExpressionCompiler.executeProgram(ir.perFrameProgram!!, registers, presetMem)

        val slotQ2 = ir.perFrameProgram!!.nameToSlotMap["q2"] ?: -1
        assertEquals(5.0f, registers[slotQ2], 0.001f)

        // Compatibility analysis
        val report = MilkdropCompatibilityAnalyzer.analyze(ir)
        assertEquals(PresetCompatibilityLevel.SUPPORTED, report.level)
        assertTrue(report.features.all { it.isSupported })
    }
}
