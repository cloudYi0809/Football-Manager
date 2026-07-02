package com.greendynasty.football.transfer.negotiation.model

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

/**
 * 合同条款数据访问对象（save.db）
 *
 * 提供合同条款（9 基础 + 7 特殊 + 角色）的 CRUD。
 */
@Dao
interface ContractTermsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(terms: ContractTermsEntity): Long

    @Update
    suspend fun update(terms: ContractTermsEntity)

    @Query("SELECT * FROM contract_terms WHERE save_id = :saveId AND offer_id = :offerId LIMIT 1")
    suspend fun getByOffer(saveId: Int, offerId: Int): ContractTermsEntity?

    @Query("DELETE FROM contract_terms WHERE save_id = :saveId AND offer_id = :offerId")
    suspend fun deleteByOffer(saveId: Int, offerId: Int)
}
