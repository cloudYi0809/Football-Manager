package com.greendynasty.football.data.save.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 经济指数表（save.db，V0.2）
 * 按年份存储全球经济指数，用于转会费、工资的通胀计算。
 * 包括全球指数、转会费指数、工资指数、商业指数。
 */
@Entity(tableName = "economy_index")
data class EconomyIndexEntity(
    @PrimaryKey
    @ColumnInfo(name = "year")
    val year: Int, // 年份

    @ColumnInfo(name = "global_index")
    val globalIndex: Double, // 全球经济指数基准

    @ColumnInfo(name = "transfer_fee_index")
    val transferFeeIndex: Double, // 转会费通胀指数

    @ColumnInfo(name = "wage_index")
    val wageIndex: Double, // 工资通胀指数

    @ColumnInfo(name = "commercial_index")
    val commercialIndex: Double // 商业收入指数
)
