package com.miniaturesoftwares.xpjobssuperviser.di

import com.miniaturesoftwares.xpjobssuperviser.network.supervisor.SupervisorApi
import com.miniaturesoftwares.xpjobssuperviser.network.supervisor.SupervisorApiStub
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The one place that decides whether the supervisor endpoints are real.
 *
 * Swap [SupervisorApiStub] for the Retrofit-generated implementation once the
 * backend ships docs/SUPERVISOR_API.md; nothing else in the app changes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SupervisorModule {

    @Binds
    @Singleton
    abstract fun bindSupervisorApi(stub: SupervisorApiStub): SupervisorApi
}
