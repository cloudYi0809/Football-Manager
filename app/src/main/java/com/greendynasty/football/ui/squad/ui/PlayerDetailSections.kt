package com.greendynasty.football.ui.squad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.greendynasty.football.ui.squad.data.AttributeItem
import com.greendynasty.football.ui.squad.data.BasicInfo
import com.greendynasty.football.ui.squad.data.ContractInfo
import com.greendynasty.football.ui.squad.data.GrowthCurvePoint
import com.greendynasty.football.ui.squad.data.InjuryRecord
import com.greendynasty.football.ui.squad.data.PlayerDetail
import com.greendynasty.football.ui.squad.data.PositionFit
import com.greendynasty.football.ui.squad.data.ScoutReport
import com.greendynasty.football.ui.squad.data.SeasonStats
import com.greendynasty.football.ui.squad.data.TrainingPlan
import com.greendynasty.football.ui.squad.data.TransferRecord

/**
 * 球员详情页各模块 Composable 集合（10 个模块）。
 *
 * 由 [PlayerDetailScreen] 按横向 Tab 调用对应 Section。
 */

/** 通用模块卡片容器 */
@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            content()
        }
    }
}

/** 1. 基础信息 */
@Composable
fun BasicInfoSection(info: BasicInfo) {
    SectionCard(title = "基础信息") {
        InfoRow("姓名", info.name)
        InfoRow("年龄", "${info.age} 岁")
        InfoRow("国籍", info.nationality + (info.secondNationality?.let { " / $it" } ?: ""))
        InfoRow("位置", info.position + (info.secondaryPositions.joinToString("").let { if (it.isBlank()) "" else " / $it" }))
        InfoRow("出生日期", info.birthDate ?: "-")
        InfoRow("身高", info.height?.let { "$it cm" } ?: "-")
        InfoRow("体重", info.weight?.let { "$it kg" } ?: "-")
        InfoRow("惯用脚", info.preferredFoot ?: "-")
        InfoRow("性格", info.personality ?: "-")
        InfoRow("梯队", info.squadTab.displayName)
    }
}

/** 2. 属性面板（含雷达图） */
@Composable
fun AttributesSection(detail: PlayerDetail) {
    val attrs = detail.attributes
    SectionCard(title = "属性面板") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("CA ${attrs.ca}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("PA ${attrs.pa}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        PlayerAttributeRadar(values = attrs.radarValues)
        Divider(modifier = Modifier.padding(vertical = 8.dp))
        AttributeGroup("技术", attrs.technical)
        AttributeGroup("身体", attrs.physical)
        AttributeGroup("防守", attrs.defending)
        AttributeGroup("精神", attrs.mental)
        if (attrs.goalkeeper.any { it.value > 0 }) {
            AttributeGroup("门将", attrs.goalkeeper)
        }
    }
}

/** 3. 位置适应 */
@Composable
fun PositionFitSection(positions: List<PositionFit>) {
    SectionCard(title = "位置适应") {
        if (positions.isEmpty()) {
            EmptyHint("暂无位置适应数据")
        } else {
            positions.forEach { fit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(fit.position)
                    Text("熟悉度 ${fit.familiarity}%", color = familiarityColor(fit.familiarity))
                }
            }
        }
    }
}

/** 4. 成长曲线 */
@Composable
fun GrowthCurveSection(points: List<GrowthCurvePoint>) {
    SectionCard(title = "成长曲线") {
        GrowthCurveChart(points = points)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LegendDot(color = androidx.compose.ui.graphics.Color(0xFF1B5E20), label = "CA 实线")
            LegendDot(color = androidx.compose.ui.graphics.Color(0xFFFFD700), label = "PA 虚线")
        }
    }
}

/** 5. 赛季数据 */
@Composable
fun SeasonStatsSection(stats: List<SeasonStats>) {
    SectionCard(title = "赛季数据") {
        if (stats.isEmpty()) {
            EmptyHint("暂无赛季统计")
        } else {
            stats.forEach { s ->
                Text(
                    text = "${s.seasonLabel}：${s.appearances}场 ${s.goals}球 ${s.assists}助 评分${s.avgRating}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

/** 6. 合同信息 */
@Composable
fun ContractInfoSection(contract: ContractInfo) {
    SectionCard(title = "合同信息") {
        InfoRow("合同到期", contract.contractUntil ?: "-")
        InfoRow("周薪", "${contract.wage}")
        InfoRow("身价", "${contract.marketValue}")
        InfoRow("是否挂牌", if (contract.isListed) "是" else "否")
        InfoRow("是否队长", if (contract.isCaptain) "是" else "否")
        InfoRow("外租状态", if (contract.isLoaned) "外租中" else "无")
    }
}

/** 7. 转会记录 */
@Composable
fun TransferHistorySection(history: List<TransferRecord>) {
    SectionCard(title = "转会记录") {
        if (history.isEmpty()) {
            EmptyHint("暂无转会记录")
        } else {
            history.forEach { t ->
                Text(
                    text = "${t.transferDate} · ${t.transferType ?: "转会"} · 转会费 ${t.fee}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                t.notes?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline) }
                Divider(modifier = Modifier.padding(vertical = 2.dp))
            }
        }
    }
}

/** 8. 伤病记录 */
@Composable
fun InjuryHistorySection(history: List<InjuryRecord>) {
    SectionCard(title = "伤病记录") {
        if (history.isEmpty()) {
            EmptyHint("暂无伤病记录")
        } else {
            history.forEach { i ->
                Text(
                    text = "${i.injuryType ?: "伤病"} · ${i.startDate ?: "-"} ~ ${i.expectedReturnDate ?: "-"}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                Text(
                    text = "严重度 ${i.severity} · ${i.status}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

/** 9. 球探报告 */
@Composable
fun ScoutReportSection(report: ScoutReport) {
    SectionCard(title = "球探报告") {
        Text("推荐等级：${"★".repeat(report.recommendationLevel)}${"☆".repeat(5 - report.recommendationLevel)}")
        Text(
            text = report.summary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        Text("优势：", fontWeight = FontWeight.SemiBold)
        report.strengths.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall) }
        Text("短板：", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
        report.weaknesses.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall) }
    }
}

/** 10. 训练计划 */
@Composable
fun TrainingPlanSection(plan: TrainingPlan) {
    SectionCard(title = "训练计划") {
        InfoRow("训练方向", plan.focusArea)
        InfoRow("训练强度", "${plan.intensity}")
        InfoRow("导师", plan.mentorId?.toString() ?: "未设置")
        InfoRow("位置改造", plan.newPositionTraining ?: "无")
    }
}

// ==================== 内部辅助组件 ====================

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.outline)
        Text(value)
    }
}

@Composable
private fun AttributeGroup(title: String, items: List<AttributeItem>) {
    if (items.isEmpty()) return
    Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
    items.chunked(2).forEach { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            row.forEach { attr ->
                Text("${attr.label} ${attr.value}", style = MaterialTheme.typography.bodySmall)
            }
            if (row.size == 1) Text("")
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("●", color = color, style = MaterialTheme.typography.labelSmall)
        Text(" $label", style = MaterialTheme.typography.labelSmall)
    }
}

private fun familiarityColor(value: Int): androidx.compose.ui.graphics.Color = when {
    value >= 80 -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
    value >= 50 -> androidx.compose.ui.graphics.Color(0xFFEF6C00)
    else -> androidx.compose.ui.graphics.Color(0xFFC62828)
}
