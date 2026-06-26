package com.rms.web

import com.rms.domain.ItemType
import com.rms.repo.ProjectRepository
import com.rms.service.TraceabilityService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.util.UUID

/**
 * The Phase 2 traceability views (PLAN §4, §12): the live matrix, the per-item ego graph,
 * impact analysis, and a project-wide gap dashboard. All read-only — every view is computed
 * from [TraceabilityService] on each request, so it always reflects the current link graph.
 */
@Controller
class TraceabilityController(
    private val traceability: TraceabilityService,
    private val projects: ProjectRepository,
) {
    /** Item types offered in the matrix axis selectors (the requirement hierarchy, Phase 1-2). */
    private val matrixTypes =
        listOf(ItemType.USER_NEED, ItemType.SYSTEM_REQUIREMENT, ItemType.SOFTWARE_REQUIREMENT)

    @GetMapping("/projects/{projectId}/traceability")
    fun dashboard(
        @PathVariable projectId: UUID,
        model: Model,
    ): String {
        model.addAttribute("project", projectOrThrow(projectId))
        model.addAttribute("gaps", traceability.gaps(projectId))
        return "traceability"
    }

    @GetMapping("/projects/{projectId}/matrix")
    fun matrix(
        @PathVariable projectId: UUID,
        @RequestParam(defaultValue = "USER_NEED") rowType: ItemType,
        @RequestParam(defaultValue = "SYSTEM_REQUIREMENT") colType: ItemType,
        model: Model,
    ): String {
        model.addAttribute("project", projectOrThrow(projectId))
        model.addAttribute("matrix", traceability.matrix(projectId, rowType, colType))
        model.addAttribute("itemTypes", matrixTypes)
        return "matrix"
    }

    @GetMapping("/items/{itemId}/graph")
    fun graph(
        @PathVariable itemId: UUID,
        model: Model,
    ): String {
        model.addAttribute("layout", GraphLayout.of(traceability.graph(itemId)))
        return "graph"
    }

    @GetMapping("/items/{itemId}/impact")
    fun impact(
        @PathVariable itemId: UUID,
        model: Model,
    ): String {
        model.addAttribute("impact", traceability.impact(itemId))
        return "impact"
    }

    private fun projectOrThrow(projectId: UUID) =
        projects.findById(projectId).orElseThrow { IllegalArgumentException("Unknown project $projectId") }

    /**
     * Lays the ego graph out as a simple, deterministic grid (one column per level, stacked
     * rows within a column) and renders it as plain server-side SVG — no client-side graphing
     * library, no build step, in keeping with the project's low-dependency front end (PLAN §8).
     */
    data class GraphLayout(
        val focusItemId: UUID,
        val width: Int,
        val height: Int,
        val nodes: List<PositionedNode>,
        val edges: List<PositionedEdge>,
    ) {
        data class PositionedNode(
            val itemId: UUID,
            val humanKey: String,
            val type: ItemType,
            val focus: Boolean,
            val x: Int,
            val y: Int,
        )

        data class PositionedEdge(
            val x1: Int,
            val y1: Int,
            val x2: Int,
            val y2: Int,
            val label: String,
            val suspect: Boolean,
        )

        companion object {
            private const val COLUMN_WIDTH = 220
            private const val ROW_HEIGHT = 70
            private const val NODE_HALF_WIDTH = 85
            private const val NODE_HALF_HEIGHT = 18
            private const val MARGIN = 60

            fun of(graph: TraceabilityService.Graph): GraphLayout {
                val byLevel = graph.nodes.groupBy { it.level }.toSortedMap()
                val minLevel = byLevel.keys.minOrNull() ?: 0
                val maxRows = byLevel.values.maxOfOrNull { it.size } ?: 1

                val x = mutableMapOf<UUID, Int>()
                val y = mutableMapOf<UUID, Int>()
                byLevel.forEach { (level, nodesAtLevel) ->
                    val colX = MARGIN + NODE_HALF_WIDTH + (level - minLevel) * COLUMN_WIDTH
                    nodesAtLevel.forEachIndexed { index, node ->
                        x[node.item.id] = colX
                        y[node.item.id] = MARGIN + NODE_HALF_HEIGHT + index * ROW_HEIGHT
                    }
                }

                val positionedNodes =
                    graph.nodes.map { n ->
                        PositionedNode(
                            itemId = n.item.id,
                            humanKey = n.item.humanKey,
                            type = n.item.type,
                            focus = n.focus,
                            x = x.getValue(n.item.id),
                            y = y.getValue(n.item.id),
                        )
                    }
                val positionedEdges =
                    graph.edges.map { e ->
                        PositionedEdge(
                            x1 = x.getValue(e.sourceItemId),
                            y1 = y.getValue(e.sourceItemId),
                            x2 = x.getValue(e.targetItemId),
                            y2 = y.getValue(e.targetItemId),
                            label = e.linkType.label,
                            suspect = e.suspect,
                        )
                    }

                val width = MARGIN * 2 + NODE_HALF_WIDTH * 2 + (byLevel.size - 1).coerceAtLeast(0) * COLUMN_WIDTH
                val height = MARGIN * 2 + NODE_HALF_HEIGHT * 2 + (maxRows - 1).coerceAtLeast(0) * ROW_HEIGHT
                return GraphLayout(graph.focusItemId, width, height, positionedNodes, positionedEdges)
            }
        }
    }
}
