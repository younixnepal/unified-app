package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.entity.AccountEntity
import com.example.data.repository.UnifiedCloudRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context matches UnifiedCloud`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("UnifiedCloud", appName)
    }

    @Test
    fun `test storage aggregation with two connected accounts`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = UnifiedCloudRepository(context)

        val account1 = AccountEntity(
            id = "acc1",
            email = "user1@gmail.com",
            displayName = "User One",
            accessToken = "token1",
            totalStorageLimit = 100L * 1024 * 1024 * 1024, // 100 GB
            usedStorage = 50L * 1024 * 1024 * 1024 // 50 GB
        )

        val account2 = AccountEntity(
            id = "acc2",
            email = "user2@gmail.com",
            displayName = "User Two",
            accessToken = "token2",
            totalStorageLimit = 15L * 1024 * 1024 * 1024, // 15 GB
            usedStorage = 10L * 1024 * 1024 * 1024 // 10 GB
        )

        val aggregation = repo.calculateStorageAggregation(listOf(account1, account2))

        assertEquals(2, aggregation.accountCount)
        assertEquals(115L * 1024 * 1024 * 1024, aggregation.totalLimitBytes)
        assertEquals(60L * 1024 * 1024 * 1024, aggregation.totalUsedBytes)
        assertEquals(55L * 1024 * 1024 * 1024, aggregation.totalAvailableBytes)
        assertTrue(aggregation.usedPercentage > 50f)
    }

    @Test
    fun `test formatBytes helper`() {
        assertEquals("500 B", UnifiedCloudRepository.formatBytes(500L))
        assertEquals("1.0 KB", UnifiedCloudRepository.formatBytes(1024L))
        assertEquals("1.0 MB", UnifiedCloudRepository.formatBytes(1024L * 1024))
        assertEquals("15.00 GB", UnifiedCloudRepository.formatBytes(15L * 1024 * 1024 * 1024))
    }
}
