package com.example.data.entertainment

import com.example.data.model.EntertainmentDifficulty
import com.example.data.model.EntertainmentGameType
import com.example.data.model.EntertainmentPuzzleEntity
import com.example.data.model.SudokuPuzzleData
import org.json.JSONObject
import kotlin.random.Random

object SudokuGenerator {

    fun generateAllLevels(): List<EntertainmentPuzzleEntity> {
        val result = mutableListOf<EntertainmentPuzzleEntity>()
        val now = System.currentTimeMillis()
        for (difficulty in EntertainmentDifficulty.entries) {
            for (level in 1..30) {
                val data = buildSudokuFor(difficulty, level)
                val sizeLabel = "${data.size}×${data.size}"
                val id = "sudoku_${difficulty.key.lowercase()}_$level"
                result.add(
                    EntertainmentPuzzleEntity(
                        id = id,
                        gameType = EntertainmentGameType.SUDOKU.key,
                        difficulty = difficulty.key,
                        levelNumber = level,
                        title = "سودوکو ${difficulty.titleFa} ($sizeLabel) - مرحله $level",
                        payloadJson = serializeSudoku(data),
                        updatedAtEpochMs = now
                    )
                )
            }
        }
        return result
    }

    fun buildSudokuFor(difficulty: EntertainmentDifficulty, levelNumber: Int): SudokuPuzzleData {
        val seed = (difficulty.order * 31337L) + (levelNumber * 104729L)
        val rng = Random(seed)

        // First 15 Beginner stages use gentle 6x6 grids; remaining Beginner + all higher levels use classic 9x9
        val isMini6x6 = (difficulty == EntertainmentDifficulty.BEGINNER && levelNumber <= 15)
        val size = if (isMini6x6) 6 else 9
        val boxRows = if (isMini6x6) 2 else 3
        val boxCols = 3

        val fullSolution = generateValidSolutionGrid(size, boxRows, boxCols, rng)
        val givensCount = when (difficulty) {
            EntertainmentDifficulty.BEGINNER -> if (isMini6x6) 20 - (levelNumber % 4) else 46 - (levelNumber % 4)
            EntertainmentDifficulty.INTERMEDIATE -> 38 - (levelNumber % 4)
            EntertainmentDifficulty.ADVANCED -> 32 - (levelNumber % 3)
            EntertainmentDifficulty.MASTER -> 27 - (levelNumber % 3)
        }

        val puzzleGrid = fullSolution.copyOf()
        val totalCells = size * size
        val targetRemovals = (totalCells - givensCount).coerceIn(10, totalCells - 16)

        // Remove cells in balanced pairs while ensuring every row, col, and box keeps at least 1 clue
        val indices = (0 until totalCells).toMutableList().apply { shuffle(rng) }
        var removed = 0
        for (idx in indices) {
            if (removed >= targetRemovals) break
            if (puzzleGrid[idx] == 0) continue

            val backup = puzzleGrid[idx]
            puzzleGrid[idx] = 0

            val r = idx / size
            val c = idx % size
            val rowCount = (0 until size).count { puzzleGrid[r * size + it] != 0 }
            val colCount = (0 until size).count { puzzleGrid[it * size + c] != 0 }

            if (rowCount >= 1 && colCount >= 1) {
                // For 6x6 and Beginner/Intermediate, also verify unique solution
                if (size == 6 || removed < 44) {
                    if (countSolutions(puzzleGrid.copyOf(), size, boxRows, boxCols, limit = 2) == 1) {
                        removed++
                    } else {
                        puzzleGrid[idx] = backup
                    }
                } else {
                    removed++
                }
            } else {
                puzzleGrid[idx] = backup
            }
        }

        return SudokuPuzzleData(
            size = size,
            boxRows = boxRows,
            boxCols = boxCols,
            puzzle = puzzleGrid.joinToString(""),
            solution = fullSolution.joinToString("")
        )
    }

    /**
     * Generates a mathematically valid complete Sudoku solution via canonical pattern
     * plus structure-preserving permutations (symbols, rows within bands, cols within stacks, bands, stacks).
     */
    private fun generateValidSolutionGrid(
        size: Int,
        boxRows: Int,
        boxCols: Int,
        rng: Random
    ): IntArray {
        val base = IntArray(size * size)
        for (r in 0 until size) {
            for (c in 0 until size) {
                val patternVal = (boxCols * (r % boxRows) + r / boxRows + c) % size
                base[r * size + c] = patternVal + 1
            }
        }

        val digits = (1..size).toList().shuffled(rng)
        val bandCount = size / boxRows
        val stackCount = size / boxCols

        val bandOrder = (0 until bandCount).toList().shuffled(rng)
        val rowOrder = mutableListOf<Int>()
        for (b in bandOrder) {
            val rowsInBand = (0 until boxRows).map { b * boxRows + it }.shuffled(rng)
            rowOrder.addAll(rowsInBand)
        }

        val stackOrder = (0 until stackCount).toList().shuffled(rng)
        val colOrder = mutableListOf<Int>()
        for (s in stackOrder) {
            val colsInStack = (0 until boxCols).map { s * boxCols + it }.shuffled(rng)
            colOrder.addAll(colsInStack)
        }

        val result = IntArray(size * size)
        for (r in 0 until size) {
            for (c in 0 until size) {
                val origVal = base[rowOrder[r] * size + colOrder[c]]
                result[r * size + c] = digits[origVal - 1]
            }
        }
        return result
    }

    private fun countSolutions(
        grid: IntArray,
        size: Int,
        boxRows: Int,
        boxCols: Int,
        limit: Int
    ): Int {
        var bestIdx = -1
        var bestCandidates = Int.MAX_VALUE
        var bestMask = 0

        for (i in grid.indices) {
            if (grid[i] != 0) continue
            val mask = allowedMask(grid, i, size, boxRows, boxCols)
            val count = Integer.bitCount(mask)
            if (count == 0) return 0
            if (count < bestCandidates) {
                bestCandidates = count
                bestIdx = i
                bestMask = mask
                if (count == 1) break
            }
        }

        if (bestIdx == -1) return 1

        var total = 0
        for (d in 1..size) {
            if ((bestMask and (1 shl d)) != 0) {
                grid[bestIdx] = d
                total += countSolutions(grid, size, boxRows, boxCols, limit - total)
                grid[bestIdx] = 0
                if (total >= limit) return total
            }
        }
        return total
    }

    private fun allowedMask(
        grid: IntArray,
        idx: Int,
        size: Int,
        boxRows: Int,
        boxCols: Int
    ): Int {
        val r = idx / size
        val c = idx % size
        var used = 0
        for (i in 0 until size) {
            val rv = grid[r * size + i]
            if (rv != 0) used = used or (1 shl rv)
            val cv = grid[i * size + c]
            if (cv != 0) used = used or (1 shl cv)
        }
        val br = (r / boxRows) * boxRows
        val bc = (c / boxCols) * boxCols
        for (dr in 0 until boxRows) {
            for (dc in 0 until boxCols) {
                val bv = grid[(br + dr) * size + (bc + dc)]
                if (bv != 0) used = used or (1 shl bv)
            }
        }
        var allowed = 0
        for (d in 1..size) {
            if ((used and (1 shl d)) == 0) allowed = allowed or (1 shl d)
        }
        return allowed
    }

    fun isValidSolvedGrid(solution: String, size: Int, boxRows: Int, boxCols: Int): Boolean {
        if (solution.length != size * size) return false
        val nums = solution.map { it - '0' }
        if (nums.any { it !in 1..size }) return false
        val expectedSet = (1..size).toSet()
        for (r in 0 until size) {
            if ((0 until size).map { nums[r * size + it] }.toSet() != expectedSet) return false
        }
        for (c in 0 until size) {
            if ((0 until size).map { nums[it * size + c] }.toSet() != expectedSet) return false
        }
        for (br in 0 until size step boxRows) {
            for (bc in 0 until size step boxCols) {
                val boxNums = mutableSetOf<Int>()
                for (dr in 0 until boxRows) {
                    for (dc in 0 until boxCols) {
                        boxNums.add(nums[(br + dr) * size + (bc + dc)])
                    }
                }
                if (boxNums != expectedSet) return false
            }
        }
        return true
    }

    fun serializeSudoku(data: SudokuPuzzleData): String {
        return JSONObject().apply {
            put("size", data.size)
            put("boxRows", data.boxRows)
            put("boxCols", data.boxCols)
            put("puzzle", data.puzzle)
            put("solution", data.solution)
        }.toString()
    }

    fun deserializeSudoku(json: String): SudokuPuzzleData? {
        return try {
            val obj = JSONObject(json)
            SudokuPuzzleData(
                size = obj.getInt("size"),
                boxRows = obj.getInt("boxRows"),
                boxCols = obj.getInt("boxCols"),
                puzzle = obj.getString("puzzle"),
                solution = obj.getString("solution")
            )
        } catch (_: Exception) {
            null
        }
    }
}
