package com.greendynasty.football.match.regression

import com.greendynasty.football.match.api.MatchInput
import com.greendynasty.football.match.api.MatchSimulator
import com.greendynasty.football.match.config.MatchConfig
import com.greendynasty.football.match.model.MatchResult
import com.greendynasty.football.match.template.StarTemplateRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * 批量模拟器（T02 方案 §十一 回归测试）
 *
 * 复用 [MatchSimulator.simulate] 接口，支持并行模拟大批量比赛，
 * 用于 Gate1 回归验收与统计分布校验。
 *
 * 支持两种模式：
 * 1. [simulateBatch]：模拟一组不同输入的比赛
 * 2. [simulateSameMatchNTimes]：同一输入重复模拟 N 次（验证分布稳定性）
 */
class BatchSimulator(
    private val config: MatchConfig = MatchConfig.DEFAULT,
    private val starTemplateRegistry: StarTemplateRegistry = StarTemplateRegistry()
) {

    /** 单批默认并行度 */
    private val defaultParallelism = 8

    /**
     * 批量模拟一组不同比赛（并行）。
     *
     * @param inputs 比赛输入列表
     * @param parallelism 最大并行度（默认 8）
     * @return 比赛结果列表，顺序与输入一致
     */
    suspend fun simulateBatch(
        inputs: List<MatchInput>,
        parallelism: Int = defaultParallelism
    ): List<MatchResult> = withContext(Dispatchers.Default) {
        if (inputs.isEmpty()) return@withContext emptyList()
        val safeParallelism = parallelism.coerceIn(1, max(1, inputs.size))

        // 分块并行：按 parallelism 切分输入，每块顺序执行
        val chunkSize = max(1, (inputs.size + safeParallelism - 1) / safeParallelism)
        val chunks = inputs.chunked(chunkSize)

        coroutineScope {
            chunks.map { chunk ->
                async {
                    chunk.map { input -> newSimulator().simulate(input) }
                }
            }.awaitAll().flatten()
        }
    }

    /**
     * 同一比赛重复模拟 N 次（验证分布稳定性，Gate1 核心）。
     *
     * 每次使用不同随机种子（input.randomSeed + i），保证样本独立。
     *
     * @param input 基础比赛输入
     * @param n 重复次数
     * @param parallelism 最大并行度（默认 8）
     * @return N 场比赛结果
     */
    suspend fun simulateSameMatchNTimes(
        input: MatchInput,
        n: Int,
        parallelism: Int = defaultParallelism
    ): List<MatchResult> {
        if (n <= 0) return emptyList()
        // 派生不同种子的输入：避免相同种子导致结果完全一致
        val inputs = (0 until n).map { i ->
            input.copy(randomSeed = input.randomSeed + i.toLong() * 7919L)
        }
        return simulateBatch(inputs, parallelism = min(parallelism, n))
    }

    /** 每次模拟创建独立 Simulator 实例（避免共享可变状态） */
    private fun newSimulator(): MatchSimulator =
        MatchSimulator(config = config, starTemplateRegistry = starTemplateRegistry)
}
