package com.greendynasty.football.economy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.greendynasty.football.economy.model.EconomyIndexSnapshot
import com.greendynasty.football.economy.model.FinancialWarningLevel
import com.greendynasty.football.economy.model.LeagueEconomySnapshot
import com.greendynasty.football.economy.ui.state.EconomyUiState
import com.greendynasty.football.economy.ui.viewmodel.EconomyViewModel

/**
 * T17 经济概览页（UI 入口）。
 *
 * 展示内容：
 * - 顶部：当前经济指数卡片（4 字段：global / transfer_fee / wage / commercial）
 * - 中部：通胀趋势图（1992 → 当前年份，自定义 Canvas 折线图）
 * - 下部 1：9 大联赛商业系数列表（按系数降序）
 * - 下部 2：玩家俱乐部财政健康卡片（4 档预警）
 *
 * 设计对齐 GrowthScreen 模式（Material3 / LazyColumn / Card）。
 */
@Composable
fun EconomyScreen(
    viewModel: EconomyViewModel = viewModel(factory = EconomyViewModel.factory(
        androidx.compose.ui.platform.LocalContext.current.applicationContext as android.app.Application
    ))
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold { padding ->
        when (val state = uiState) {
            is EconomyUiState.Loading -> LoadingView(padding)
            is EconomyUiState.Locked -> EmptyView(padding, state.reason)
            is EconomyUiState.Empty -> EmptyView(padding, state.reason)
            is EconomyUiState.Error -> ErrorView(padding, state.message)
            is EconomyUiState.Normal -> NormalView(padding, state)
        }
    }
}

// ==================== 视图组件 ====================

@Composable
private fun LoadingView(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyView(padding: PaddingValues, reason: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Text(text = reason, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorView(padding: PaddingValues, message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
private fun NormalView(padding: PaddingValues, state: EconomyUiState.Normal) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 顶部标题
        item {
            SectionTitle("经济概览（${state.currentDate}）")
        }

        // 当前经济指数卡片
        item {
            CurrentEconomyCard(state.currentIndex)
        }

        // 通胀趋势图
        if (state.trend.size >= 2) {
            item {
                SectionTitle("通胀趋势（1992 - ${state.currentYear}）")
                EconomyTrendChart(state.trend)
            }
        }

        // 9 大联赛商业系数
        if (state.leagueSnapshots.isNotEmpty()) {
            item {
                SectionTitle("联赛商业系数（9 大联赛）")
            }
            items(state.leagueSnapshots) { snapshot ->
                LeagueEconomyCard(snapshot)
            }
        }

        // 俱乐部财政健康
        if (state.healthReport != null && state.clubFinancial != null) {
            item {
                SectionTitle("俱乐部财政健康")
                ClubFinancialCard(state.clubFinancial, state.healthReport)
            }
        }

        // 底部占位
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

/**
 * 当前经济指数卡片（4 字段）。
 */
@Composable
private fun CurrentEconomyCard(snapshot: EconomyIndexSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFE3F2FD)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "当前年份 ${snapshot.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "数据来源：${sourceDisplayName(snapshot.source)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            // 4 字段网格
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IndexCell("全球经济", snapshot.globalIndex, Color(0xFF1565C0))
                IndexCell("转会费", snapshot.transferFeeIndex, Color(0xFF2E7D32))
                IndexCell("工资", snapshot.wageIndex, Color(0xFFE65100))
                IndexCell("商业", snapshot.commercialIndex, Color(0xFF6A1B9A))
            }
        }
    }
}

@Composable
private fun IndexCell(label: String, value: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = String.format("%.2f", value),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 通胀趋势折线图（自定义 Canvas）。
 *
 * X 轴：年份（1992 → 当前年份）
 * Y 轴：global_index（0 → max × 1.1）
 */
@Composable
private fun EconomyTrendChart(trend: List<EconomyIndexSnapshot>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(16.dp)
        ) {
            if (trend.size < 2) return@Canvas

            val maxIndex = trend.maxOf { it.globalIndex }.coerceAtLeast(1.0) * 1.1
            val minYear = trend.minOf { it.year }
            val maxYear = trend.maxOf { it.year }
            val yearRange = (maxYear - minYear).coerceAtLeast(1)

            val width = size.width
            val height = size.height

            // 坐标轴
            val axisColor = Color(0xFFBDBDBD)
            drawLine(
                color = axisColor,
                start = Offset(0f, height),
                end = Offset(width, height),
                strokeWidth = 2f
            )
            drawLine(
                color = axisColor,
                start = Offset(0f, 0f),
                end = Offset(0f, height),
                strokeWidth = 2f
            )

            // 2002 基准线（index = 1.0）
            val baselineY = height - (1.0f / maxIndex.toFloat()) * height
            drawLine(
                color = Color(0xFFFFC107),
                start = Offset(0f, baselineY),
                end = Offset(width, baselineY),
                strokeWidth = 1f,
                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                    floatArrayOf(10f, 10f)
                )
            )

            // 折线路径
            val lineColor = Color(0xFF1565C0)
            val fillPath = Path()
            val linePath = Path()
            trend.forEachIndexed { index, snapshot ->
                val x = ((snapshot.year - minYear).toFloat() / yearRange) * width
                val y = height - (snapshot.globalIndex.toFloat() / maxIndex.toFloat()) * height
                if (index == 0) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, height)
                    fillPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
                if (index == trend.size - 1) {
                    fillPath.lineTo(x, height)
                    fillPath.close()
                }
            }
            // 填充区域（半透明）
            drawPath(
                path = fillPath,
                color = lineColor.copy(alpha = 0.15f)
            )
            // 折线
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 3f)
            )

            // 数据点
            trend.forEach { snapshot ->
                val x = ((snapshot.year - minYear).toFloat() / yearRange) * width
                val y = height - (snapshot.globalIndex.toFloat() / maxIndex.toFloat()) * height
                drawCircle(
                    color = lineColor,
                    radius = 3f,
                    center = Offset(x, y)
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "X 轴：年份（${trend.first().year} - ${trend.last().year}）  Y 轴：全球经济指数  黄线 = 2002 基准（1.00）",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 联赛商业系数卡片。
 */
@Composable
private fun LeagueEconomyCard(snapshot: LeagueEconomySnapshot) {
    val multiplierColor = when {
        snapshot.multiplier >= 3.0 -> Color(0xFF1565C0)
        snapshot.multiplier >= 1.5 -> Color(0xFF2E7D32)
        snapshot.multiplier >= 0.8 -> Color(0xFFE65100)
        else -> Color(0xFF6A1B9A)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${snapshot.leagueName}（${snapshot.leagueId}）",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "基础 ${String.format("%.2f", snapshot.baseMultiplier)} · 增长 ${snapshot.growthType} · 来源 ${snapshot.source}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = String.format("%.2f", snapshot.multiplier),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = multiplierColor
            )
        }
    }
}

/**
 * 俱乐部财政健康卡片。
 */
@Composable
private fun ClubFinancialCard(
    financial: com.greendynasty.football.economy.model.ClubFinancialState,
    report: com.greendynasty.football.economy.model.FinancialHealthReport
) {
    val (bgColor, textColor) = when (report.level) {
        FinancialWarningLevel.HEALTHY -> Color(0xFFE8F5E9) to Color(0xFF2E7D32)
        FinancialWarningLevel.ACCEPTABLE -> Color(0xFFFFF9C4) to Color(0xFFF57F17)
        FinancialWarningLevel.RISK -> Color(0xFFFFF3E0) to Color(0xFFE65100)
        FinancialWarningLevel.HIGH_RISK -> Color(0xFFFFCDD2) to Color(0xFFB71C1C)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "工资/收入比",
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = "${String.format("%.1f", report.wageToIncomeRatio * 100)}%",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "等级：${report.level.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )
            Spacer(modifier = Modifier.height(8.dp))
            // 财政明细
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FinancialCell("转会预算", financial.transferBudget)
                FinancialCell("工资预算", financial.wageBudget)
                FinancialCell("余额", financial.balance)
                FinancialCell("年收入", financial.totalIncome)
            }
            // 建议
            if (report.recommendations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "建议措施：",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                report.recommendations.forEach { rec ->
                    Text(
                        text = "· $rec",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FinancialCell(label: String, value: Int) {
    val displayValue = if (value >= 1_000_000) {
        String.format("%.1fM", value / 1_000_000.0)
    } else if (value >= 1_000) {
        String.format("%.0fK", value / 1_000.0)
    } else {
        value.toString()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = displayValue,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 数据来源中文名 */
private fun sourceDisplayName(source: String): String = when (source) {
    "db" -> "存档表"
    "fixed" -> "固定表"
    "projected" -> "架空增长"
    else -> source
}
