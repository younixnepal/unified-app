package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY isPrimary DESC, email ASC")
    fun getAllAccountsFlow(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE email = :email LIMIT 1")
    suspend fun getAccountByEmail(email: String): AccountEntity?

    @Query("SELECT * FROM accounts")
    suspend fun getAllAccounts(): List<AccountEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(account: AccountEntity)

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE accounts SET usedStorage = :used, totalStorageLimit = :limit, usageInDrive = :driveUsage, usageInDriveTrash = :trashUsage, status = 'CONNECTED', lastSyncedAt = :timestamp WHERE id = :accountId")
    suspend fun updateQuota(
        accountId: String,
        limit: Long?,
        used: Long,
        driveUsage: Long,
        trashUsage: Long,
        timestamp: Long = System.currentTimeMillis()
    )

    @Query("UPDATE accounts SET status = :status WHERE id = :accountId")
    suspend fun updateStatus(accountId: String, status: String)

    @Query("UPDATE accounts SET accessToken = :token, tokenExpiresAt = :expiresAt, status = 'CONNECTED' WHERE id = :accountId")
    suspend fun updateToken(accountId: String, token: String, expiresAt: Long)

    @Query("UPDATE accounts SET startPageToken = :token, lastSyncedAt = :timestamp WHERE id = :accountId")
    suspend fun updateStartPageToken(accountId: String, token: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM accounts WHERE id LIKE 'demo_%' OR accessToken LIKE 'demo_%' OR accessToken LIKE 'simulated_%'")
    suspend fun deleteDemoAccounts()
}
