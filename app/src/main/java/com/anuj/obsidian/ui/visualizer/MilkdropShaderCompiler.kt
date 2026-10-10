package com.anuj.obsidian.ui.visualizer

import android.opengl.GLES20
import android.util.Log

/**
 * =========================================================================================
 * OBSIDIAN MILKDROP ADVANCED HLSL TO GLSL SHADER TRANSLATION & RUNTIME ENGINE
 * =========================================================================================
 * Robust HLSL (Pixel Shader 2.0 / 3.0) to OpenGL ES 2.0/3.0 GLSL translation layer.
 *
 * Supported features:
 * 1. MilkDrop HLSL Samplers:
 *    - sampler_main, sampler_fc_main, sampler_fw_main, sampler_pc_main, sampler_pw_main
 *    - sampler_blur1, sampler_blur2, sampler_blur3
 *    - sampler_noise_lq, sampler_noise_mq, sampler_noise_hq, sampler_noise_vol_lq, sampler_noise_vol_hq
 * 2. MilkDrop Helper Functions:
 *    - GetBlur1(uv), GetBlur2(uv), GetBlur3(uv), GetPixel(uv)
 * 3. HLSL Math Intrinsics:
 *    - lerp -> mix, saturate -> clamp(..., 0.0, 1.0), frac -> fract, atan2 -> atan, fmod -> mod
 *    - ddx/ddy -> dFdx/dFdy, mul(m, v)
 * 4. Types & Swizzles:
 *    - float4/half4 -> vec4, float3/half3 -> vec3, float2/half2 -> vec2, float/half -> float
 *    - int4 -> ivec4, int3 -> ivec3, int2 -> ivec2, int -> int, bool -> bool
 * 5. Uniforms:
 *    - q1..q32, time, fps, frame, bass, mid, treb, bass_att, mid_att, treb_att, texsize, aspect_x, aspect_y, decay
 * =========================================================================================
 */
object MilkdropShaderCompiler {

    private const val TAG = "MilkdropShaderCompiler"

    // Standard Fullscreen Vertex Shader for Warp Mesh & Post-Process
    const val VERTEX_SHADER_MESH = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        uniform mat4 uMVPMatrix;
        
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vTexCoord = aTexCoord;
        }
    """

    // Standard Fullscreen Quad Vertex Shader
    const val VERTEX_SHADER_QUAD = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """

    // Standard Fallback Warp Fragment Shader
    const val FRAGMENT_SHADER_WARP_DEFAULT = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uSamplerMain;
        uniform float uDecay;
        uniform float uTime;
        uniform float uWarp;
        
        void main() {
            vec2 uv = vTexCoord;
            vec4 color = texture2D(uSamplerMain, uv);
            gl_FragColor = color * uDecay;
        }
    """

    // Standard Fallback Composite Fragment Shader
    const val FRAGMENT_SHADER_COMP_DEFAULT = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uSamplerMain;
        uniform sampler2D uSamplerBlur1;
        uniform sampler2D uSamplerBlur2;
        uniform sampler2D uSamplerBlur3;
        uniform float uTime;
        uniform float uBass;
        uniform float uMid;
        uniform float uTreb;
        
        void main() {
            vec2 uv = vTexCoord;
            vec4 base = texture2D(uSamplerMain, uv);
            vec4 blur1 = texture2D(uSamplerBlur1, uv);
            vec4 blur2 = texture2D(uSamplerBlur2, uv);
            
            // Vibrant MilkDrop HDR composite blend
            vec3 finalColor = base.rgb + blur1.rgb * 0.45 + blur2.rgb * 0.25;
            gl_FragColor = vec4(finalColor, 1.0);
        }
    """

    // Downsampling Gaussian Blur Fragment Shader for Blur Passes
    const val FRAGMENT_SHADER_BLUR = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uSamplerSource;
        uniform vec2 uTexelOffset;
        
        void main() {
            vec4 sum = vec4(0.0);
            sum += texture2D(uSamplerSource, vTexCoord + uTexelOffset * -2.0) * 0.06136;
            sum += texture2D(uSamplerSource, vTexCoord + uTexelOffset * -1.0) * 0.24477;
            sum += texture2D(uSamplerSource, vTexCoord) * 0.38774;
            sum += texture2D(uSamplerSource, vTexCoord + uTexelOffset * 1.0) * 0.24477;
            sum += texture2D(uSamplerSource, vTexCoord + uTexelOffset * 2.0) * 0.06136;
            gl_FragColor = sum;
        }
    """

    // Procedural Noise Generator Fragment Shader
    const val FRAGMENT_SHADER_NOISE = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform float uSeed;
        
        float hash(vec2 p) {
            vec3 p3 = fract(vec3(p.xyx) * 0.1031 + uSeed);
            p3 += dot(p3, p3.yzx + 33.33);
            return fract((p3.x + p3.y) * p3.z);
        }
        
        void main() {
            float n = hash(vTexCoord * 256.0);
            gl_FragColor = vec4(vec3(n), 1.0);
        }
    """

    // Double Preset Blend Fragment Shader (MilkDrop 3)
    const val FRAGMENT_SHADER_BLEND = """
        precision mediump float;
        varying vec2 vTexCoord;
        uniform sampler2D uPresetA;
        uniform sampler2D uPresetB;
        uniform float uRatio;
        uniform int uPattern; // 0=Crossfade, 1=Plasma, 2=Radial, 3=Wipe
        uniform float uTime;
        
        void main() {
            vec4 colA = texture2D(uPresetA, vTexCoord);
            vec4 colB = texture2D(uPresetB, vTexCoord);
            
            float mixFactor = uRatio;
            if (uPattern == 1) { // Plasma blend
                float plasma = sin(vTexCoord.x * 10.0 + uTime) * cos(vTexCoord.y * 10.0 + uTime) * 0.5 + 0.5;
                mixFactor = smoothstep(uRatio - 0.2, uRatio + 0.2, plasma);
            } else if (uPattern == 2) { // Radial
                float dist = distance(vTexCoord, vec2(0.5));
                mixFactor = smoothstep(uRatio * 0.7 - 0.1, uRatio * 0.7 + 0.1, dist);
            } else if (uPattern == 3) { // Wipe
                mixFactor = smoothstep(uRatio - 0.05, uRatio + 0.05, vTexCoord.x);
            }
            
            gl_FragColor = mix(colA, colB, clamp(mixFactor, 0.0, 1.0));
        }
    """

    /**
     * Translates MilkDrop HLSL pixel shader lines into standard GLSL ES 2.0/3.0.
     */
    fun compileShaderBlock(
        rawLines: List<String>,
        isWarp: Boolean,
        diagnostics: MutableList<PresetDiagnostic>
    ): MilkShaderIR {
        val rawSource = rawLines.joinToString("\n")
        if (rawLines.isEmpty()) {
            val fallbackSource = if (isWarp) FRAGMENT_SHADER_WARP_DEFAULT else FRAGMENT_SHADER_COMP_DEFAULT
            return MilkShaderIR(rawSource = "", glslSource = fallbackSource, isValid = true)
        }

        try {
            val glsl = translateHlslToGlsl(rawLines, isWarp)
            return MilkShaderIR(rawSource = rawSource, glslSource = glsl, isValid = true)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            diagnostics.add(
                PresetDiagnostic(
                    DiagnosticSeverity.WARNING,
                    1,
                    if (isWarp) "warp_shader" else "comp_shader",
                    "Could not convert HLSL shader block (${e.message}). Using safe GPU fallback shader."
                )
            )
            val fallbackSource = if (isWarp) FRAGMENT_SHADER_WARP_DEFAULT else FRAGMENT_SHADER_COMP_DEFAULT
            return MilkShaderIR(rawSource = rawSource, glslSource = fallbackSource, isValid = false, diagnostics = diagnostics)
        }
    }

    private fun translateHlslToGlsl(lines: List<String>, isWarp: Boolean): String {
        val sb = StringBuilder()
        
        // Detect if derivatives or 3D textures are needed
        var needsDerivatives = false
        var needs3DNoise = false
        
        val rawSource = lines.joinToString("\n")
        if (rawSource.contains("ddx") || rawSource.contains("ddy") || rawSource.contains("dFdx") || rawSource.contains("dFdy")) {
            needsDerivatives = true
        }
        if (rawSource.contains("tex3D") || rawSource.contains("sampler_noise_vol") || rawSource.contains("noise3d")) {
            needs3DNoise = true
        }

        if (needsDerivatives) {
            sb.append("#extension GL_OES_standard_derivatives : enable\n")
        }
        sb.append("precision mediump float;\n")
        sb.append("varying vec2 vTexCoord;\n\n")

        // Standard MilkDrop Uniform Samplers
        sb.append("uniform sampler2D uSamplerMain;\n")
        sb.append("uniform sampler2D uSamplerBlur1;\n")
        sb.append("uniform sampler2D uSamplerBlur2;\n")
        sb.append("uniform sampler2D uSamplerBlur3;\n")
        sb.append("uniform sampler2D uSamplerNoise;\n\n")

        // Standard MilkDrop Uniform Telemetry
        sb.append("uniform float uTime;\n")
        sb.append("uniform float uFps;\n")
        sb.append("uniform float uFrame;\n")
        sb.append("uniform float uProgress;\n")
        sb.append("uniform float uDecay;\n")
        sb.append("uniform float uWarp;\n")
        sb.append("uniform float uBass;\n")
        sb.append("uniform float uMid;\n")
        sb.append("uniform float uTreb;\n")
        sb.append("uniform float uBassAtt;\n")
        sb.append("uniform float uMidAtt;\n")
        sb.append("uniform float uTrebAtt;\n")
        sb.append("uniform float uAspectX;\n")
        sb.append("uniform float uAspectY;\n")
        sb.append("uniform vec4 uTexSize;\n")
        sb.append("uniform vec2 uAspect;\n\n")

        // Q1..Q32 Uniforms
        for (q in 1..32) {
            sb.append("uniform float q$q;\n")
        }
        sb.append("\n")

        // Injected MilkDrop HLSL Standard Helper Functions
        sb.append("// --- MilkDrop HLSL Standard Helpers ---\n")
        sb.append("vec3 GetBlur1(vec2 uv_c) { return texture2D(uSamplerBlur1, uv_c).rgb; }\n")
        sb.append("vec3 GetBlur2(vec2 uv_c) { return texture2D(uSamplerBlur2, uv_c).rgb; }\n")
        sb.append("vec3 GetBlur3(vec2 uv_c) { return texture2D(uSamplerBlur3, uv_c).rgb; }\n")
        sb.append("vec4 GetPixel(vec2 uv_c) { return texture2D(uSamplerMain, uv_c); }\n")
        sb.append("float saturate(float val) { return clamp(val, 0.0, 1.0); }\n")
        sb.append("vec2 saturate(vec2 val) { return clamp(val, 0.0, 1.0); }\n")
        sb.append("vec3 saturate(vec3 val) { return clamp(val, 0.0, 1.0); }\n")
        sb.append("vec4 saturate(vec4 val) { return clamp(val, 0.0, 1.0); }\n")
        sb.append("float lum(vec3 col) { return dot(col, vec3(0.299, 0.587, 0.114)); }\n")
        sb.append("float lum(vec4 col) { return dot(col.rgb, vec3(0.299, 0.587, 0.114)); }\n")
        sb.append("vec2 rotate(vec2 p, float a) { float s = sin(a); float c = cos(a); return vec2(p.x * c - p.y * s, p.x * s + p.y * c); }\n")
        sb.append("vec4 tex2Dlod(sampler2D s, vec4 uvlod) { return texture2D(s, uvlod.xy); }\n")
        sb.append("vec4 tex2Dproj(sampler2D s, vec4 uvproj) { return texture2DProj(s, uvproj); }\n")
        sb.append("vec4 tex2Dproj(sampler2D s, vec3 uvproj) { return texture2DProj(s, uvproj); }\n")
        sb.append("void sincos(float x, out float s, out float c) { s = sin(x); c = cos(x); }\n\n")

        if (needs3DNoise) {
            sb.append("// --- 3D Pseudo-Noise emulation using 2D noise sampler ---\n")
            sb.append("float noise3d(vec3 p) {\n")
            sb.append("    vec3 ip = floor(p);\n")
            sb.append("    vec3 fp = fract(p);\n")
            sb.append("    fp = fp * fp * (3.0 - 2.0 * fp);\n")
            sb.append("    vec2 tap = (ip.xy + vec2(37.0, 17.0) * ip.z);\n")
            sb.append("    vec2 r0 = texture2D(uSamplerNoise, (tap + fp.xy) / 128.0).rg;\n")
            sb.append("    return mix(r0.x, r0.y, fp.z);\n")
            sb.append("}\n\n")
        }

        sb.append("void main() {\n")
        sb.append("    vec2 uv = vTexCoord;\n")
        sb.append("    vec2 uv_orig = vTexCoord;\n")
        sb.append("    vec4 texsize = uTexSize;\n")
        sb.append("    vec2 aspect = uAspect;\n")
        sb.append("    float time = uTime;\n")
        sb.append("    float fps = uFps;\n")
        sb.append("    float frame = uFrame;\n")
        sb.append("    float progress = uProgress;\n")
        sb.append("    float bass = uBass;\n")
        sb.append("    float mid = uMid;\n")
        sb.append("    float treb = uTreb;\n")
        sb.append("    float bass_att = uBassAtt;\n")
        sb.append("    float mid_att = uMidAtt;\n")
        sb.append("    float treb_att = uTrebAtt;\n")
        sb.append("    float decay = uDecay;\n")
        sb.append("    float warp = uWarp;\n")
        sb.append("    vec4 ret = vec4(0.0);\n\n")

        var inBody = false
        var hasExplicitReturn = false

        for (line in lines) {
            var clean = line.trim()
            if (clean.isEmpty() || clean.startsWith("//") || clean.startsWith("#")) continue

            if (clean.contains("shader_body")) {
                inBody = true
                continue
            }

            if (inBody) {
                if (clean == "}" || clean == "};") {
                    break
                }

                // HLSL to GLSL replacements
                clean = clean
                    .replace("float4", "vec4")
                    .replace("float3", "vec3")
                    .replace("float2", "vec2")
                    .replace("half4", "vec4")
                    .replace("half3", "vec3")
                    .replace("half2", "vec2")
                    .replace("half", "float")
                    .replace("int4", "ivec4")
                    .replace("int3", "ivec3")
                    .replace("int2", "ivec2")
                    .replace("float2x2", "mat2")
                    .replace("float3x3", "mat3")
                    .replace("float4x4", "mat4")
                    .replace("(float4)", "vec4")
                    .replace("(float3)", "vec3")
                    .replace("(float2)", "vec2")
                    .replace("(float)", "float")
                    .replace("tex2D(sampler_main,", "texture2D(uSamplerMain,")
                    .replace("tex2D(sampler_fc_main,", "texture2D(uSamplerMain,")
                    .replace("tex2D(sampler_fw_main,", "texture2D(uSamplerMain,")
                    .replace("tex2D(sampler_pc_main,", "texture2D(uSamplerMain,")
                    .replace("tex2D(sampler_pw_main,", "texture2D(uSamplerMain,")
                    .replace("tex2D(sampler_blur1,", "texture2D(uSamplerBlur1,")
                    .replace("tex2D(sampler_blur2,", "texture2D(uSamplerBlur2,")
                    .replace("tex2D(sampler_blur3,", "texture2D(uSamplerBlur3,")
                    .replace("tex2D(sampler_noise_lq,", "texture2D(uSamplerNoise,")
                    .replace("tex2D(sampler_noise_mq,", "texture2D(uSamplerNoise,")
                    .replace("tex2D(sampler_noise_hq,", "texture2D(uSamplerNoise,")
                    .replace("tex2D(sampler_noise_vol_lq,", "texture2D(uSamplerNoise,")
                    .replace("tex2D(sampler_noise_vol_hq,", "texture2D(uSamplerNoise,")
                    .replace("tex2D(sampler_noisevol_hq,", "texture2D(uSamplerNoise,")
                    .replace("tex2D(sampler_noisevol_lq,", "texture2D(uSamplerNoise,")
                    .replace("tex2D(sampler_rand0,", "texture2D(uSamplerNoise,")
                    .replace("tex2D(sampler_rand1,", "texture2D(uSamplerNoise,")
                    .replace("tex2D(sampler_rand2,", "texture2D(uSamplerNoise,")
                    .replace("tex2D(sampler_rand3,", "texture2D(uSamplerNoise,")
                    .replace("sampler_main", "uSamplerMain")
                    .replace("sampler_fc_main", "uSamplerMain")
                    .replace("sampler_fw_main", "uSamplerMain")
                    .replace("sampler_pc_main", "uSamplerMain")
                    .replace("sampler_pw_main", "uSamplerMain")
                    .replace("sampler_blur1", "uSamplerBlur1")
                    .replace("sampler_blur2", "uSamplerBlur2")
                    .replace("sampler_blur3", "uSamplerBlur3")
                    .replace("sampler_noise_lq", "uSamplerNoise")
                    .replace("sampler_noise_mq", "uSamplerNoise")
                    .replace("sampler_noise_hq", "uSamplerNoise")
                    .replace("sampler_noise_vol_lq", "uSamplerNoise")
                    .replace("sampler_noise_vol_hq", "uSamplerNoise")
                    .replace("sampler_noisevol_hq", "uSamplerNoise")
                    .replace("sampler_noisevol_lq", "uSamplerNoise")
                    .replace("sampler_rand0", "uSamplerNoise")
                    .replace("sampler_rand1", "uSamplerNoise")
                    .replace("sampler_rand2", "uSamplerNoise")
                    .replace("sampler_rand3", "uSamplerNoise")
                    .replace("tex2D(", "texture2D(")
                    .replace("lerp(", "mix(")
                    .replace("frac(", "fract(")
                    .replace("fmod(", "mod(")
                    .replace("atan2(", "atan(")
                    .replace("rsqrt(", "inversesqrt(")
                    .replace("ddx(", "dFdx(")
                    .replace("ddy(", "dFdy(")
                    .replace("texsize.xy", "uTexSize.xy")
                    .replace("texsize.zw", "uTexSize.zw")
                    .replace("texsize", "uTexSize")
                    .replace("aspect.xy", "uAspect.xy")
                    .replace("aspect", "uAspect")

                // Replace HLSL specific constructs (mul, clip, tex3D)
                clean = replaceMul(clean)
                clean = replaceClip(clean)
                clean = replaceTex3D(clean)

                if (clean.startsWith("return")) {
                    val returnExpr = clean.removePrefix("return").removeSuffix(";").trim()
                    sb.append("    ret = $returnExpr;\n")
                    sb.append("    gl_FragColor = ret;\n")
                    hasExplicitReturn = true
                    break
                } else {
                    sb.append("    ").append(clean).append("\n")
                }
            }
        }

        if (!inBody) {
            if (isWarp) {
                sb.append("    gl_FragColor = texture2D(uSamplerMain, uv) * uDecay;\n")
            } else {
                sb.append("    gl_FragColor = texture2D(uSamplerMain, uv);\n")
            }
        } else if (!hasExplicitReturn) {
            sb.append("    gl_FragColor = ret;\n")
        }

        sb.append("}\n")
        return sb.toString()
    }

    private fun replaceMul(source: String): String {
        var current = source
        while (true) {
            val idx = current.indexOf("mul(")
            if (idx == -1) break
            
            var parenLevel = 0
            var commaIdx = -1
            var endIdx = -1
            
            for (i in (idx + 4) until current.length) {
                val c = current[i]
                if (c == '(') {
                    parenLevel++
                } else if (c == ')') {
                    if (parenLevel == 0) {
                        endIdx = i
                        break
                    } else {
                        parenLevel--
                    }
                } else if (c == ',') {
                    if (parenLevel == 0) {
                        commaIdx = i
                    }
                }
            }
            
            if (commaIdx != -1 && endIdx != -1) {
                val arg1 = current.substring(idx + 4, commaIdx).trim()
                val arg2 = current.substring(commaIdx + 1, endIdx).trim()
                val replacement = "(($arg1) * ($arg2))"
                current = current.substring(0, idx) + replacement + current.substring(endIdx + 1)
            } else {
                break
            }
        }
        return current
    }

    private fun replaceClip(source: String): String {
        var current = source
        while (true) {
            val idx = current.indexOf("clip(")
            if (idx == -1) break
            
            var parenLevel = 0
            var endIdx = -1
            for (i in (idx + 5) until current.length) {
                val c = current[i]
                if (c == '(') {
                    parenLevel++
                } else if (c == ')') {
                    if (parenLevel == 0) {
                        endIdx = i
                        break
                    } else {
                        parenLevel--
                    }
                }
            }
            
            if (endIdx != -1) {
                val arg = current.substring(idx + 5, endIdx).trim()
                val replacement = "if (($arg) < 0.0) discard"
                current = current.substring(0, idx) + replacement + current.substring(endIdx + 1)
            } else {
                break
            }
        }
        return current
    }

    private fun replaceTex3D(source: String): String {
        var current = source
        while (true) {
            val idx = current.indexOf("tex3D(")
            if (idx == -1) break
            
            var parenLevel = 0
            var commaIdx = -1
            var endIdx = -1
            for (i in (idx + 6) until current.length) {
                val c = current[i]
                if (c == '(') {
                    parenLevel++
                } else if (c == ')') {
                    if (parenLevel == 0) {
                        endIdx = i
                        break
                    } else {
                        parenLevel--
                    }
                } else if (c == ',') {
                    if (parenLevel == 0) {
                        commaIdx = i
                    }
                }
            }
            
            if (commaIdx != -1 && endIdx != -1) {
                val uvw = current.substring(commaIdx + 1, endIdx).trim()
                val replacement = "vec4(noise3d(($uvw)), noise3d(($uvw) + vec3(0.15)), noise3d(($uvw) + vec3(0.3)), 1.0)"
                current = current.substring(0, idx) + replacement + current.substring(endIdx + 1)
            } else {
                break
            }
        }
        return current
    }

    /**
     * Compiles GLSL Shader and links Program with OpenGL ES.
     */
    fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0

        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader)
            return 0
        }

        val program = GLES20.glCreateProgram()
        if (program == 0) {
            Log.e(TAG, "Could not create GLES program")
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return 0
        }

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] != GLES20.GL_TRUE) {
            val log = GLES20.glGetProgramInfoLog(program)
            Log.e(TAG, "Could not link program: $log")
            GLES20.glDeleteProgram(program)
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return 0
        }

        // Clean up individual shaders once linked
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        return program
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) return 0

        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "Could not compile shader $type: $log\nSource:\n$shaderCode")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }
}
