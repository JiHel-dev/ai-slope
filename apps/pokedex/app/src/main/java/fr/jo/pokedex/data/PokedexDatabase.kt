package fr.jo.pokedex.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PokemonEntity::class, AliasEntity::class, MetadataEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PokedexDatabase : RoomDatabase() {
    abstract fun pokemonDao(): PokemonDao

    companion object {
        fun create(context: Context): PokedexDatabase =
            Room.databaseBuilder(context, PokedexDatabase::class.java, "pokedex.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
