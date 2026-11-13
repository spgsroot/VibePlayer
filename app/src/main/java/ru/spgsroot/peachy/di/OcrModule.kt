package ru.spgsroot.peachy.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.spgsroot.peachy.translate.MlKitPageOcr
import ru.spgsroot.peachy.translate.PageOcr

@Module
@InstallIn(SingletonComponent::class)
abstract class OcrModule {
    @Binds
    abstract fun bindPageOcr(implementation: MlKitPageOcr): PageOcr
}
