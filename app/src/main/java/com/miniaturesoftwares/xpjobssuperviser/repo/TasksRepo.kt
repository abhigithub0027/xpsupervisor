package com.miniaturesoftwares.xpjobssuperviser.repo

import com.miniaturesoftwares.xpjobssuperviser.network.ApiRateLimiter
import com.miniaturesoftwares.xpjobssuperviser.network.ApiService
import com.miniaturesoftwares.xpjobssuperviser.network.SecureMMKVStorage
import com.miniaturesoftwares.xpjobssuperviser.network.TasksRes
import com.miniaturesoftwares.xpjobssuperviser.network.TaskInfoRes
import com.miniaturesoftwares.xpjobssuperviser.network.TaskTag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TasksRepo @Inject constructor(
    private val api: ApiService,
    private val storage: SecureMMKVStorage
) {
    /** Outlives any one screen: prefetching must survive leaving the list. */
    private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefetchJob: Job? = null

    fun getTasks(
        tag: String? = null,
        status: String? = null,
        tagId: String? = null,
        tagTitle: String? = null,
    ): Flow<TasksRes> = flow {
        // Cache per query, not globally. A single shared key meant switching
        // tab, tag or category re-emitted a *different* query's results, so the
        // list appeared not to react to the filter at all.
        val cacheKey = tasksCacheKey(status, tagId, tagTitle)
        val cached = storage.prefs.getString(cacheKey, null)?.let {
            runCatching { storage.gson.fromJson(it, TasksRes::class.java) }.getOrNull()
        }
        if (cached != null) {
            emit(cached)
        }

        val response = api.getTasks(tag, status, tagId, tagTitle)

        if (response.isSuccessful) {
            response.body()?.let { tasksRes ->
                storage.prefs.putString(cacheKey, storage.gson.toJson(tasksRes))
                emit(tasksRes)

                // Detail prefetch is fire-and-forget on its own scope. It used
                // to run inside coroutineScope{}, which held this flow open
                // until every per-task request finished - the reason switching
                // tabs took so long. Nothing on screen waits for it.
                prefetchTaskDetails(tasksRes)
            } ?: throw Exception("Empty response body")
        } else {
            if (cached == null) {
                throw Exception(response.errorBody()?.string() ?: "Failed to load tasks")
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun tasksCacheKey(status: String?, tagId: String?, tagTitle: String?): String =
        buildString {
            append(SecureMMKVStorage.SecureKey.TASKS_CACHE.key)
            append('_').append(status ?: "all")
            if (!tagId.isNullOrEmpty()) append('_').append(tagId)
            if (!tagTitle.isNullOrEmpty()) append('_').append(tagTitle)
        }

    /**
     * Warm the per-task detail cache in the background.
     *
     * Sequential and rate-limited: this used to launch one coroutine per task
     * at once, which both blew past the server's limit and crowded out the
     * requests the user was actually waiting for.
     */
    private fun prefetchTaskDetails(tasksRes: TasksRes) {
        prefetchJob?.cancel()
        prefetchJob = prefetchScope.launch {
            for (mapping in tasksRes.tasks) {
                val taskId = mapping.task.id
                val cacheKey = "${SecureMMKVStorage.SecureKey.TASK_DETAILS_CACHE_PREFIX.key}$taskId"
                // Already cached: skip without spending a request.
                if (storage.prefs.getString(cacheKey, null) != null) continue

                ApiRateLimiter.acquire()
                try {
                    val detailResponse = api.getTaskInfo(taskId)
                    if (detailResponse.isSuccessful) {
                        detailResponse.body()?.let { detail ->
                            storage.prefs.putString(cacheKey, storage.gson.toJson(detail))
                        }
                    }
                } catch (e: Exception) {
                    // Silent fail for background caching.
                }
            }
        }
    }

    fun getTaskInfo(taskId: String): Flow<TaskInfoRes> = flow {
        // Check cache first for immediate offline support
        val cacheKey = "${SecureMMKVStorage.SecureKey.TASK_DETAILS_CACHE_PREFIX.key}$taskId"
        val cachedJson = storage.prefs.getString(cacheKey, null)
        if (cachedJson != null) {
            try {
                val cached = storage.gson.fromJson(cachedJson, TaskInfoRes::class.java)
                emit(cached)
            } catch (e: Exception) {
                // Ignore parsing error
            }
        }

        val response = api.getTaskInfo(taskId)

        if (response.isSuccessful) {
            response.body()?.let { 
                storage.prefs.putString(cacheKey, storage.gson.toJson(it))
                emit(it) 
            } ?: throw Exception("Empty response body")
        } else {
            // If network fails but we had cache, we already emitted it.
            // If we didn't have cache, throw error.
            if (cachedJson == null) {
                throw Exception(response.errorBody()?.string() ?: "Failed to load task details")
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Work-type tags, filtered server-side.
     *
     * An empty [filter] returns the full list, which is what the screen asks
     * for on open - the same call serves both cases, exactly as on iOS.
     */
    fun searchTags(filter: String = ""): Flow<List<TaskTag>> = flow {
        val response = api.searchTags(filter)

        if (response.isSuccessful) {
            emit(response.body()?.tags.orEmpty())
        } else {
            throw Exception(response.errorBody()?.string() ?: "Failed to load tags")
        }
    }.flowOn(Dispatchers.IO)
}
