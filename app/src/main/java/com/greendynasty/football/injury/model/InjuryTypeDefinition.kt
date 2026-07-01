package com.greendynasty.football.injury.model

/**
 * 伤病类型定义（T08 方案 §三.1 / §九配置表）
 *
 * 注：history.db 是随安装包提供的只读资产库，无法在运行时新增 injury_type 静态表。
 * 因此 V1 将 30+ 伤病类型预置在 [InjuryConfig] 内存中（[InjuryTypeDefinition]），
 * 而非 Room 实体。这与 V0.2 "injury_type 表在 history.db" 的定位在数据层面等价，
 * 仅载体由数据库表改为内存配置（后续若引入可写数据包可平滑迁移）。
 *
 * @param typeCode 伤病类型编码（如 STRAIN_MUSCLE / ACL_TEAR）
 * @param nameCn 中文显示名
 * @param nameEn 英文名
 * @param severity 严重度
 * @param category 类别：MUSCLE/LIGAMENT/FRACTURE/CONCUSSION/TENDON
 * @param baseRecoveryDaysMin 基础恢复天数下限
 * @param baseRecoveryDaysMax 基础恢复天数上限
 * @param recurrenceBaseRisk 基础复发概率 0-1
 * @param permanentImpactRisk 永久影响概率 0-1（仅重伤及以上）
 * @param bodyPart 受影响部位 leg/knee/ankle/foot/head/upper_body
 * @param isContactType true=接触性伤，false=非接触性
 * @param weightInPool 同严重度池中的抽样权重
 */
data class InjuryTypeDefinition(
    val typeCode: String,
    val nameCn: String,
    val nameEn: String,
    val severity: InjurySeverity,
    val category: String,
    val baseRecoveryDaysMin: Int,
    val baseRecoveryDaysMax: Int,
    val recurrenceBaseRisk: Double,
    val permanentImpactRisk: Double,
    val bodyPart: String,
    val isContactType: Boolean,
    val weightInPool: Int
) {
    /** 是否为重伤及以上（触发永久影响评估的门槛） */
    val isMajorOrWorse: Boolean get() = severity == InjurySeverity.MAJOR || severity == InjurySeverity.CAREER_THREATENING
}
