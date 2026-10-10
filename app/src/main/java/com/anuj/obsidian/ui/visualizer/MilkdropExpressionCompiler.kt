package com.anuj.obsidian.ui.visualizer

import android.util.Log
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * =========================================================================================
 * OBSIDIAN MILKDROP EXPRESSION COMPILER & RUNTIME AST ENGINE
 * =========================================================================================
 * Comprehensive specification-compliant MilkDrop mathematical expression compiler and runtime.
 * Supports:
 * - Unary/Binary math operations and MilkDrop special operators
 * - Function library: sin, cos, tan, asin, acos, atan, atan2, sqrt, sqr, abs, pow, exp, log/ln,
 *   min, max, floor, ceil, int, frac, mod, clamp, rand, sign, above, below, equal, band, bor,
 *   bxor, bnot, not, and, or, sigmoid, if, exec2, exec3
 * - Global memory buffers: gmegabuf(index) and megabuf(index) read & write
 * - Loop / Iteration constructs: loop(count, stmt1; stmt2; ...)
 * - Constant folding and structured diagnostics
 * =========================================================================================
 */
sealed class ExpressionASTNode {
    abstract fun eval(vars: FloatArray, presetMem: MilkdropPresetMemory? = null): Float

    data class ConstantNode(val value: Float) : ExpressionASTNode() {
        override fun eval(vars: FloatArray, presetMem: MilkdropPresetMemory?): Float = value
    }

    data class VariableNode(val slot: Int, val name: String) : ExpressionASTNode() {
        override fun eval(vars: FloatArray, presetMem: MilkdropPresetMemory?): Float {
            return if (slot >= 0 && slot < vars.size) vars[slot] else 0f
        }
    }

    data class GMegabufReadNode(val indexExpr: ExpressionASTNode) : ExpressionASTNode() {
        override fun eval(vars: FloatArray, presetMem: MilkdropPresetMemory?): Float {
            val idx = indexExpr.eval(vars, presetMem)
            return MilkdropGlobalMemory.read(idx)
        }
    }

    data class MegabufReadNode(val indexExpr: ExpressionASTNode) : ExpressionASTNode() {
        override fun eval(vars: FloatArray, presetMem: MilkdropPresetMemory?): Float {
            val idx = indexExpr.eval(vars, presetMem)
            return presetMem?.read(idx) ?: 0f
        }
    }

    data class UnaryOpNode(val op: UnaryOp, val child: ExpressionASTNode) : ExpressionASTNode() {
        override fun eval(vars: FloatArray, presetMem: MilkdropPresetMemory?): Float {
            val v = child.eval(vars, presetMem)
            return when (op) {
                UnaryOp.NEG -> -v
                UnaryOp.ABS -> abs(v)
                UnaryOp.SIN -> sin(v)
                UnaryOp.COS -> cos(v)
                UnaryOp.TAN -> tan(v)
                UnaryOp.SQRT -> if (v >= 0f) sqrt(v) else 0f
                UnaryOp.EXP -> exp(v.coerceIn(-25f, 25f))
                UnaryOp.LOG -> if (v > 0f) ln(v) else 0f
                UnaryOp.FLOOR -> floor(v)
                UnaryOp.CEIL -> ceil(v)
                UnaryOp.INT -> v.toInt().toFloat()
                UnaryOp.FRAC -> v - floor(v)
                UnaryOp.ASIN -> asin(v.coerceIn(-1f, 1f))
                UnaryOp.ACOS -> acos(v.coerceIn(-1f, 1f))
                UnaryOp.ATAN -> atan(v)
                UnaryOp.SQR -> v * v
                UnaryOp.SIGN -> if (v > 0f) 1f else if (v < 0f) -1f else 0f
                UnaryOp.NOT -> if (v <= 0f) 1f else 0f
                UnaryOp.BNOT -> if (v.toInt() == 0) 1f else 0f
                UnaryOp.RAND -> (Math.random() * abs(v)).toFloat()
            }
        }
    }

    data class BinaryOpNode(val op: BinaryOp, val left: ExpressionASTNode, val right: ExpressionASTNode) : ExpressionASTNode() {
        override fun eval(vars: FloatArray, presetMem: MilkdropPresetMemory?): Float {
            val l = left.eval(vars, presetMem)
            val r = right.eval(vars, presetMem)
            return when (op) {
                BinaryOp.ADD -> l + r
                BinaryOp.SUB -> l - r
                BinaryOp.MUL -> l * r
                BinaryOp.DIV -> if (r != 0f) l / r else 0f
                BinaryOp.MOD -> if (r != 0f) l % r else 0f
                BinaryOp.POW -> if (l >= 0f || r.toInt().toFloat() == r) l.pow(r) else 0f
                BinaryOp.MIN -> min(l, r)
                BinaryOp.MAX -> max(l, r)
                BinaryOp.ATAN2 -> atan2(l, r)
                BinaryOp.GREATER -> if (l > r) 1f else 0f
                BinaryOp.GREATER_EQUAL -> if (l >= r) 1f else 0f
                BinaryOp.LESS -> if (l < r) 1f else 0f
                BinaryOp.LESS_EQUAL -> if (l <= r) 1f else 0f
                BinaryOp.EQUAL -> if (abs(l - r) < 0.00001f) 1f else 0f
                BinaryOp.NOT_EQUAL -> if (abs(l - r) >= 0.00001f) 1f else 0f
                BinaryOp.AND -> if (l > 0f && r > 0f) 1f else 0f
                BinaryOp.OR -> if (l > 0f || r > 0f) 1f else 0f
                BinaryOp.BAND -> (l.toInt() and r.toInt()).toFloat()
                BinaryOp.BOR -> (l.toInt() or r.toInt()).toFloat()
                BinaryOp.BXOR -> (l.toInt() xor r.toInt()).toFloat()
                BinaryOp.SIGMOID -> 1f / (1f + exp((-l * r).coerceIn(-25f, 25f)))
                BinaryOp.EXEC2 -> r
            }
        }
    }

    data class IfConditionNode(
        val cond: ExpressionASTNode,
        val trueBranch: ExpressionASTNode,
        val falseBranch: ExpressionASTNode
    ) : ExpressionASTNode() {
        override fun eval(vars: FloatArray, presetMem: MilkdropPresetMemory?): Float {
            return if (cond.eval(vars, presetMem) > 0f) trueBranch.eval(vars, presetMem) else falseBranch.eval(vars, presetMem)
        }
    }

    data class ClampNode(
        val expr: ExpressionASTNode,
        val minExpr: ExpressionASTNode,
        val maxExpr: ExpressionASTNode
    ) : ExpressionASTNode() {
        override fun eval(vars: FloatArray, presetMem: MilkdropPresetMemory?): Float {
            val v = expr.eval(vars, presetMem)
            val minV = minExpr.eval(vars, presetMem)
            val maxV = maxExpr.eval(vars, presetMem)
            return v.coerceIn(minV, maxV)
        }
    }

    data class Exec3Node(
        val a: ExpressionASTNode,
        val b: ExpressionASTNode,
        val c: ExpressionASTNode
    ) : ExpressionASTNode() {
        override fun eval(vars: FloatArray, presetMem: MilkdropPresetMemory?): Float {
            a.eval(vars, presetMem)
            b.eval(vars, presetMem)
            return c.eval(vars, presetMem)
        }
    }

    data class UnsupportedFunctionNode(
        val funcName: String,
        val args: List<ExpressionASTNode>
    ) : ExpressionASTNode() {
        override fun eval(vars: FloatArray, presetMem: MilkdropPresetMemory?): Float {
            // Safely evaluate args to allow side-effects if any, return 0f
            for (arg in args) {
                arg.eval(vars, presetMem)
            }
            return 0f
        }
    }
}

enum class UnaryOp {
    NEG, ABS, SIN, COS, TAN, SQRT, EXP, LOG, FLOOR, CEIL, INT, FRAC, ASIN, ACOS, ATAN, SQR, SIGN, NOT, BNOT, RAND
}

enum class BinaryOp {
    ADD, SUB, MUL, DIV, MOD, POW, MIN, MAX, ATAN2,
    GREATER, GREATER_EQUAL, LESS, LESS_EQUAL, EQUAL, NOT_EQUAL,
    AND, OR, BAND, BOR, BXOR, SIGMOID, EXEC2
}

/**
 * =========================================================================================
 * OBSIDIAN HIGH-PRECISION MILKDROP EXPRESSION COMPILER
 * =========================================================================================
 */
class MilkdropExpressionCompiler {

    companion object {
        private const val TAG = "MilkdropExpressionCompiler"
        private const val MAX_LOOP_ITERATIONS = 100_000

        /**
         * Compiles a collection of script lines into a MilkdropProgramIR with structured diagnostics.
         */
        fun compileProgram(
            scriptLines: List<String>,
            baseDiagnostics: MutableList<PresetDiagnostic> = mutableListOf(),
            scope: MilkdropVariableScope = MilkdropVariableScope.PER_FRAME
        ): MilkdropProgramIR {
            if (scriptLines.isEmpty()) {
                return MilkdropProgramIR(emptyList(), emptyList(), emptyMap(), 256)
            }

            val nameToSlotMap = HashMap<String, Int>()
            val variableNames = mutableListOf<String>()

            fun getOrCreateSlot(name: String): Int {
                val lower = name.lowercase().trim()
                return nameToSlotMap.getOrPut(lower) {
                    val slot = variableNames.size
                    variableNames.add(lower)
                    slot
                }
            }

            // Pre-register standard Milkdrop Q1..Q32 and T1..T8 and common telemetry globals
            for (q in 1..32) getOrCreateSlot("q$q")
            for (t in 1..8) getOrCreateSlot("t$t")
            getOrCreateSlot("time")
            getOrCreateSlot("frame")
            getOrCreateSlot("fps")
            getOrCreateSlot("bass")
            getOrCreateSlot("mid")
            getOrCreateSlot("treb")
            getOrCreateSlot("bass_att")
            getOrCreateSlot("mid_att")
            getOrCreateSlot("treb_att")
            getOrCreateSlot("zoom")
            getOrCreateSlot("zoomexp")
            getOrCreateSlot("rot")
            getOrCreateSlot("warp")
            getOrCreateSlot("cx")
            getOrCreateSlot("cy")
            getOrCreateSlot("dx")
            getOrCreateSlot("dy")
            getOrCreateSlot("sx")
            getOrCreateSlot("sy")
            getOrCreateSlot("decay")

            val statements = mutableListOf<CompiledStatement>()

            for ((lineIdx, rawLine) in scriptLines.withIndex()) {
                val line = rawLine.trim()
                if (line.isEmpty() || line.startsWith("//") || line.startsWith("#") || line.startsWith("/*")) continue

                // Check for loops: loop(N, ...)
                if (line.startsWith("loop(", ignoreCase = true) || line.startsWith("loop (", ignoreCase = true)) {
                    val loopStmt = parseLoopStatement(line, lineIdx + 1, baseDiagnostics, ::getOrCreateSlot)
                    if (loopStmt != null) {
                        statements.add(loopStmt)
                        continue
                    }
                }

                // Split statements separated by semicolons
                val subStatements = splitStatementsPreservingLoops(line)

                for (stmt in subStatements) {
                    val compiledStmt = parseStatement(stmt, lineIdx + 1, baseDiagnostics, ::getOrCreateSlot)
                    if (compiledStmt != null) {
                        statements.add(compiledStmt)
                    }
                }
            }

            return MilkdropProgramIR(
                statements = statements,
                variableNames = variableNames,
                nameToSlotMap = nameToSlotMap,
                totalSlots = max(variableNames.size, 256)
            )
        }

        private fun splitStatementsPreservingLoops(line: String): List<String> {
            val result = mutableListOf<String>()
            var parenDepth = 0
            var cur = StringBuilder()

            for (c in line) {
                if (c == '(') parenDepth++
                else if (c == ')') parenDepth = max(0, parenDepth - 1)

                if (c == ';' && parenDepth == 0) {
                    val s = cur.toString().trim()
                    if (s.isNotEmpty()) result.add(s)
                    cur = StringBuilder()
                } else {
                    cur.append(c)
                }
            }
            val s = cur.toString().trim()
            if (s.isNotEmpty()) result.add(s)
            return result
        }

        private fun parseStatement(
            stmt: String,
            lineNum: Int,
            diagnostics: MutableList<PresetDiagnostic>,
            slotLookup: (String) -> Int
        ): CompiledStatement? {
            val trimmed = stmt.trim()
            if (trimmed.isEmpty()) return null

            // Check if this is a loop statement
            if (trimmed.startsWith("loop(", ignoreCase = true) || trimmed.startsWith("loop (", ignoreCase = true)) {
                return parseLoopStatement(trimmed, lineNum, diagnostics, slotLookup)
            }

            var assignOp = AssignmentOperator.SET
            val splitIdx = when {
                trimmed.contains("+=") -> { assignOp = AssignmentOperator.ADD; trimmed.indexOf("+=") }
                trimmed.contains("-=") -> { assignOp = AssignmentOperator.SUB; trimmed.indexOf("-=") }
                trimmed.contains("*=") -> { assignOp = AssignmentOperator.MUL; trimmed.indexOf("*=") }
                trimmed.contains("/=") -> { assignOp = AssignmentOperator.DIV; trimmed.indexOf("/=") }
                trimmed.contains("=") && !trimmed.contains("==") && !trimmed.contains("<=") && !trimmed.contains(">=") && !trimmed.contains("!=") -> {
                    assignOp = AssignmentOperator.SET
                    trimmed.indexOf("=")
                }
                else -> -1
            }

            if (splitIdx <= 0) {
                // Not an assignment. Could be a standalone function call or expression
                val parsedExpr = parseExpression(trimmed, lineNum, diagnostics, slotLookup)
                return CompiledStatement.Assignment(
                    targetSlot = -1,
                    targetName = "",
                    expr = parsedExpr,
                    op = AssignmentOperator.SET
                )
            }

            val opLen = if (assignOp == AssignmentOperator.SET) 1 else 2
            val targetStr = trimmed.substring(0, splitIdx).trim().lowercase()
            val exprStr = trimmed.substring(splitIdx + opLen).trim()

            if (targetStr.isEmpty() || exprStr.isEmpty()) {
                diagnostics.add(
                    PresetDiagnostic(
                        DiagnosticSeverity.WARNING,
                        lineNum,
                        targetStr,
                        "Malformed assignment target or empty expression in '$trimmed'"
                    )
                )
                return null
            }

            val parsedExpr = foldConstants(parseExpression(exprStr, lineNum, diagnostics, slotLookup))

            // Check if target is gmegabuf(index) or megabuf(index)
            if (targetStr.startsWith("gmegabuf(") && targetStr.endsWith(")")) {
                val indexStr = targetStr.substring("gmegabuf(".length, targetStr.length - 1).trim()
                val indexExpr = foldConstants(parseExpression(indexStr, lineNum, diagnostics, slotLookup))
                return CompiledStatement.GMegabufWrite(indexExpr, parsedExpr, assignOp)
            } else if (targetStr.startsWith("megabuf(") && targetStr.endsWith(")")) {
                val indexStr = targetStr.substring("megabuf(".length, targetStr.length - 1).trim()
                val indexExpr = foldConstants(parseExpression(indexStr, lineNum, diagnostics, slotLookup))
                return CompiledStatement.MegabufWrite(indexExpr, parsedExpr, assignOp)
            }

            val targetSlot = slotLookup(targetStr)
            return CompiledStatement.Assignment(
                targetSlot = targetSlot,
                targetName = targetStr,
                expr = parsedExpr,
                op = assignOp
            )
        }

        private fun parseLoopStatement(
            line: String,
            lineNum: Int,
            diagnostics: MutableList<PresetDiagnostic>,
            slotLookup: (String) -> Int
        ): CompiledStatement.LoopStatement? {
            val startIdx = line.indexOf('(')
            if (startIdx < 0 || !line.endsWith(")")) return null

            val inner = line.substring(startIdx + 1, line.length - 1).trim()
            val commaIdx = inner.indexOf(',')
            if (commaIdx <= 0) return null

            val countStr = inner.substring(0, commaIdx).trim()
            val bodyStr = inner.substring(commaIdx + 1).trim()

            val countExpr = foldConstants(parseExpression(countStr, lineNum, diagnostics, slotLookup))
            val bodyStatements = mutableListOf<CompiledStatement>()

            val subStmts = splitStatementsPreservingLoops(bodyStr)
            for (sub in subStmts) {
                val stmt = parseStatement(sub, lineNum, diagnostics, slotLookup)
                if (stmt != null) bodyStatements.add(stmt)
            }

            return CompiledStatement.LoopStatement(countExpr, bodyStatements)
        }

        fun parseExpression(
            exprStr: String,
            lineNum: Int,
            diagnostics: MutableList<PresetDiagnostic>,
            slotLookup: (String) -> Int
        ): ExpressionASTNode {
            return try {
                val tokens = tokenize(exprStr)
                val parser = ASTParser(tokens, lineNum, diagnostics, slotLookup)
                parser.parse()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                diagnostics.add(
                    PresetDiagnostic(
                        DiagnosticSeverity.ERROR,
                        lineNum,
                        exprStr,
                        "Syntax error compiling expression: ${e.message}"
                    )
                )
                ExpressionASTNode.ConstantNode(0f)
            }
        }

        fun foldConstants(node: ExpressionASTNode): ExpressionASTNode {
            return when (node) {
                is ExpressionASTNode.ConstantNode -> node
                is ExpressionASTNode.VariableNode -> node
                is ExpressionASTNode.GMegabufReadNode -> {
                    val folded = foldConstants(node.indexExpr)
                    ExpressionASTNode.GMegabufReadNode(folded)
                }
                is ExpressionASTNode.MegabufReadNode -> {
                    val folded = foldConstants(node.indexExpr)
                    ExpressionASTNode.MegabufReadNode(folded)
                }
                is ExpressionASTNode.UnaryOpNode -> {
                    val child = foldConstants(node.child)
                    if (child is ExpressionASTNode.ConstantNode && node.op != UnaryOp.RAND) {
                        ExpressionASTNode.ConstantNode(ExpressionASTNode.UnaryOpNode(node.op, child).eval(FloatArray(0)))
                    } else {
                        ExpressionASTNode.UnaryOpNode(node.op, child)
                    }
                }
                is ExpressionASTNode.BinaryOpNode -> {
                    val left = foldConstants(node.left)
                    val right = foldConstants(node.right)
                    if (left is ExpressionASTNode.ConstantNode && right is ExpressionASTNode.ConstantNode && node.op != BinaryOp.EXEC2) {
                        ExpressionASTNode.ConstantNode(ExpressionASTNode.BinaryOpNode(node.op, left, right).eval(FloatArray(0)))
                    } else {
                        ExpressionASTNode.BinaryOpNode(node.op, left, right)
                    }
                }
                is ExpressionASTNode.IfConditionNode -> {
                    val cond = foldConstants(node.cond)
                    val t = foldConstants(node.trueBranch)
                    val f = foldConstants(node.falseBranch)
                    if (cond is ExpressionASTNode.ConstantNode) {
                        if (cond.value > 0f) t else f
                    } else {
                        ExpressionASTNode.IfConditionNode(cond, t, f)
                    }
                }
                is ExpressionASTNode.ClampNode -> {
                    val e = foldConstants(node.expr)
                    val mn = foldConstants(node.minExpr)
                    val mx = foldConstants(node.maxExpr)
                    if (e is ExpressionASTNode.ConstantNode && mn is ExpressionASTNode.ConstantNode && mx is ExpressionASTNode.ConstantNode) {
                        ExpressionASTNode.ConstantNode(e.value.coerceIn(mn.value, mx.value))
                    } else {
                        ExpressionASTNode.ClampNode(e, mn, mx)
                    }
                }
                is ExpressionASTNode.Exec3Node -> {
                    ExpressionASTNode.Exec3Node(
                        foldConstants(node.a),
                        foldConstants(node.b),
                        foldConstants(node.c)
                    )
                }
                is ExpressionASTNode.UnsupportedFunctionNode -> node
            }
        }

        private fun tokenize(input: String): List<String> {
            val result = mutableListOf<String>()
            var i = 0
            val len = input.length
            while (i < len) {
                val c = input[i]
                when {
                    c.isWhitespace() -> i++
                    c == '&' && i + 1 < len && input[i + 1] == '&' -> { result.add("&&"); i += 2 }
                    c == '|' && i + 1 < len && input[i + 1] == '|' -> { result.add("||"); i += 2 }
                    c == '=' && i + 1 < len && input[i + 1] == '=' -> { result.add("=="); i += 2 }
                    c == '!' && i + 1 < len && input[i + 1] == '=' -> { result.add("!="); i += 2 }
                    c == '<' && i + 1 < len && input[i + 1] == '=' -> { result.add("<="); i += 2 }
                    c == '>' && i + 1 < len && input[i + 1] == '=' -> { result.add(">="); i += 2 }
                    c in "+-*/%^(),<>!&|" -> {
                        result.add(c.toString())
                        i++
                    }
                    c.isDigit() || (c == '.' && i + 1 < len && input[i + 1].isDigit()) -> {
                        val start = i
                        while (i < len && (input[i].isDigit() || input[i] == '.' || input[i] == 'e' || input[i] == 'E' || ((input[i] == '+' || input[i] == '-') && (input[i - 1] == 'e' || input[i - 1] == 'E')))) i++
                        result.add(input.substring(start, i))
                    }
                    c.isLetter() || c == '_' || c == '$' -> {
                        val start = i
                        while (i < len && (input[i].isLetterOrDigit() || input[i] == '_' || input[i] == '$')) i++
                        result.add(input.substring(start, i))
                    }
                    else -> i++
                }
            }
            return result
        }

        /**
         * Recursive descent parser with correct MilkDrop operator precedence.
         */
        private class ASTParser(
            private val tokens: List<String>,
            private val lineNum: Int,
            private val diagnostics: MutableList<PresetDiagnostic>,
            private val slotLookup: (String) -> Int
        ) {
            private var pos = 0

            fun parse(): ExpressionASTNode {
                return parseLogicalOr()
            }

            private fun peek(): String? = if (pos < tokens.size) tokens[pos] else null
            private fun consume(): String = if (pos < tokens.size) tokens[pos++] else ""

            private fun parseLogicalOr(): ExpressionASTNode {
                var left = parseLogicalAnd()
                while (peek() == "||" || peek() == "|") {
                    val opStr = consume()
                    val right = parseLogicalAnd()
                    val op = if (opStr == "|") BinaryOp.BOR else BinaryOp.OR
                    left = ExpressionASTNode.BinaryOpNode(op, left, right)
                }
                return left
            }

            private fun parseLogicalAnd(): ExpressionASTNode {
                var left = parseEquality()
                while (peek() == "&&" || peek() == "&") {
                    val opStr = consume()
                    val right = parseEquality()
                    val op = if (opStr == "&") BinaryOp.BAND else BinaryOp.AND
                    left = ExpressionASTNode.BinaryOpNode(op, left, right)
                }
                return left
            }

            private fun parseEquality(): ExpressionASTNode {
                var left = parseRelational()
                while (true) {
                    val p = peek()
                    if (p == "==" || p == "!=") {
                        val opStr = consume()
                        val right = parseRelational()
                        val op = if (opStr == "==") BinaryOp.EQUAL else BinaryOp.NOT_EQUAL
                        left = ExpressionASTNode.BinaryOpNode(op, left, right)
                    } else {
                        break
                    }
                }
                return left
            }

            private fun parseRelational(): ExpressionASTNode {
                var left = parseAddSub()
                while (true) {
                    val p = peek()
                    if (p == "<" || p == ">" || p == "<=" || p == ">=") {
                        val opStr = consume()
                        val right = parseAddSub()
                        val op = when (opStr) {
                            "<" -> BinaryOp.LESS
                            ">" -> BinaryOp.GREATER
                            "<=" -> BinaryOp.LESS_EQUAL
                            else -> BinaryOp.GREATER_EQUAL
                        }
                        left = ExpressionASTNode.BinaryOpNode(op, left, right)
                    } else {
                        break
                    }
                }
                return left
            }

            private fun parseAddSub(): ExpressionASTNode {
                var left = parseMulDiv()
                while (true) {
                    val p = peek()
                    if (p == "+" || p == "-") {
                        val opStr = consume()
                        val right = parseMulDiv()
                        val op = if (opStr == "+") BinaryOp.ADD else BinaryOp.SUB
                        left = ExpressionASTNode.BinaryOpNode(op, left, right)
                    } else {
                        break
                    }
                }
                return left
            }

            private fun parseMulDiv(): ExpressionASTNode {
                var left = parsePower()
                while (true) {
                    val p = peek()
                    if (p == "*" || p == "/" || p == "%") {
                        val opStr = consume()
                        val right = parsePower()
                        val op = when (opStr) {
                            "*" -> BinaryOp.MUL
                            "/" -> BinaryOp.DIV
                            else -> BinaryOp.MOD
                        }
                        left = ExpressionASTNode.BinaryOpNode(op, left, right)
                    } else {
                        break
                    }
                }
                return left
            }

            private fun parsePower(): ExpressionASTNode {
                var left = parseUnary()
                while (peek() == "^") {
                    consume()
                    val right = parseUnary()
                    left = ExpressionASTNode.BinaryOpNode(BinaryOp.POW, left, right)
                }
                return left
            }

            private fun parseUnary(): ExpressionASTNode {
                val p = peek()
                if (p == "-") {
                    consume()
                    return ExpressionASTNode.UnaryOpNode(UnaryOp.NEG, parseUnary())
                }
                if (p == "+") {
                    consume()
                    return parseUnary()
                }
                if (p == "!") {
                    consume()
                    return ExpressionASTNode.UnaryOpNode(UnaryOp.NOT, parseUnary())
                }
                return parsePrimary()
            }

            private fun parsePrimary(): ExpressionASTNode {
                val token = if (pos < tokens.size) consume() else return ExpressionASTNode.ConstantNode(0f)

                val num = token.toFloatOrNull()
                if (num != null) return ExpressionASTNode.ConstantNode(num)

                if (token == "(") {
                    val expr = parseLogicalOr()
                    if (peek() == ")") consume()
                    return expr
                }

                // Check if this token is followed by '(' -> Function call or Array index
                if (peek() == "(") {
                    val funcName = token.lowercase()
                    consume() // '('
                    val args = mutableListOf<ExpressionASTNode>()
                    if (peek() != ")") {
                        args.add(parseLogicalOr())
                        while (peek() == ",") {
                            consume() // ','
                            args.add(parseLogicalOr())
                        }
                    }
                    if (peek() == ")") consume() // ')'

                    return when (funcName) {
                        "gmegabuf" -> ExpressionASTNode.GMegabufReadNode(args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) })
                        "megabuf" -> ExpressionASTNode.MegabufReadNode(args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) })

                        "sin" -> ExpressionASTNode.UnaryOpNode(UnaryOp.SIN, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) })
                        "cos" -> ExpressionASTNode.UnaryOpNode(UnaryOp.COS, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) })
                        "tan" -> ExpressionASTNode.UnaryOpNode(UnaryOp.TAN, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) })
                        "abs" -> ExpressionASTNode.UnaryOpNode(UnaryOp.ABS, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) })
                        "sqrt" -> ExpressionASTNode.UnaryOpNode(UnaryOp.SQRT, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) })
                        "exp" -> ExpressionASTNode.UnaryOpNode(UnaryOp.EXP, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) })
                        "log", "ln" -> ExpressionASTNode.UnaryOpNode(UnaryOp.LOG, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) })
                        "floor" -> ExpressionASTNode.UnaryOpNode(UnaryOp.FLOOR, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) })
                        "ceil" -> ExpressionASTNode.UnaryOpNode(UnaryOp.CEIL, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) })
                        "int" -> ExpressionASTNode.UnaryOpNode(UnaryOp.INT, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) })
                        "frac" -> ExpressionASTNode.UnaryOpNode(UnaryOp.FRAC, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) })
                        "sqr" -> ExpressionASTNode.UnaryOpNode(UnaryOp.SQR, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) })
                        "asin" -> ExpressionASTNode.UnaryOpNode(UnaryOp.ASIN, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) })
                        "acos" -> ExpressionASTNode.UnaryOpNode(UnaryOp.ACOS, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) })
                        "atan" -> ExpressionASTNode.UnaryOpNode(UnaryOp.ATAN, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) })
                        "sign" -> ExpressionASTNode.UnaryOpNode(UnaryOp.SIGN, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) })
                        "rand" -> ExpressionASTNode.UnaryOpNode(UnaryOp.RAND, args.getOrElse(0) { ExpressionASTNode.ConstantNode(1f) })
                        "bnot", "not" -> ExpressionASTNode.UnaryOpNode(UnaryOp.BNOT, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) })

                        "min" -> ExpressionASTNode.BinaryOpNode(BinaryOp.MIN, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) }, args.getOrElse(1) { ExpressionASTNode.ConstantNode(0f) })
                        "max" -> ExpressionASTNode.BinaryOpNode(BinaryOp.MAX, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) }, args.getOrElse(1) { ExpressionASTNode.ConstantNode(0f) })
                        "pow" -> ExpressionASTNode.BinaryOpNode(BinaryOp.POW, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) }, args.getOrElse(1) { ExpressionASTNode.ConstantNode(0f) })
                        "atan2" -> ExpressionASTNode.BinaryOpNode(BinaryOp.ATAN2, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) }, args.getOrElse(1) { ExpressionASTNode.ConstantNode(0f) })
                        "above" -> ExpressionASTNode.BinaryOpNode(BinaryOp.GREATER, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) }, args.getOrElse(1) { ExpressionASTNode.ConstantNode(0f) })
                        "below" -> ExpressionASTNode.BinaryOpNode(BinaryOp.LESS, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) }, args.getOrElse(1) { ExpressionASTNode.ConstantNode(0f) })
                        "equal" -> ExpressionASTNode.BinaryOpNode(BinaryOp.EQUAL, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) }, args.getOrElse(1) { ExpressionASTNode.ConstantNode(0f) })
                        "band" -> ExpressionASTNode.BinaryOpNode(BinaryOp.BAND, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) }, args.getOrElse(1) { ExpressionASTNode.ConstantNode(0f) })
                        "bor" -> ExpressionASTNode.BinaryOpNode(BinaryOp.BOR, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) }, args.getOrElse(1) { ExpressionASTNode.ConstantNode(0f) })
                        "bxor" -> ExpressionASTNode.BinaryOpNode(BinaryOp.BXOR, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) }, args.getOrElse(1) { ExpressionASTNode.ConstantNode(0f) })
                        "sigmoid" -> ExpressionASTNode.BinaryOpNode(BinaryOp.SIGMOID, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) }, args.getOrElse(1) { ExpressionASTNode.ConstantNode(1f) })
                        "exec2" -> ExpressionASTNode.BinaryOpNode(BinaryOp.EXEC2, args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) }, args.getOrElse(1) { ExpressionASTNode.ConstantNode(0f) })

                        "clamp" -> ExpressionASTNode.ClampNode(
                            args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) },
                            args.getOrElse(1) { ExpressionASTNode.ConstantNode(0f) },
                            args.getOrElse(2) { ExpressionASTNode.ConstantNode(1f) }
                        )

                        "exec3" -> ExpressionASTNode.Exec3Node(
                            args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) },
                            args.getOrElse(1) { ExpressionASTNode.ConstantNode(0f) },
                            args.getOrElse(2) { ExpressionASTNode.ConstantNode(0f) }
                        )

                        "if" -> ExpressionASTNode.IfConditionNode(
                            args.getOrElse(0) { ExpressionASTNode.ConstantNode(0f) },
                            args.getOrElse(1) { ExpressionASTNode.ConstantNode(0f) },
                            args.getOrElse(2) { ExpressionASTNode.ConstantNode(0f) }
                        )

                        else -> {
                            diagnostics.add(
                                PresetDiagnostic(
                                    DiagnosticSeverity.WARNING,
                                    lineNum,
                                    funcName,
                                    "Unsupported or custom MilkDrop function '$funcName()'. Using safe evaluation."
                                )
                            )
                            ExpressionASTNode.UnsupportedFunctionNode(funcName, args)
                        }
                    }
                }

                // Variable identifier
                val slot = slotLookup(token)
                return ExpressionASTNode.VariableNode(slot, token)
            }
        }

        /**
         * Fast-path executor for precompiled MilkdropProgramIR with zero allocations.
         */
        fun executeProgram(
            program: MilkdropProgramIR,
            registers: FloatArray,
            presetMem: MilkdropPresetMemory? = null
        ) {
            val statements = program.statements
            for (i in statements.indices) {
                executeStatement(statements[i], registers, presetMem)
            }
        }

        private fun executeStatement(
            stmt: CompiledStatement,
            registers: FloatArray,
            presetMem: MilkdropPresetMemory?
        ) {
            when (stmt) {
                is CompiledStatement.Assignment -> {
                    val slot = stmt.targetSlot
                    val evalVal = stmt.expr.eval(registers, presetMem)
                    if (slot >= 0 && slot < registers.size) {
                        when (stmt.op) {
                            AssignmentOperator.SET -> registers[slot] = evalVal
                            AssignmentOperator.ADD -> registers[slot] += evalVal
                            AssignmentOperator.SUB -> registers[slot] -= evalVal
                            AssignmentOperator.MUL -> registers[slot] *= evalVal
                            AssignmentOperator.DIV -> if (evalVal != 0f) registers[slot] /= evalVal
                        }
                    }
                }
                is CompiledStatement.GMegabufWrite -> {
                    val idx = stmt.indexExpr.eval(registers, presetMem)
                    val evalVal = stmt.expr.eval(registers, presetMem)
                    val currentVal = MilkdropGlobalMemory.read(idx)
                    val finalVal = when (stmt.op) {
                        AssignmentOperator.SET -> evalVal
                        AssignmentOperator.ADD -> currentVal + evalVal
                        AssignmentOperator.SUB -> currentVal - evalVal
                        AssignmentOperator.MUL -> currentVal * evalVal
                        AssignmentOperator.DIV -> if (evalVal != 0f) currentVal / evalVal else currentVal
                    }
                    MilkdropGlobalMemory.write(idx, finalVal)
                }
                is CompiledStatement.MegabufWrite -> {
                    val idx = stmt.indexExpr.eval(registers, presetMem)
                    val evalVal = stmt.expr.eval(registers, presetMem)
                    val currentVal = presetMem?.read(idx) ?: 0f
                    val finalVal = when (stmt.op) {
                        AssignmentOperator.SET -> evalVal
                        AssignmentOperator.ADD -> currentVal + evalVal
                        AssignmentOperator.SUB -> currentVal - evalVal
                        AssignmentOperator.MUL -> currentVal * evalVal
                        AssignmentOperator.DIV -> if (evalVal != 0f) currentVal / evalVal else currentVal
                    }
                    presetMem?.write(idx, finalVal)
                }
                is CompiledStatement.LoopStatement -> {
                    val rawCount = stmt.countExpr.eval(registers, presetMem).toInt()
                    val loopCount = rawCount.coerceIn(0, MAX_LOOP_ITERATIONS)
                    val body = stmt.body
                    for (iter in 0 until loopCount) {
                        for (bIdx in body.indices) {
                            executeStatement(body[bIdx], registers, presetMem)
                        }
                    }
                }
                is CompiledStatement.UnsupportedStatement -> {
                    // Safe no-op
                }
            }
        }

        /**
         * Legacy bridge for executing equations inside MilkdropFrameContext.
         */
        fun compile(lines: List<String>): CompiledLegacyProgram {
            val diagnostics = mutableListOf<PresetDiagnostic>()
            val prog = compileProgram(lines, diagnostics, MilkdropVariableScope.PER_FRAME)
            return CompiledLegacyProgram(prog)
        }
    }
}

class CompiledLegacyProgram(val programIR: MilkdropProgramIR) {
    private val scratchRegisters = FloatArray(256)

    fun execute(ctx: MilkdropFrameContext) {
        val nameMap = programIR.nameToSlotMap
        for ((name, slot) in nameMap) {
            when (name) {
                "time" -> scratchRegisters[slot] = ctx.time
                "frame" -> scratchRegisters[slot] = ctx.frame.toFloat()
                "bass" -> scratchRegisters[slot] = ctx.bass
                "mid" -> scratchRegisters[slot] = ctx.mid
                "treb" -> scratchRegisters[slot] = ctx.treb
                "zoom" -> scratchRegisters[slot] = ctx.zoom
                "rot" -> scratchRegisters[slot] = ctx.rot
                "warp" -> scratchRegisters[slot] = ctx.warp
                "dx" -> scratchRegisters[slot] = ctx.dx
                "dy" -> scratchRegisters[slot] = ctx.dy
                "sx" -> scratchRegisters[slot] = ctx.sx
                "sy" -> scratchRegisters[slot] = ctx.sy
                "decay" -> scratchRegisters[slot] = ctx.decay
                else -> {
                    if (name.startsWith("q") && name.substring(1).toIntOrNull() in 1..32) {
                        val qIdx = name.substring(1).toInt() - 1
                        scratchRegisters[slot] = ctx.qVars[qIdx]
                    }
                }
            }
        }

        MilkdropExpressionCompiler.executeProgram(programIR, scratchRegisters)

        for ((name, slot) in nameMap) {
            val v = scratchRegisters[slot]
            when (name) {
                "zoom" -> ctx.zoom = v
                "rot" -> ctx.rot = v
                "warp" -> ctx.warp = v
                "dx" -> ctx.dx = v
                "dy" -> ctx.dy = v
                "sx" -> ctx.sx = v
                "sy" -> ctx.sy = v
                "decay" -> ctx.decay = v
                else -> {
                    if (name.startsWith("q") && name.substring(1).toIntOrNull() in 1..32) {
                        val qIdx = name.substring(1).toInt() - 1
                        ctx.qVars[qIdx] = v
                    }
                }
            }
        }
    }
}
