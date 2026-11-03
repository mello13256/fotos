package io.github.mello13256.fotos.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MediaEntity::class, CollectionEntity::class, CollectionItemEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class FotosDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun collectionDao(): CollectionDao

    companion object {
        fun build(context: Context): FotosDatabase =
            Room.databaseBuilder(context, FotosDatabase::class.java, "fotos.db").build()
    }
}
