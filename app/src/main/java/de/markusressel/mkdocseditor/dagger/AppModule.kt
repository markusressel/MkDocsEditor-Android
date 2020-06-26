package de.markusressel.mkdocseditor.dagger

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ApplicationComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import de.markusressel.kutepreferences.core.persistence.DefaultKutePreferenceDataProvider
import de.markusressel.kutepreferences.core.persistence.KutePreferenceDataProvider
import de.markusressel.mkdocseditor.data.persistence.entity.MyObjectBox
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import io.objectbox.BoxStore
import javax.inject.Singleton

/**
 * Created by Markus on 20.12.2017.
 */
@Module
@InstallIn(ApplicationComponent::class)
class AppModule {

    @Provides
    @Singleton
    internal fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    internal fun provideKutePreferenceDataProvider(@ApplicationContext context: Context): KutePreferenceDataProvider {
        return DefaultKutePreferenceDataProvider(context)
    }

    @Provides
    @Singleton
    internal fun provideDiffMatchPatch(): diff_match_patch {
        return diff_match_patch()
    }

    @Provides
    @Singleton
    internal fun provideMkDocsRestClient(): MkDocsRestClient {
        return MkDocsRestClient()
    }

    @Provides
    @Singleton
    internal fun provideBoxStore(@ApplicationContext context: Context): BoxStore {
        return MyObjectBox
                .builder()
                .androidContext(context)
                .build()
    }

}
