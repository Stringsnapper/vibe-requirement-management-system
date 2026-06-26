package com.rms.service

import com.rms.domain.Item
import com.rms.domain.ItemType
import com.rms.domain.LinkType
import com.rms.repo.ItemRepository
import com.rms.repo.ItemRevisionRepository
import com.rms.repo.TraceLinkRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Real-time traceability views over the live link graph (PLAN §4, §12): the matrix, the ego
 * graph around a single item, project-wide orphan/gap detection, and impact analysis (the
 * transitive blast radius of a change). Nothing here is persisted — every view is derived, on
 * demand, from [com.rms.domain.TraceLink]s between items' *current* revisions, so it is always
 * live.
 */
@Service
class TraceabilityService(
    private val items: ItemRepository,
    private val revisions: ItemRevisionRepository,
    private val links: TraceLinkRepository,
) {
    /** A link between two items (resolved from their current revisions). */
    data class Edge(
        val sourceItemId: UUID,
        val targetItemId: UUID,
        val linkType: LinkType,
        val suspect: Boolean,
    )

    private data class ProjectGraph(
        val itemsById: Map<UUID, Item>,
        val outgoing: Map<UUID, List<Edge>>,
        val incoming: Map<UUID, List<Edge>>,
    )

    /** Builds the live item/link graph for a project from current revisions only. */
    private fun loadGraph(projectId: UUID): ProjectGraph {
        val projectItems = items.findByProjectIdOrderByHumanKeyAsc(projectId)
        val itemsById = projectItems.associateBy { it.id }
        val itemIdByCurrentRevId =
            revisions.findByItemIdInAndIsCurrentTrue(itemsById.keys).associate { it.id to it.itemId }

        val edges =
            links.findByProjectId(projectId).mapNotNull { link ->
                val sourceItemId = itemIdByCurrentRevId[link.sourceRevisionId] ?: return@mapNotNull null
                val targetItemId = itemIdByCurrentRevId[link.targetRevisionId] ?: return@mapNotNull null
                Edge(sourceItemId, targetItemId, link.linkType, link.suspect)
            }

        return ProjectGraph(
            itemsById = itemsById,
            outgoing = edges.groupBy { it.sourceItemId },
            incoming = edges.groupBy { it.targetItemId },
        )
    }

    // --- Traceability matrix ---

    data class MatrixCell(
        val linkTypes: Set<LinkType>,
        val suspect: Boolean,
    )

    data class Matrix(
        val rowType: ItemType,
        val colType: ItemType,
        val rows: List<Item>,
        val cols: List<Item>,
        val cells: Map<UUID, Map<UUID, MatrixCell>>,
        val uncoveredRowIds: Set<UUID>,
        val uncoveredColIds: Set<UUID>,
    )

    /**
     * A configurable grid between two item types (PLAN §4), e.g. User Needs × Software
     * Requirements. A cell shows the link type(s) connecting that row and column item,
     * regardless of which side is the trace-link source. Rows/columns with no cell at all are
     * surfaced as uncovered so gaps are visible directly in the grid.
     */
    @Transactional(readOnly = true)
    fun matrix(
        projectId: UUID,
        rowType: ItemType,
        colType: ItemType,
    ): Matrix {
        val graph = loadGraph(projectId)
        val rows =
            graph.itemsById.values
                .filter { it.type == rowType }
                .sortedBy { it.humanKey }
        val cols =
            graph.itemsById.values
                .filter { it.type == colType }
                .sortedBy { it.humanKey }
        val rowIds = rows.map { it.id }.toSet()
        val colIds = cols.map { it.id }.toSet()

        val cells = mutableMapOf<UUID, MutableMap<UUID, MatrixCell>>()
        val coveredRows = mutableSetOf<UUID>()
        val coveredCols = mutableSetOf<UUID>()

        fun record(
            rowId: UUID,
            colId: UUID,
            linkType: LinkType,
            suspect: Boolean,
        ) {
            val rowCells = cells.getOrPut(rowId) { mutableMapOf() }
            val existing = rowCells[colId]
            rowCells[colId] =
                if (existing == null) {
                    MatrixCell(setOf(linkType), suspect)
                } else {
                    MatrixCell(existing.linkTypes + linkType, existing.suspect || suspect)
                }
            coveredRows += rowId
            coveredCols += colId
        }

        val allEdges =
            graph.outgoing.values
                .flatten()
                .distinct()
        for (edge in allEdges) {
            if (edge.sourceItemId in rowIds && edge.targetItemId in colIds) {
                record(edge.sourceItemId, edge.targetItemId, edge.linkType, edge.suspect)
            } else if (edge.targetItemId in rowIds && edge.sourceItemId in colIds) {
                record(edge.targetItemId, edge.sourceItemId, edge.linkType, edge.suspect)
            }
        }

        return Matrix(
            rowType = rowType,
            colType = colType,
            rows = rows,
            cols = cols,
            cells = cells,
            uncoveredRowIds = rowIds - coveredRows,
            uncoveredColIds = colIds - coveredCols,
        )
    }

    // --- Ego graph ---

    data class GraphNode(
        val item: Item,
        val level: Int,
        val focus: Boolean,
    )

    data class Graph(
        val focusItemId: UUID,
        val nodes: List<GraphNode>,
        val edges: List<Edge>,
    )

    /**
     * Upstream and downstream neighbourhood of [itemId], out to [depth] hops in each direction —
     * the interactive node-link view of PLAN §4. Upstream items get negative levels, the focus
     * item level 0, downstream items positive levels.
     */
    @Transactional(readOnly = true)
    fun graph(
        itemId: UUID,
        depth: Int = 3,
    ): Graph {
        val item = items.findById(itemId).orElseThrow { IllegalArgumentException("Unknown item $itemId") }
        val graph = loadGraph(item.projectId)

        val levels = mutableMapOf(itemId to 0)

        fun expand(
            direction: Int,
            adjacency: (UUID) -> List<Edge>,
            other: (Edge) -> UUID,
        ) {
            var frontier = listOf(itemId)
            var hop = 0
            while (frontier.isNotEmpty() && hop < depth) {
                val next = mutableListOf<UUID>()
                for (id in frontier) {
                    for (edge in adjacency(id)) {
                        val otherId = other(edge)
                        if (otherId !in levels) {
                            levels[otherId] = (hop + 1) * direction
                            next += otherId
                        }
                    }
                }
                frontier = next
                hop++
            }
        }
        // Outgoing = "this item derives from / points at" → upstream (what it depends on).
        expand(-1, { graph.outgoing[it].orEmpty() }, { it.targetItemId })
        // Incoming = other items pointing at this one → downstream (what depends on it).
        expand(1, { graph.incoming[it].orEmpty() }, { it.sourceItemId })

        val nodes =
            levels
                .map { (id, level) -> GraphNode(graph.itemsById.getValue(id), level, id == itemId) }
                .sortedWith(compareBy({ it.level }, { it.item.humanKey }))

        val nodeIds = levels.keys
        val edges =
            (graph.outgoing.values.flatten() + graph.incoming.values.flatten())
                .distinct()
                .filter { it.sourceItemId in nodeIds && it.targetItemId in nodeIds }

        return Graph(itemId, nodes, edges)
    }

    // --- Impact analysis ---

    data class ImpactResult(
        val focus: Item,
        val upstream: List<Item>,
        val downstream: List<Item>,
    )

    /**
     * The full transitive blast radius of changing [itemId] (PLAN §4): every item downstream
     * that depends on it — candidates to re-review/re-verify — and every item upstream it
     * depends on, for context on why it exists.
     */
    @Transactional(readOnly = true)
    fun impact(itemId: UUID): ImpactResult {
        val item = items.findById(itemId).orElseThrow { IllegalArgumentException("Unknown item $itemId") }
        val graph = loadGraph(item.projectId)

        fun closure(
            adjacency: (UUID) -> List<Edge>,
            other: (Edge) -> UUID,
        ): List<Item> {
            val visited = mutableSetOf(itemId)
            val queue = ArrayDeque(listOf(itemId))
            val result = mutableListOf<Item>()
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                for (edge in adjacency(current)) {
                    val otherId = other(edge)
                    if (visited.add(otherId)) {
                        result += graph.itemsById.getValue(otherId)
                        queue += otherId
                    }
                }
            }
            return result
        }

        val downstream = closure({ graph.incoming[it].orEmpty() }, { it.sourceItemId })
        val upstream = closure({ graph.outgoing[it].orEmpty() }, { it.targetItemId })

        return ImpactResult(item, upstream, downstream)
    }

    // --- Project-wide orphan / gap detection ---

    data class Gap(
        val item: Item,
        val reason: String,
    )

    /**
     * Project-wide orphan and gap detection (PLAN §12) — the raw data behind a completeness
     * dashboard. Mirrors the per-item checks shown on an item's own page, but across every item
     * in the project so nothing is missed.
     */
    @Transactional(readOnly = true)
    fun gaps(projectId: UUID): List<Gap> {
        val graph = loadGraph(projectId)
        val gaps = mutableListOf<Gap>()
        for (item in graph.itemsById.values.sortedBy { it.humanKey }) {
            val current = revisions.findByItemIdAndIsCurrentTrue(item.id) ?: continue

            if (item.type != ItemType.USER_NEED &&
                graph.outgoing[item.id].orEmpty().none { it.linkType == LinkType.DERIVES_FROM }
            ) {
                gaps += Gap(item, "Orphan — does not derive from a parent requirement.")
            }
            if (current.acceptanceCriteria.isNullOrBlank()) {
                gaps += Gap(item, "No acceptance criteria — add measurable criteria so it is testable.")
            }
            if (item.type == ItemType.SOFTWARE_REQUIREMENT && current.softwareSafetyClass == null) {
                gaps += Gap(item, "No IEC 62304 software safety class assigned.")
            }
        }
        return gaps
    }
}
