package com.example.data.entertainment

import com.example.data.model.CrosswordClue
import com.example.data.model.CrosswordPuzzleData
import com.example.data.model.EntertainmentDifficulty
import com.example.data.model.EntertainmentGameType
import com.example.data.model.EntertainmentPuzzleEntity
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

/**
 * Generates authentic 15×15 Classic Iranian Newspaper Crosswords («جدول کلاسیک روزنامه‌ای ۱۵×۱۵»):
 * - Dense 15×15 white grid (~75%–82% white playable cells) separated by crisp black divider squares.
 * - Every single horizontal run (len >= 2) and vertical run (len >= 2) is a genuine Persian word
 *   solved via an indexed MRV + Forward-Checking CSP solver over [PersianCrosswordLexicon].
 * - Zero isolated single-letter white cells; all 15 rows (1..15) and 15 columns (1..15) are active.
 * - Classic Iranian newspaper numbering: ACROSS clues carry their Row number (1..15), and
 *   DOWN clues carry their Column number (1..15), ordered naturally right-to-left & top-to-bottom.
 */
object PersianCrosswordGenerator {

    const val GRID_SIZE = 15

    private data class Slot(
        val id: Int,
        val row: Int,
        val col: Int,
        val length: Int,
        val horizontal: Boolean
    ) {
        fun cellRow(pos: Int): Int = if (horizontal) row else row + pos
        fun cellCol(pos: Int): Int = if (horizontal) col + pos else col
    }

    private data class Crossing(
        val myPos: Int,
        val otherSlotId: Int,
        val otherPos: Int
    )

    private class WordIndex(entries: List<CrosswordWordEntry>) {
        val byLength: Map<Int, List<CrosswordWordEntry>> = entries.groupBy { it.word.length }
        // (length, pos, char) -> list of matching entries
        private val byPosChar: Map<Int, Array<HashMap<Char, MutableList<CrosswordWordEntry>>>>

        init {
            val map = HashMap<Int, Array<HashMap<Char, MutableList<CrosswordWordEntry>>>>()
            for ((len, list) in byLength) {
                val posArr = Array(len) { HashMap<Char, MutableList<CrosswordWordEntry>>() }
                for (entry in list) {
                    val w = entry.word
                    for (p in 0 until len) {
                        posArr[p].getOrPut(w[p]) { ArrayList() }.add(entry)
                    }
                }
                map[len] = posArr
            }
            byPosChar = map
        }

        fun candidatesMatching(
            length: Int,
            grid: Array<CharArray>,
            slot: Slot,
            usedWords: Set<String>
        ): List<CrosswordWordEntry> {
            val posArr = byPosChar[length] ?: return emptyList()
            var smallestBucket: List<CrosswordWordEntry>? = null
            var hasConstraint = false
            for (p in 0 until length) {
                val ch = grid[slot.cellRow(p)][slot.cellCol(p)]
                if (ch != '.') {
                    hasConstraint = true
                    val bucket = posArr[p][ch] ?: return emptyList()
                    if (smallestBucket == null || bucket.size < smallestBucket.size) {
                        smallestBucket = bucket
                    }
                }
            }
            val base = if (hasConstraint) smallestBucket!! else (byLength[length] ?: return emptyList())
            val out = ArrayList<CrosswordWordEntry>(base.size.coerceAtMost(64))
            for (entry in base) {
                val w = entry.word
                if (w in usedWords) continue
                var ok = true
                for (p in 0 until length) {
                    val ch = grid[slot.cellRow(p)][slot.cellCol(p)]
                    if (ch != '.' && ch != w[p]) {
                        ok = false
                        break
                    }
                }
                if (ok) out.add(entry)
            }
            return out
        }

        fun hasAnyCandidate(
            length: Int,
            grid: Array<CharArray>,
            slot: Slot,
            usedWords: Set<String>
        ): Boolean {
            val posArr = byPosChar[length] ?: return false
            var smallestBucket: List<CrosswordWordEntry>? = null
            var hasConstraint = false
            for (p in 0 until length) {
                val ch = grid[slot.cellRow(p)][slot.cellCol(p)]
                if (ch != '.') {
                    hasConstraint = true
                    val bucket = posArr[p][ch] ?: return false
                    if (smallestBucket == null || bucket.size < smallestBucket.size) {
                        smallestBucket = bucket
                    }
                }
            }
            val base = if (hasConstraint) smallestBucket!! else (byLength[length] ?: return false)
            for (entry in base) {
                val w = entry.word
                if (w in usedWords) continue
                var ok = true
                for (p in 0 until length) {
                    val ch = grid[slot.cellRow(p)][slot.cellCol(p)]
                    if (ch != '.' && ch != w[p]) {
                        ok = false
                        break
                    }
                }
                if (ok) return true
            }
            return false
        }
    }

    private val FULL_WORD_INDEX: WordIndex by lazy {
        WordIndex(PersianCrosswordLexicon.ALL_WORDS)
    }

    private val DIFFICULTY_INDICES: Map<EntertainmentDifficulty, WordIndex> by lazy {
        EntertainmentDifficulty.entries.associateWith { diff ->
            val maxDiff = when (diff) {
                EntertainmentDifficulty.BEGINNER -> 2
                EntertainmentDifficulty.INTERMEDIATE -> 2
                EntertainmentDifficulty.ADVANCED -> 3
                EntertainmentDifficulty.MASTER -> 4
            }
            val filtered = PersianCrosswordLexicon.ALL_WORDS.filter { it.minDifficulty <= maxDiff }
            // Ensure abundant 2-letter and 3-letter connecting words even on Beginner
            val enriched = if (filtered.count { it.word.length == 3 } < 280) {
                (filtered + PersianCrosswordLexicon.ALL_WORDS.filter { it.word.length in 2..3 }).distinctBy { it.word }
            } else {
                filtered
            }
            WordIndex(enriched)
        }
    }

    fun normalizeWord(raw: String): String = PersianCrosswordLexicon.normalizeWord(raw)

    fun generateAllLevels(): List<EntertainmentPuzzleEntity> {
        val result = mutableListOf<EntertainmentPuzzleEntity>()
        val now = System.currentTimeMillis()
        for (difficulty in EntertainmentDifficulty.entries) {
            for (level in 1..30) {
                val puzzleData = buildCrosswordFor(difficulty, level)
                val id = "crossword_${difficulty.key.lowercase()}_$level"
                result.add(
                    EntertainmentPuzzleEntity(
                        id = id,
                        gameType = EntertainmentGameType.CROSSWORD.key,
                        difficulty = difficulty.key,
                        levelNumber = level,
                        title = "جدول کلاسیک 15×15 ${difficulty.titleFa}",
                        payloadJson = serializeCrossword(puzzleData),
                        updatedAtEpochMs = now
                    )
                )
            }
        }
        return result
    }

    /**
     * Builds a classic dense 15×15 Iranian newspaper crossword puzzle where every single
     * horizontal and vertical run (length >= 2) is a real Persian word with its authentic clue.
     */
    fun buildCrosswordFor(difficulty: EntertainmentDifficulty, levelNumber: Int): CrosswordPuzzleData {
        val primaryIndex = DIFFICULTY_INDICES[difficulty] ?: FULL_WORD_INDEX
        val templateOrder = buildTemplateCandidatesFor(difficulty, levelNumber)

        for ((idx, mask) in templateOrder.withIndex()) {
            val seed = (difficulty.order * 31337L) + (levelNumber * 7919L) + (idx * 977L)
            val solved = solveNewspaperMask(mask, primaryIndex, Random(seed), stepLimitPerComponent = 6500)
                ?: solveNewspaperMask(mask, FULL_WORD_INDEX, Random(seed + 41L), stepLimitPerComponent = 6500)
            if (solved != null) {
                return solved
            }
        }

        // Guaranteed fast fallback using ultra-reliable classic 3-wide diagonal staircase
        val safeMask = sanitizeMask(buildDiagonalMask(setOf(3, 7, 11, 15, 19, 23, 27), mirrored = false))
        return solveNewspaperMask(
            safeMask,
            FULL_WORD_INDEX,
            Random(levelNumber * 1009L + difficulty.order * 53L),
            stepLimitPerComponent = 15000
        ) ?: error("Unable to construct 15x15 classic crossword")
    }

    /**
     * Produces varied authentic Iranian newspaper 15×15 black-square masks (both `/` and `\`
     * diagonal staircase patterns, mixing 3-letter & 4-letter bands with ~76%–80% white cells).
     */
    private fun buildTemplateCandidatesFor(
        difficulty: EntertainmentDifficulty,
        levelNumber: Int
    ): List<Array<BooleanArray>> {
        val mirrored = ((levelNumber + difficulty.order) % 2) == 1
        val variant = (levelNumber + difficulty.order * 3) % 6

        val mixedTemplates = listOf(
            // Mix of 4-letter and 3-letter diagonal bands (~50 black cells = 77.8% white!)
            buildDiagonalMask(setOf(4, 8, 13, 17, 22, 26), mirrored = mirrored),
            buildDiagonalMask(setOf(3, 8, 12, 17, 21, 26), mirrored = mirrored),
            buildDiagonalMask(setOf(4, 9, 13, 18, 22, 26), mirrored = !mirrored),
            buildDiagonalMask(setOf(2, 6, 11, 15, 20, 24), mirrored = mirrored),
            buildDiagonalMask(setOf(3, 7, 12, 16, 21, 25), mirrored = !mirrored),
            buildDiagonalMask(setOf(2, 7, 11, 16, 20, 25), mirrored = mirrored)
        )
        val pureThreeTemplates = listOf(
            buildDiagonalMask(setOf(3, 7, 11, 15, 19, 23, 27), mirrored = mirrored),
            buildDiagonalMask(setOf(2, 6, 10, 14, 18, 22, 26), mirrored = !mirrored),
            buildDiagonalMask(setOf(1, 5, 9, 13, 17, 21, 25), mirrored = mirrored)
        )

        val ordered = mutableListOf<Array<BooleanArray>>()
        // Prioritize the rotated mixed 4+3 template first, then another mixed variant, then reliable 3-wide
        ordered.add(sanitizeMask(mixedTemplates[variant % mixedTemplates.size]))
        ordered.add(sanitizeMask(mixedTemplates[(variant + 1) % mixedTemplates.size]))
        ordered.add(sanitizeMask(pureThreeTemplates[variant % pureThreeTemplates.size]))
        ordered.add(sanitizeMask(pureThreeTemplates[(variant + 1) % pureThreeTemplates.size]))
        return ordered
    }

    /**
     * Builds a 15×15 boolean mask where `true` = black divider square `#`, `false` = white cell.
     */
    private fun buildDiagonalMask(blackDiagonals: Set<Int>, mirrored: Boolean): Array<BooleanArray> {
        return Array(GRID_SIZE) { r ->
            BooleanArray(GRID_SIZE) { c ->
                val diag = if (mirrored) r + (GRID_SIZE - 1 - c) else r + c
                diag in blackDiagonals
            }
        }
    }

    /**
     * Ensures zero isolated 1-letter white cells (every white cell belongs to at least one
     * horizontal or vertical word of length >= 2).
     */
    private fun sanitizeMask(rawMask: Array<BooleanArray>): Array<BooleanArray> {
        val mask = Array(GRID_SIZE) { r -> rawMask[r].clone() }
        var changed = true
        while (changed) {
            changed = false
            for (r in 0 until GRID_SIZE) {
                for (c in 0 until GRID_SIZE) {
                    if (mask[r][c]) continue
                    val hasHorizNeighbor = (c > 0 && !mask[r][c - 1]) || (c + 1 < GRID_SIZE && !mask[r][c + 1])
                    val hasVertNeighbor = (r > 0 && !mask[r - 1][c]) || (r + 1 < GRID_SIZE && !mask[r + 1][c])
                    if (!hasHorizNeighbor && !hasVertNeighbor) {
                        mask[r][c] = true
                        changed = true
                    }
                }
            }
        }
        return mask
    }

    private fun extractSlots(mask: Array<BooleanArray>): List<Slot> {
        val slots = mutableListOf<Slot>()
        var nextId = 0

        // Horizontal slots (Across)
        for (r in 0 until GRID_SIZE) {
            var c = 0
            while (c < GRID_SIZE) {
                while (c < GRID_SIZE && mask[r][c]) c++
                val start = c
                while (c < GRID_SIZE && !mask[r][c]) c++
                val len = c - start
                if (len >= 2) {
                    slots.add(Slot(nextId++, r, start, len, horizontal = true))
                }
            }
        }

        // Vertical slots (Down)
        for (c in 0 until GRID_SIZE) {
            var r = 0
            while (r < GRID_SIZE) {
                while (r < GRID_SIZE && mask[r][c]) r++
                val start = r
                while (r < GRID_SIZE && !mask[r][c]) r++
                val len = r - start
                if (len >= 2) {
                    slots.add(Slot(nextId++, start, c, len, horizontal = false))
                }
            }
        }
        return slots
    }

    private fun solveNewspaperMask(
        mask: Array<BooleanArray>,
        wordIndex: WordIndex,
        rng: Random,
        stepLimitPerComponent: Int
    ): CrosswordPuzzleData? {
        val slots = extractSlots(mask)
        if (slots.isEmpty()) return null

        // Build cell -> slots incidence map and crossing adjacency
        val cellToSlots = HashMap<Pair<Int, Int>, MutableList<Pair<Int, Int>>>()
        for (slot in slots) {
            for (p in 0 until slot.length) {
                cellToSlots.getOrPut(slot.cellRow(p) to slot.cellCol(p)) { mutableListOf() }
                    .add(slot.id to p)
            }
        }

        val crossings = Array(slots.size) { mutableListOf<Crossing>() }
        for (occupants in cellToSlots.values) {
            if (occupants.size == 2) {
                val (idA, posA) = occupants[0]
                val (idB, posB) = occupants[1]
                crossings[idA].add(Crossing(posA, idB, posB))
                crossings[idB].add(Crossing(posB, idA, posA))
            }
        }

        // Partition slots into independent connected components (diagonal bands)
        val visited = BooleanArray(slots.size)
        val components = mutableListOf<List<Int>>()
        for (sId in slots.indices) {
            if (visited[sId]) continue
            val comp = mutableListOf<Int>()
            val queue = ArrayDeque<Int>()
            queue.add(sId)
            visited[sId] = true
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                comp.add(cur)
                for (cr in crossings[cur]) {
                    if (!visited[cr.otherSlotId]) {
                        visited[cr.otherSlotId] = true
                        queue.add(cr.otherSlotId)
                    }
                }
            }
            components.add(comp)
        }

        val grid = Array(GRID_SIZE) { r ->
            CharArray(GRID_SIZE) { c -> if (mask[r][c]) '#' else '.' }
        }
        val assigned = arrayOfNulls<CrosswordWordEntry>(slots.size)
        val globalUsedWords = HashSet<String>()

        // Solve larger components first so they have the widest available vocabulary
        val orderedComponents = components.sortedByDescending { it.size }
        for (comp in orderedComponents) {
            val compUsed = HashSet(globalUsedWords)
            val steps = IntArray(1)
            val ok = solveComponentMrv(
                compSlotIds = comp,
                slots = slots,
                crossings = crossings,
                grid = grid,
                assigned = assigned,
                usedWords = compUsed,
                wordIndex = wordIndex,
                rng = rng,
                steps = steps,
                stepLimit = stepLimitPerComponent
            )
            if (!ok) return null
            for (sId in comp) {
                assigned[sId]?.let { globalUsedWords.add(it.word) }
            }
        }

        // Verify no unassigned white cells remain
        for (r in 0 until GRID_SIZE) {
            for (c in 0 until GRID_SIZE) {
                if (grid[r][c] == '.') return null
            }
        }

        // Build classic Iranian newspaper clues:
        // ACROSS -> number = row + 1 (1..15), ordered right-to-left (col ascending in RTL)
        // DOWN   -> number = col + 1 (1..15), ordered top-to-bottom (row ascending)
        val clues = slots.mapNotNull { slot ->
            val entry = assigned[slot.id] ?: return@mapNotNull null
            CrosswordClue(
                number = if (slot.horizontal) slot.row + 1 else slot.col + 1,
                direction = if (slot.horizontal) "ACROSS" else "DOWN",
                row = slot.row,
                col = slot.col,
                answer = entry.word,
                clue = entry.clue
            )
        }.sortedWith(
            compareBy<CrosswordClue> { it.direction }
                .thenBy { it.number }
                .thenBy { if (it.direction == "ACROSS") it.col else it.row }
        )

        val solutionStrings = grid.map { String(it) }
        return CrosswordPuzzleData(
            rows = GRID_SIZE,
            cols = GRID_SIZE,
            solutionGrid = solutionStrings,
            clues = clues
        )
    }

    private fun solveComponentMrv(
        compSlotIds: List<Int>,
        slots: List<Slot>,
        crossings: Array<MutableList<Crossing>>,
        grid: Array<CharArray>,
        assigned: Array<CrosswordWordEntry?>,
        usedWords: HashSet<String>,
        wordIndex: WordIndex,
        rng: Random,
        steps: IntArray,
        stepLimit: Int
    ): Boolean {
        if (++steps[0] > stepLimit) return false

        // MRV: Pick the unassigned slot with the FEWEST valid candidates
        var bestSlotId = -1
        var bestCandidates: List<CrosswordWordEntry>? = null

        for (sId in compSlotIds) {
            if (assigned[sId] != null) continue
            val slot = slots[sId]
            val cands = wordIndex.candidatesMatching(slot.length, grid, slot, usedWords)
            if (cands.isEmpty()) return false
            if (bestCandidates == null || cands.size < bestCandidates.size) {
                bestCandidates = cands
                bestSlotId = sId
                if (cands.size == 1) break
            }
        }

        if (bestSlotId == -1) return true // All slots in this component solved!

        val slot = slots[bestSlotId]
        val shuffledCandidates = bestCandidates!!.toMutableList()
        shuffledCandidates.shuffle(rng)

        // Cap candidate branching slightly on unconstrained root words to keep solve blazing fast
        val limit = if (shuffledCandidates.size > 48) 48 else shuffledCandidates.size

        // Save current grid characters along this slot so we can backtrack cleanly
        val savedChars = CharArray(slot.length) { p -> grid[slot.cellRow(p)][slot.cellCol(p)] }

        for (i in 0 until limit) {
            val entry = shuffledCandidates[i]
            val w = entry.word

            // Place word
            for (p in 0 until slot.length) {
                grid[slot.cellRow(p)][slot.cellCol(p)] = w[p]
            }
            assigned[bestSlotId] = entry
            usedWords.add(w)

            // Forward check every unassigned crossing slot immediately
            var forwardOk = true
            for (cr in crossings[bestSlotId]) {
                val otherId = cr.otherSlotId
                if (assigned[otherId] != null) continue
                val otherSlot = slots[otherId]
                if (!wordIndex.hasAnyCandidate(otherSlot.length, grid, otherSlot, usedWords)) {
                    forwardOk = false
                    break
                }
            }

            if (forwardOk) {
                if (solveComponentMrv(
                        compSlotIds, slots, crossings, grid, assigned, usedWords, wordIndex, rng, steps, stepLimit
                    )
                ) {
                    return true
                }
            }

            // Undo placement
            usedWords.remove(w)
            assigned[bestSlotId] = null
            for (p in 0 until slot.length) {
                grid[slot.cellRow(p)][slot.cellCol(p)] = savedChars[p]
            }
            if (steps[0] > stepLimit) return false
        }

        return false
    }

    fun serializeCrossword(data: CrosswordPuzzleData): String {
        val gridArr = JSONArray()
        data.solutionGrid.forEach { gridArr.put(it) }
        val cluesArr = JSONArray()
        data.clues.forEach { clue ->
            cluesArr.put(
                JSONObject().apply {
                    put("number", clue.number)
                    put("direction", clue.direction)
                    put("row", clue.row)
                    put("col", clue.col)
                    put("answer", clue.answer)
                    put("clue", clue.clue)
                }
            )
        }
        return JSONObject().apply {
            put("rows", data.rows)
            put("cols", data.cols)
            put("grid", gridArr)
            put("clues", cluesArr)
        }.toString()
    }

    fun deserializeCrossword(json: String): CrosswordPuzzleData? {
        return try {
            val obj = JSONObject(json)
            val rows = obj.getInt("rows")
            val cols = obj.getInt("cols")
            val gridArr = obj.getJSONArray("grid")
            val grid = List(gridArr.length()) { gridArr.getString(it) }
            val cluesArr = obj.getJSONArray("clues")
            val clues = List(cluesArr.length()) { idx ->
                val c = cluesArr.getJSONObject(idx)
                CrosswordClue(
                    number = c.getInt("number"),
                    direction = c.getString("direction"),
                    row = c.getInt("row"),
                    col = c.getInt("col"),
                    answer = c.getString("answer"),
                    clue = c.getString("clue")
                )
            }
            CrosswordPuzzleData(rows = rows, cols = cols, solutionGrid = grid, clues = clues)
        } catch (_: Exception) {
            null
        }
    }
}
