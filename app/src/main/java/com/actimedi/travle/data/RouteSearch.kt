package com.actimedi.travle.data

import java.util.PriorityQueue

/** 무엇을 아끼는 탐색인지. */
enum class SearchGoal {
    /** 총 소요 시간이 가장 짧은 길. */
    FASTEST,

    /** 갈아타는 횟수가 가장 적은 길. 같은 환승 수면 그 중 빠른 것. */
    FEWEST_TRANSFERS,
}

/** 한 노선을 계속 타고 가는 구간. */
data class SearchLeg(
    val line: String,
    val stations: List<Int>,
) {
    val hops: Int get() = (stations.size - 1).coerceAtLeast(0)
}

data class SearchResult(
    val goal: SearchGoal,
    val legs: List<SearchLeg>,
    val minutes: Int,
    val transfers: Int,
)

/**
 * 노선망 위에서 길을 찾는다.
 *
 * 상태는 (역, 타고 있는 노선)이다. 역만으로 상태를 잡으면 환승 비용을 표현할 수 없다.
 * 비용은 [TravelTimes]의 추정 모델을 그대로 쓴다 — 역당 [TravelTimes.MINUTES_PER_HOP]분,
 * 갈아탈 때 [TravelTimes.DEFAULT_TRANSFER_WAIT]분. 시각표가 없으므로 결과도 예상치다.
 *
 * [SearchGoal.FEWEST_TRANSFERS]는 환승 한 번에 [TRANSFER_WEIGHT]분에 해당하는 벌점을
 * 매겨, 환승을 줄이려 크게 돌아가는 길까지는 고르지 않게 한다.
 */
object RouteSearch {

    /** 최소 환승 탐색에서 환승 한 번에 매기는 벌점(분). */
    const val TRANSFER_WEIGHT = 120

    private data class Node(val station: Int, val line: String?)

    fun find(network: SubwayNetwork, fromName: String, toName: String, goal: SearchGoal): SearchResult? {
        val from = network.findStation(fromName) ?: return null
        val to = network.findStation(toName) ?: return null
        if (from == to) return SearchResult(goal, emptyList(), 0, 0)

        val adjacency = network.adjacency()
        val start = Node(from, null)

        val cost = mutableMapOf(start to 0)
        val minutes = mutableMapOf(start to 0)
        val transfers = mutableMapOf(start to 0)
        val cameFrom = mutableMapOf<Node, Pair<Node, String>>()
        val queue = PriorityQueue<Pair<Int, Node>>(compareBy { it.first })
        queue += 0 to start

        var best: Node? = null
        while (queue.isNotEmpty()) {
            val (spent, node) = queue.poll()
            if (spent > (cost[node] ?: Int.MAX_VALUE)) continue
            if (node.station == to) {
                best = node
                break
            }

            adjacency[node.station].orEmpty().forEach { edge ->
                val changed = node.line != null && node.line != edge.line
                val rideMinutes = TravelTimes.MINUTES_PER_HOP
                val waitMinutes = if (changed) TravelTimes.DEFAULT_TRANSFER_WAIT else 0
                val penalty = if (changed && goal == SearchGoal.FEWEST_TRANSFERS) TRANSFER_WEIGHT else 0

                val next = Node(edge.to, edge.line)
                val nextCost = spent + rideMinutes + waitMinutes + penalty
                if (nextCost >= (cost[next] ?: Int.MAX_VALUE)) return@forEach

                cost[next] = nextCost
                minutes[next] = (minutes[node] ?: 0) + rideMinutes + waitMinutes
                transfers[next] = (transfers[node] ?: 0) + if (changed) 1 else 0
                cameFrom[next] = node to edge.line
                queue += nextCost to next
            }
        }

        val end = best ?: return null
        return SearchResult(
            goal = goal,
            legs = rebuild(cameFrom, end),
            minutes = minutes[end] ?: 0,
            transfers = transfers[end] ?: 0,
        )
    }

    /** 같은 노선이 이어지는 동안 한 구간으로 묶어 되돌린다. */
    private fun rebuild(cameFrom: Map<Node, Pair<Node, String>>, end: Node): List<SearchLeg> {
        val steps = mutableListOf<Triple<Int, Int, String>>()   // from, to, line
        var current = end
        while (true) {
            val (previous, line) = cameFrom[current] ?: break
            steps += Triple(previous.station, current.station, line)
            current = previous
        }
        steps.reverse()
        if (steps.isEmpty()) return emptyList()

        val legs = mutableListOf<SearchLeg>()
        var line = steps.first().third
        var stations = mutableListOf(steps.first().first)
        steps.forEach { (_, to, stepLine) ->
            if (stepLine != line) {
                legs += SearchLeg(line, stations)
                line = stepLine
                stations = mutableListOf(stations.last())
            }
            stations += to
        }
        legs += SearchLeg(line, stations)
        return legs
    }
}

/** 인접한 역 하나. */
data class NetworkEdge(val to: Int, val line: String)

/**
 * 역 → 인접 역 목록. 노선 경로에서 이웃한 쌍을 양방향으로 편다.
 *
 * 노선망은 앱이 사는 동안 바뀌지 않으므로 한 번만 만든다.
 */
private val adjacencyCache = HashMap<SubwayNetwork, List<List<NetworkEdge>>>()

fun SubwayNetwork.adjacency(): List<List<NetworkEdge>> = adjacencyCache.getOrPut(this) {
    val out = List(stations.size) { mutableListOf<NetworkEdge>() }
    lines.forEach { line ->
        line.paths.forEach { path ->
            path.zipWithNext { a, b ->
                out[a] += NetworkEdge(b, line.name)
                out[b] += NetworkEdge(a, line.name)
            }
        }
    }
    out.map { it.distinct() }
}
