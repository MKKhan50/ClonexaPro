package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import com.example.model.ManagedAppInstanceEntity
import com.example.model.ProxyConfigEntity
import com.example.model.SecurityAuditLogEntity
import com.example.model.SpeedTestResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ManagedInstanceDao {
    @Query("SELECT * FROM managed_app_instances ORDER BY createdAt DESC")
    fun observeAllInstances(): Flow<List<ManagedAppInstanceEntity>>

    @Query("SELECT * FROM managed_app_instances WHERE isInPrivateSpace = 0 ORDER BY createdAt DESC")
    fun observePublicInstances(): Flow<List<ManagedAppInstanceEntity>>

    @Query("SELECT * FROM managed_app_instances WHERE isInPrivateSpace = 1 ORDER BY createdAt DESC")
    fun observePrivateInstances(): Flow<List<ManagedAppInstanceEntity>>

    @Query("SELECT * FROM managed_app_instances WHERE id = :id LIMIT 1")
    suspend fun getInstanceById(id: Long): ManagedAppInstanceEntity?

    @Query("SELECT COUNT(*) FROM managed_app_instances WHERE packageName = :packageName")
    suspend fun getInstanceCountForPackage(packageName: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstance(instance: ManagedAppInstanceEntity): Long

    @Update
    suspend fun updateInstance(instance: ManagedAppInstanceEntity)

    @Query("DELETE FROM managed_app_instances WHERE id = :id")
    suspend fun deleteInstanceById(id: Long)

    @Query("UPDATE managed_app_instances SET launchCount = launchCount + 1, lastLaunchedAt = :timestamp WHERE id = :id")
    suspend fun recordInstanceLaunch(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE managed_app_instances SET isInPrivateSpace = :inPrivateSpace WHERE id = :id")
    suspend fun setPrivateSpaceStatus(id: Long, inPrivateSpace: Boolean)
}

@Dao
interface SecurityAuditDao {
    @Query("SELECT * FROM security_audit_logs ORDER BY timestamp DESC LIMIT 100")
    fun observeRecentLogs(): Flow<List<SecurityAuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: SecurityAuditLogEntity)

    @Query("DELETE FROM security_audit_logs")
    suspend fun clearLogs()
}

@Dao
interface SpeedTestResultDao {
    @Query("SELECT * FROM speed_test_results ORDER BY timestamp DESC LIMIT 50")
    fun observeSpeedTestHistory(): Flow<List<SpeedTestResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSpeedTestResult(result: SpeedTestResultEntity): Long

    @Query("DELETE FROM speed_test_results WHERE id = :id")
    suspend fun deleteSpeedTestResultById(id: Long)

    @Query("DELETE FROM speed_test_results")
    suspend fun clearAllSpeedTestResults()
}

@Dao
interface ProxyConfigDao {
    @Query("SELECT * FROM proxy_configs ORDER BY isActiveConnected DESC, isSelected DESC, createdAt DESC")
    fun observeAllProxies(): Flow<List<ProxyConfigEntity>>

    @Query("SELECT * FROM proxy_configs WHERE id = :id LIMIT 1")
    suspend fun getProxyById(id: Long): ProxyConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProxy(proxy: ProxyConfigEntity): Long

    @Update
    suspend fun updateProxy(proxy: ProxyConfigEntity)

    @Query("DELETE FROM proxy_configs WHERE id = :id")
    suspend fun deleteProxyById(id: Long)

    @Query("UPDATE proxy_configs SET isSelected = CASE WHEN id = :selectedId THEN 1 ELSE 0 END")
    suspend fun setSelectedProxy(selectedId: Long)

    @Query("UPDATE proxy_configs SET isActiveConnected = CASE WHEN id = :activeId THEN 1 ELSE 0 END, isSelected = CASE WHEN id = :activeId THEN 1 ELSE isSelected END")
    suspend fun setActiveConnectedProxy(activeId: Long)

    @Query("UPDATE proxy_configs SET isActiveConnected = 0")
    suspend fun clearAllActiveConnectedProxies()
}

@Database(
    entities = [
        ManagedAppInstanceEntity::class,
        SecurityAuditLogEntity::class,
        SpeedTestResultEntity::class,
        ProxyConfigEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class ClonexaDatabase : RoomDatabase() {
    abstract fun managedInstanceDao(): ManagedInstanceDao
    abstract fun securityAuditDao(): SecurityAuditDao
    abstract fun speedTestResultDao(): SpeedTestResultDao
    abstract fun proxyConfigDao(): ProxyConfigDao

    companion object {
        @Volatile
        private var INSTANCE: ClonexaDatabase? = null

        fun getInstance(context: Context): ClonexaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ClonexaDatabase::class.java,
                    "clonexapro_workspace.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

class ClonexaWorkspaceRepository(
    private val instanceDao: ManagedInstanceDao,
    private val auditDao: SecurityAuditDao,
    private val speedTestDao: SpeedTestResultDao? = null,
    private val proxyDao: ProxyConfigDao? = null
) {
    val allInstances: Flow<List<ManagedAppInstanceEntity>> = instanceDao.observeAllInstances()
    val auditLogs: Flow<List<SecurityAuditLogEntity>> = auditDao.observeRecentLogs()
    val speedTestHistory: Flow<List<SpeedTestResultEntity>>? = speedTestDao?.observeSpeedTestHistory()
    val proxyConfigs: Flow<List<ProxyConfigEntity>>? = proxyDao?.observeAllProxies()

    suspend fun createInstance(instance: ManagedAppInstanceEntity): Long {
        val id = instanceDao.insertInstance(instance)
        auditDao.insertLog(
            SecurityAuditLogEntity(
                eventType = "INSTANCE_CREATED",
                packageName = instance.packageName,
                summary = "Registered '${instance.accountLabel}' for ${instance.appName} [${instance.resolvedTier.badgeTitle}]",
                isSuccess = true
            )
        )
        return id
    }

    suspend fun updateInstance(instance: ManagedAppInstanceEntity) {
        instanceDao.updateInstance(instance)
        auditDao.insertLog(
            SecurityAuditLogEntity(
                eventType = "INSTANCE_UPDATED",
                packageName = instance.packageName,
                summary = "Updated instance '${instance.accountLabel}' for ${instance.appName}",
                isSuccess = true
            )
        )
    }

    suspend fun deleteInstance(instance: ManagedAppInstanceEntity) {
        instanceDao.deleteInstanceById(instance.id)
        auditDao.insertLog(
            SecurityAuditLogEntity(
                eventType = "INSTANCE_REMOVED",
                packageName = instance.packageName,
                summary = "Removed workspace instance '${instance.accountLabel}' (${instance.appName})",
                isSuccess = true
            )
        )
    }

    suspend fun togglePrivateSpace(instance: ManagedAppInstanceEntity, moveToPrivate: Boolean) {
        instanceDao.setPrivateSpaceStatus(instance.id, moveToPrivate)
        auditDao.insertLog(
            SecurityAuditLogEntity(
                eventType = if (moveToPrivate) "MOVED_TO_PRIVATE_SPACE" else "RESTORED_FROM_PRIVATE_SPACE",
                packageName = instance.packageName,
                summary = "${instance.appName} (${instance.accountLabel}) ${if (moveToPrivate) "locked in Private Space" else "moved to public workspace"}",
                isSuccess = true
            )
        )
    }

    suspend fun recordLaunchEvent(
        instance: ManagedAppInstanceEntity,
        succeeded: Boolean,
        detailMessage: String
    ) {
        if (succeeded) {
            instanceDao.recordInstanceLaunch(instance.id)
        }
        auditDao.insertLog(
            SecurityAuditLogEntity(
                eventType = if (succeeded) "INSTANCE_LAUNCHED" else "LAUNCH_FAILED",
                packageName = instance.packageName,
                summary = detailMessage,
                isSuccess = succeeded
            )
        )
    }

    suspend fun saveSpeedTestResult(result: SpeedTestResultEntity): Long {
        val id = speedTestDao?.insertSpeedTestResult(result) ?: 0L
        auditDao.insertLog(
            SecurityAuditLogEntity(
                eventType = "SPEED_TEST_COMPLETED",
                packageName = null,
                summary = "Speed Test (${result.connectionType}): ↓ ${String.format("%.1f", result.downloadMbps)} Mbps • ↑ ${String.format("%.1f", result.uploadMbps)} Mbps • Ping ${String.format("%.0f", result.pingMs)} ms",
                isSuccess = true
            )
        )
        return id
    }

    suspend fun deleteSpeedTestResult(id: Long) {
        speedTestDao?.deleteSpeedTestResultById(id)
    }

    suspend fun clearSpeedTestHistory() {
        speedTestDao?.clearAllSpeedTestResults()
        auditDao.insertLog(
            SecurityAuditLogEntity(
                eventType = "SPEED_TEST_HISTORY_CLEARED",
                packageName = null,
                summary = "Cleared local Internet Speed Test history",
                isSuccess = true
            )
        )
    }

    // --- Proxy Library Persistence (Never logs passwords) ---

    suspend fun saveProxyConfig(config: ProxyConfigEntity): Long {
        val isUpdate = config.id > 0L
        val id = if (isUpdate) {
            proxyDao?.updateProxy(config)
            config.id
        } else {
            val newId = proxyDao?.insertProxy(config) ?: 0L
            proxyDao?.setSelectedProxy(newId)
            newId
        }
        auditDao.insertLog(
            SecurityAuditLogEntity(
                eventType = if (isUpdate) "PROXY_UPDATED" else "PROXY_ADDED",
                packageName = null,
                summary = "${if (isUpdate) "Updated" else "Saved"} proxy '${config.name}' (${config.protocol} ${config.host}:${config.port})",
                isSuccess = true
            )
        )
        return id
    }

    suspend fun updateProxyTestResult(updated: ProxyConfigEntity) {
        proxyDao?.updateProxy(updated)
        auditDao.insertLog(
            SecurityAuditLogEntity(
                eventType = "PROXY_TESTED",
                packageName = null,
                summary = "Tested '${updated.name}' (${updated.protocol}): ${updated.resolvedTestStatus.displayLabel} • IP: ${updated.lastObservedIp}",
                isSuccess = updated.resolvedTestStatus.name == "WORKING"
            )
        )
    }

    suspend fun selectProxyConfig(id: Long) {
        proxyDao?.setSelectedProxy(id)
    }

    suspend fun setActiveConnectedProxy(id: Long, proxyName: String, observedIp: String) {
        proxyDao?.setActiveConnectedProxy(id)
        auditDao.insertLog(
            SecurityAuditLogEntity(
                eventType = "PROXY_CONNECTED",
                packageName = null,
                summary = "Connected application-scoped proxy '$proxyName' • External IP: $observedIp",
                isSuccess = true
            )
        )
    }

    suspend fun disconnectAllProxies() {
        proxyDao?.clearAllActiveConnectedProxies()
        auditDao.insertLog(
            SecurityAuditLogEntity(
                eventType = "PROXY_DISCONNECTED",
                packageName = null,
                summary = "Disconnected active proxy and restored direct connection routing",
                isSuccess = true
            )
        )
    }

    suspend fun deleteProxyConfig(proxy: ProxyConfigEntity) {
        proxyDao?.deleteProxyById(proxy.id)
        auditDao.insertLog(
            SecurityAuditLogEntity(
                eventType = "PROXY_DELETED",
                packageName = null,
                summary = "Deleted proxy configuration '${proxy.name}' (${proxy.host}:${proxy.port})",
                isSuccess = true
            )
        )
    }

    suspend fun logSecurityEvent(
        eventType: String,
        packageName: String? = null,
        summary: String,
        isSuccess: Boolean = true
    ) {
        auditDao.insertLog(
            SecurityAuditLogEntity(
                eventType = eventType,
                packageName = packageName,
                summary = summary,
                isSuccess = isSuccess
            )
        )
    }

    suspend fun clearAuditLogs() {
        auditDao.clearLogs()
    }
}
