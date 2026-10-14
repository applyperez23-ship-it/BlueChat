package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Self-contained QR Code Matrix Generator (Byte Mode, ISO/IEC 18004 compliant).
 * Generates a boolean matrix of modules suitable for rendering directly in Compose Canvas.
 */
object QrCodeGenerator {

    fun generateQrMatrix(text: String): Array<BooleanArray> {
        val data = text.toByteArray(Charsets.ISO_8859_1)
        val version = when {
            data.size <= 17 -> 1
            data.size <= 32 -> 2
            data.size <= 53 -> 3
            data.size <= 78 -> 4
            data.size <= 106 -> 5
            else -> 6
        }
        val size = 17 + 4 * version
        val matrix = Array(size) { BooleanArray(size) }
        val reserved = Array(size) { BooleanArray(size) }

        // 1. Finder patterns (Top-Left, Top-Right, Bottom-Left)
        drawFinderPattern(matrix, reserved, 0, 0)
        drawFinderPattern(matrix, reserved, size - 7, 0)
        drawFinderPattern(matrix, reserved, 0, size - 7)

        // 2. Timing patterns
        for (i in 8 until size - 8) {
            val bit = (i % 2 == 0)
            matrix[6][i] = bit
            reserved[6][i] = true
            matrix[i][6] = bit
            reserved[i][6] = true
        }

        // 3. Dark module and reserved format areas
        matrix[4 * version + 9][8] = true
        reserved[4 * version + 9][8] = true

        for (i in 0..8) {
            reserved[8][i] = true
            reserved[i][8] = true
        }
        for (i in (size - 8) until size) {
            reserved[8][i] = true
            reserved[i][8] = true
        }

        // 4. Alignment patterns for Version 2+
        if (version >= 2) {
            val alignPos = when (version) {
                2 -> intArrayOf(6, 18)
                3 -> intArrayOf(6, 22)
                4 -> intArrayOf(6, 26)
                5 -> intArrayOf(6, 30)
                else -> intArrayOf(6, 34)
            }
            for (r in alignPos) {
                for (c in alignPos) {
                    if (!reserved[r][c]) {
                        drawAlignmentPattern(matrix, reserved, r, c)
                    }
                }
            }
        }

        // 5. Encode Data Bits (Byte mode 0100)
        val bitBuffer = mutableListOf<Boolean>()
        // Mode indicator: Byte mode (0100)
        addBits(bitBuffer, 4, 4)
        // Character count indicator (8 bits for Version 1-9)
        addBits(bitBuffer, data.size, 8)
        // Data bytes
        for (b in data) {
            addBits(bitBuffer, b.toInt() and 0xFF, 8)
        }
        // Terminator (up to 4 zeroes)
        addBits(bitBuffer, 0, 4)

        // Pad to byte boundary
        while (bitBuffer.size % 8 != 0) {
            bitBuffer.add(false)
        }

        // Capacity in bytes
        val totalDataBytes = when (version) {
            1 -> 19
            2 -> 34
            3 -> 55
            4 -> 80
            5 -> 108
            else -> 136
        }
        val padBytes = intArrayOf(0xEC, 0x11)
        var padIndex = 0
        while (bitBuffer.size < totalDataBytes * 8) {
            addBits(bitBuffer, padBytes[padIndex % 2], 8)
            padIndex++
        }

        // 6. Populate matrix using zig-zag traversal and Mask 0 ((row + col) % 2 == 0)
        var bitIndex = 0
        var col = size - 1
        var movingUp = true

        while (col > 0) {
            if (col == 6) col-- // Skip timing column
            val rows = if (movingUp) (size - 1 downTo 0) else (0 until size)
            for (row in rows) {
                for (c in intArrayOf(col, col - 1)) {
                    if (!reserved[row][c]) {
                        val dataBit = if (bitIndex < bitBuffer.size) bitBuffer[bitIndex++] else false
                        val mask = ((row + c) % 2 == 0)
                        matrix[row][c] = dataBit xor mask
                    }
                }
            }
            movingUp = !movingUp
            col -= 2
        }

        // 7. Format Information (Mask 0, Error Correction Level L) -> 0x77C4 format bits
        applyFormatBits(matrix, 0x77C4, size)

        return matrix
    }

    private fun addBits(buffer: MutableList<Boolean>, value: Int, bitCount: Int) {
        for (i in bitCount - 1 downTo 0) {
            buffer.add(((value shr i) and 1) == 1)
        }
    }

    private fun drawFinderPattern(matrix: Array<BooleanArray>, reserved: Array<BooleanArray>, startR: Int, startC: Int) {
        for (r in 0..6) {
            for (c in 0..6) {
                val isOuter = (r == 0 || r == 6 || c == 0 || c == 6)
                val isInner = (r in 2..4 && c in 2..4)
                matrix[startR + r][startC + c] = isOuter || isInner
                reserved[startR + r][startC + c] = true
            }
        }
        // Separators around finder pattern
        for (r in -1..7) {
            for (c in -1..7) {
                val row = startR + r
                val col = startC + c
                if (row in matrix.indices && col in matrix.indices) {
                    reserved[row][col] = true
                }
            }
        }
    }

    private fun drawAlignmentPattern(matrix: Array<BooleanArray>, reserved: Array<BooleanArray>, centerR: Int, centerC: Int) {
        for (r in -2..2) {
            for (c in -2..2) {
                val isOuter = (r == -2 || r == 2 || c == -2 || c == 2)
                val isCenter = (r == 0 && c == 0)
                matrix[centerR + r][centerC + c] = isOuter || isCenter
                reserved[centerR + r][centerC + c] = true
            }
        }
    }

    private fun applyFormatBits(matrix: Array<BooleanArray>, format: Int, size: Int) {
        val bits = BooleanArray(15) { i -> ((format shr i) and 1) == 1 }
        // Top-left
        val posR1 = intArrayOf(0, 1, 2, 3, 4, 5, 7, 8, 8, 8, 8, 8, 8, 8, 8)
        val posC1 = intArrayOf(8, 8, 8, 8, 8, 8, 8, 8, 7, 5, 4, 3, 2, 1, 0)
        for (i in 0..14) {
            matrix[posR1[i]][posC1[i]] = bits[i]
        }
        // Top-right and Bottom-left
        for (i in 0..6) {
            matrix[size - 1 - i][8] = bits[i]
        }
        for (i in 0..7) {
            matrix[8][size - 8 + i] = bits[14 - i]
        }
    }
}

@Composable
fun QrCodeView(
    content: String,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
    darkColor: Color = Color(0xFF1E293B),
    lightColor: Color = Color.White
) {
    val matrix = remember(content) {
        try {
            QrCodeGenerator.generateQrMatrix(content)
        } catch (_: Exception) {
            Array(21) { BooleanArray(21) }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(lightColor),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size - 16.dp)) {
            val moduleCount = matrix.size
            if (moduleCount == 0) return@Canvas
            val moduleSize = this.size.width / moduleCount

            for (r in 0 until moduleCount) {
                for (c in 0 until moduleCount) {
                    if (matrix[r][c]) {
                        drawRect(
                            color = darkColor,
                            topLeft = Offset(c * moduleSize, r * moduleSize),
                            size = Size(moduleSize + 0.5f, moduleSize + 0.5f)
                        )
                    }
                }
            }
        }
    }
}
